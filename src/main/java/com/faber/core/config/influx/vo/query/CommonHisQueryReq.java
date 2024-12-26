package com.faber.core.config.influx.vo.query;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * @description: influxDB通用历史查询
 * @author: zjb
 * @createTime: 2024/12/26
 */
@Data
public class CommonHisQueryReq {
    private String emsId;
    private String deviceId;
    private String tableName;
    //查询字段
    private List<String> fieldNameList;
    //筛选条件
    private List<String> fiter;
    //开始时间
    private Instant startTime;
    //结束时间
    private Instant endTime;
    //查询对象
    private Object queryObject;

    private String fluxCondifiton;
}
