package com.faber.core.config.influx.service;


import com.faber.core.config.influx.InfluxDbConfig;
import com.faber.core.config.influx.entity.InfluxSQL;
import com.faber.core.config.influx.vo.query.CommonHisQueryReq;
import com.influxdb.annotations.Measurement;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;


/**
 * @description: 时序数据库操作类
 * @author: zjb
 * @createTime: 2024/12/25
 */
@Component
public class InfluxDBService {

    @Autowired
    private InfluxDbConfig influxDbConfig;

    /**
     * 通用插入
     */
    public void writeToInfluxDB(Object object) throws IllegalAccessException {
        InfluxDBClient influxdb = InfluxDBClientFactory.create(influxDbConfig.getUrl(), influxDbConfig.getToken().toCharArray(), influxDbConfig.getOrg(), influxDbConfig.getBucket());
        WriteApiBlocking writeApi = influxdb.getWriteApiBlocking();
        writeApi.writeMeasurement(WritePrecision.NS, object);
        influxdb.close();
    }

    /**
     * 通用查询
     */
    public Object queryInfluxDB(String emsId, String deviceId, Object object) throws IllegalAccessException {
        InfluxDBClient influxdb = InfluxDBClientFactory.create(influxDbConfig.getUrl(), influxDbConfig.getToken().toCharArray(), influxDbConfig.getOrg(), influxDbConfig.getBucket());
        //取出表名
        Measurement masurement = object.getClass().getAnnotation(Measurement.class);
        String tableName = masurement.name();

        //取出所有字段
        List<Field> fields = Arrays.stream(object.getClass().getDeclaredFields()).toList();
        //目标字段类型
        // 使用Stream筛选匹配的字段并处理
        Map<String, String> fieldsType = Arrays.stream(object.getClass().getDeclaredFields())
                .peek(field -> field.setAccessible(true)) // 如果字段是私有的，需要设置为可访问
                .collect(Collectors.toMap(
                        Field::getName, // 获取字段名作为Map的key
                        field -> field.getType().getName() // 获取字段类型的完全限定名作为Map的value
                ));

        String response = "";
        StringBuilder flux = new StringBuilder();
        flux.append("from(bucket:\"").append(influxDbConfig.getBucket()).append("\") ");
        flux.append("|> range(start: -24h, stop: 1h) ");
        flux.append("|> filter(fn: (r) => r._measurement == \"").append(tableName).append("\") ");
        if(null != deviceId){
            flux.append("|> filter(fn: (r) => r.deviceId == \"").append(deviceId).append("\") ");
        }
        flux.append("|> filter(fn: (r) => r.emsId == \"").append(emsId).append("\") ");
        flux.append("|> sort(columns: [\"_time\"], desc: true) ");
        flux.append("|> limit(n:1, offset: 0)");
        QueryApi queryApi = influxdb.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux.toString());
        for (FluxTable fluxTable : tables) {
            List<FluxRecord> records = fluxTable.getRecords();
            for (FluxRecord fluxRecord : records) {
                fields.stream().forEach(field -> {
                    if(field.getName().equals(fluxRecord.getField())){
                        try {
                            // 设置字段可访问
                            field.setAccessible(true);
                            String name = fieldsType.get(fluxRecord.getField());
                            Object value = fluxRecord.getValueByKey("_value");
                            if("long".equals(name)){
                                value = new BigDecimal(String.valueOf(value)).setScale(1, RoundingMode.HALF_UP).longValue();
                            } else if("double".equals(name)){
                                value = new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP).doubleValue();
                            }
                            field.set(object, value);
                        } catch (IllegalAccessException e) {
                            System.out.println(e.getMessage());
                        }
                    } else if(field.getName().equals("time")){
                        // 设置字段可访问
                        field.setAccessible(true);
                        try {
                            field.set(object, fluxRecord.getTime());
                        } catch (IllegalAccessException e) {
                            System.out.println(e.getMessage());
                        }
                    } else if(field.getName().equals("deviceId")){
                        // 设置字段可访问
                        field.setAccessible(true);
                        try {
                            field.set(object, deviceId);
                        } catch (IllegalAccessException e) {
                            System.out.println(e.getMessage());
                        }
                    } else if(field.getName().equals("emsId")){
                        // 设置字段可访问
                        field.setAccessible(true);
                        try {
                            field.set(object, emsId);
                        } catch (IllegalAccessException e) {
                            System.out.println(e.getMessage());
                        }
                    }
                });
            }
        }
        influxdb.close();
        return object;
    }

    /**
     * 通用条件查询
     */
    public List<Map<String, Object>> queryInfluxDBByCondition(CommonHisQueryReq commonHisQueryRequest) throws IllegalAccessException {
        InfluxDBClient influxdb = InfluxDBClientFactory.create(influxDbConfig.getUrl(), influxDbConfig.getToken().toCharArray(), influxDbConfig.getOrg(), influxDbConfig.getBucket());
        List<Map<String,Object>> fieldDataList = new ArrayList<>();
        List<String> fieldKey = commonHisQueryRequest.getFieldNameList();
        //额外条件
        String fluxCondition = commonHisQueryRequest.getFluxCondifiton();
        //取出表名
        QueryApi queryApi = influxdb.getQueryApi();

        Instant startTie = commonHisQueryRequest.getStartTime();
        Instant endTime = commonHisQueryRequest.getEndTime();
        //取出表名
        Object object = commonHisQueryRequest.getQueryObject();
        Measurement masurement = object.getClass().getAnnotation(Measurement.class);

        String tableName = masurement.name();
        String deviceId = commonHisQueryRequest.getDeviceId();
        String emsId = commonHisQueryRequest.getEmsId();

        StringBuilder flux = new StringBuilder();
        //单个字段的数据值
        //单个字段的数据值
        //字段的历史数据 key
        flux.append("from(bucket:\"").append(influxDbConfig.getBucket()).append("\") ");
        flux.append("|> range(start: ").append(startTie).append(",stop:").append(endTime).append(") ");
        flux.append("|> filter(fn: (r) => r._measurement == \"").append(tableName).append("\") ");
        flux.append("|> filter(fn: (r) => r.deviceId == \"").append(deviceId).append("\") ");
        flux.append("|> filter(fn: (r) => r.emsId == \"").append(emsId).append("\") ");
        Boolean frist = true;
        for(String fieldName : commonHisQueryRequest.getFieldNameList()) {
            if(frist) {
                flux.append("|> filter(fn: (r) => r._field == \"").append(fieldName).append("\") ");
                frist = false;
            } else {
                flux.append(" or r._field == \"").append(fieldName).append("\") ");
            }
        }
        flux.append(fluxCondition);
        List<FluxTable> tables = queryApi.query(flux.toString());
        for (FluxTable fluxTable : tables) {
            List<FluxRecord> records = fluxTable.getRecords();
            for (FluxRecord fluxRecord : records) {
                Map<String,Object> fieldData = new HashMap<>();
                if(fieldKey.contains(fluxRecord.getField())){
                    fieldData.put(fluxRecord.getField(), fluxRecord.getValueByKey("_value"));
                }
                if(null == fieldData.get("time")){
                    Object time = Instant.parse(Objects.requireNonNull(fluxRecord.getValueByKey("_time")).toString()).atZone(ZoneId.systemDefault()).toLocalDateTime();
                    fieldData.put("time", time);
                }
                fieldDataList.add(fieldData);
            }
        }
        influxdb.close();
        return fieldDataList;
    }

    /**
     * 通用历史查询
     */
    public List<Map<String, Object>> queryInfluxDBHistory(CommonHisQueryReq commonHisQueryRequest) throws IllegalAccessException {
        InfluxDBClient influxdb = InfluxDBClientFactory.create(influxDbConfig.getUrl(), influxDbConfig.getToken().toCharArray(), influxDbConfig.getOrg(), influxDbConfig.getBucket());
        Instant startTie = commonHisQueryRequest.getStartTime();
        Instant endTime = commonHisQueryRequest.getEndTime();

        //取出目标对象
        Object object = commonHisQueryRequest.getQueryObject();
        //表名注解
        Measurement masurement = object.getClass().getAnnotation(Measurement.class);
        //取出表名
        String tableName = masurement.name();
        //获取需要筛选的字段名称列表
        List<String> fieldNameList = commonHisQueryRequest.getFieldNameList();
        //目标字段类型
        Field[] fields = object.getClass().getDeclaredFields();
        // 使用Stream筛选匹配的字段并处理
        Map<String, String> fieldsType = Arrays.stream(fields)
                .filter(field -> fieldNameList.contains(getSimpleFieldName(field)))
                .peek(field -> field.setAccessible(true)) // 如果字段是私有的，需要设置为可访问
                .collect(Collectors.toMap(
                        Field::getName, // 获取字段名作为Map的key
                        field -> field.getType().getName() // 获取字段类型的完全限定名作为Map的value
                ));
        //取出设备号
        String deviceId = commonHisQueryRequest.getDeviceId();
        //取出emsId
        String emsId = commonHisQueryRequest.getEmsId();


        StringBuilder flux = new StringBuilder();
        flux.append("from(bucket:\"").append(influxDbConfig.getBucket()).append("\") ");
        flux.append("|> range(start: ").append(startTie).append(",stop:").append(endTime).append(") ");
        flux.append("|> filter(fn: (r) => r._measurement == \"").append(tableName).append("\") ");
        flux.append("|> filter(fn: (r) => r.deviceId == \"").append(deviceId).append("\") ");
        flux.append("|> filter(fn: (r) => r.emsId == \"").append(emsId).append("\") ");

        Boolean frist = true;
        flux.append("|> filter(fn: (r) => ");
        for(String fieldName : fieldNameList) {
            if(frist) {
                flux.append("r._field == \"").append(fieldName).append("\"");
                frist = false;
            } else {
                flux.append(" or r._field == \"").append(fieldName).append("\"");
            }
        }
        flux.append(") ");
        flux.append("|> sort(columns: [\"_time\"], desc: true) ");
        QueryApi queryApi = influxdb.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux.toString());

        Map<LocalDateTime, Map<String,Object>> timeSelectMap = new HashMap<>();
        Map<String,Object> fieldData = new HashMap<>();
        for (FluxTable fluxTable : tables) {
            List<FluxRecord> records = fluxTable.getRecords();
            for (FluxRecord fluxRecord : records) {
                if(fieldNameList.contains(fluxRecord.getField())){
                    LocalDateTime time = Instant.parse(fluxRecord.getValueByKey("_time").toString()).atZone(ZoneId.systemDefault()).toLocalDateTime();
                    if(null == timeSelectMap.get(time)){
                        fieldData = new HashMap<>();
                    } else {
                        fieldData = timeSelectMap.get(time);
                    }

                    if(null == fieldData.get("time")){
                        fieldData.put("time", time);
                    }
                    String name = fieldsType.get(fluxRecord.getField());
                    Object value = fluxRecord.getValueByKey("_value");
                    if("long".equals(name)){
                        value = new BigDecimal(String.valueOf(value)).setScale(1, RoundingMode.HALF_UP).longValue();
                    } else if("double".equals(name)){
                        value = new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP).doubleValue();
                    }
                    fieldData.put(fluxRecord.getField(), value);
                    timeSelectMap.put(time, fieldData);

                }
            }
        }
        influxdb.close();

        // 按 LocalDateTime 升序排序
        List<Map<String, Object>> fieldDataList = new ArrayList<>();
        timeSelectMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())  // 默认升序排序
                // 提取排序后的 value 并添加到 result 列表
                .forEach(entry -> fieldDataList.add(entry.getValue()));

        return fieldDataList;
    }

    /**
     * 通用查询v1
     */
    public List<FluxTable> queryInfluxDBV1(InfluxSQL influxSQL) throws IllegalAccessException {
        InfluxDBClient influxdb = InfluxDBClientFactory.create(influxDbConfig.getUrl(), influxDbConfig.getToken().toCharArray(), influxDbConfig.getOrg(), influxDbConfig.getBucket());

        StringBuilder flux = new StringBuilder();
        flux.append("from(bucket:\"").append(influxDbConfig.getBucket()).append("\") \n");
        if (influxSQL.getStart() == null) {
            flux.append("|> range(stop: ").append(influxSQL.getStop()).append(") \n");
        } else if (influxSQL.getStop() == null) {
            flux.append("|> range(start: ").append(influxSQL.getStart()).append(") \n");
        } else {
            flux.append("|> range(start: ").append(influxSQL.getStart()).append(", stop: ").append(influxSQL.getStop()).append(") \n");
        }
        flux.append("|> filter(fn: (r) => r._measurement == \"").append(influxSQL.getMeasurement()).append("\") \n");
        if (influxSQL.getEmsId() != null) {
            flux.append("|> filter(fn: (r) => r.emsId == ").append(influxSQL.getEmsId()).append(") \n");
        }
        if (influxSQL.getField() != null) {
            flux.append("|> filter(fn: (r) => r._field == ").append(influxSQL.getField()).append(") \n");
        }
        if (influxSQL.getDeviceId() != null) {
            flux.append("|> filter(fn: (r) => r.deviceId == ").append(influxSQL.getDeviceId()).append(") \n");
        }
        if (influxSQL.getEvery() != null) {
            flux.append("|> window(every: ").append(influxSQL.getEvery()).append(") \n");
        }
        if (influxSQL.getLast() != null) {
            flux.append("|> last() \n");
        }
        if (influxSQL.getFirst() != null) {
            flux.append("|> first() \n");
        }
        if (influxSQL.getSum() != null) {
            flux.append("|> sum(column: \"").append(influxSQL.getSum()).append("\") \n");
        }
        QueryApi queryApi = influxdb.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux.toString());

        return tables;
    }

    /**
     * 通用查询v2
     */
    public List<FluxTable> queryInfluxDBV2(InfluxSQL influxSQL) {
        InfluxDBClient influxdb = InfluxDBClientFactory.create(influxDbConfig.getUrl(), influxDbConfig.getToken().toCharArray(), influxDbConfig.getOrg(), influxDbConfig.getBucket());
        StringBuilder flux = new StringBuilder();
        flux.append("from(bucket:\"").append(influxDbConfig.getBucket()).append("\") \n");
        flux.append("|> range(start: ").append(influxSQL.getStart()).append(", stop:").append(influxSQL.getStop()).append(") \n");
        flux.append("|> filter(fn: (r) => r._measurement == \"").append(influxSQL.getMeasurement()).append("\") \n");
        flux.append("|> filter(fn: (r) => r.emsId == ").append(influxSQL.getEmsId()).append(") \n");
        flux.append("|> filter(fn: (r) => r._field == ").append(influxSQL.getField()).append(") \n");
//        flux.append("|> filter(fn: (r) => r._deviceId == ").append(influxSQL.getDeviceId()).append(") \n");
        flux.append("|> aggregateWindow(every: 1d, fn: sum) \n");
        flux.append("|> pivot(rowKey: [\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")\n \n");


        QueryApi queryApi = influxdb.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux.toString());

        return tables;
    }

    /**
     * 通用查询v3
     */
    public List<FluxTable> queryInfluxDBV3(String fluxQuery) {
        InfluxDBClient influxdb = InfluxDBClientFactory.create(
                influxDbConfig.getUrl(),
                influxDbConfig.getToken().toCharArray(),
                influxDbConfig.getOrg(),
                influxDbConfig.getBucket());

        return influxdb.getQueryApi().query(fluxQuery);
    }


    private void appendSumIfNotNull(StringBuilder flux, String column) {
        if (column != null) {
            flux.append("|> sum(column: \"")
                    .append(escapeSpecialChars(column))
                    .append("\") \n");
        }
    }

    private String escapeSpecialChars(String value) {
        // 这里简单地转义双引号，实际应用中可能需要更复杂的转义逻辑
        return Objects.requireNonNullElse(value, "").replace("\"", "\\\"");
    }

    // 处理并返回简单的字段名，去除包路径或任何前缀
    private String getSimpleFieldName(Field field) {
        String fullName = field.getName(); // 获取完整的字段名
        // 如果有前缀或者包路径的情况，可以在这里处理
        // 比如去除包路径前缀等，假设你知道它是用某种模式构造的
        return fullName.replaceAll(".*\\.", ""); // 去除包路径，仅返回字段名
    }
}
