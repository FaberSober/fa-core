package com.faber.core.config.influx.entity;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.time.Instant;

/**
 * @Description influxDB的操作实体类
 * @Author zjb
 * @Date 2024/12/25
 * @Version
 **/
@Data
@Builder
public class InfluxSQL {
    private Instant start;
    private Instant stop;
    private String measurement;
    private String emsId;
    private String deviceId;
    private String field;
    private String every;
    private Boolean last;
    private Boolean first;
    private Boolean mean;
    private String sum;

    // 复制构造函数
    public InfluxSQL(InfluxSQL other, Instant start, Instant stop) {
        this.start = start;
        this.stop = stop;
        this.measurement = other.measurement;
        this.emsId = other.emsId;
        this.deviceId = other.deviceId;
        this.field = other.field;
        this.every = other.every;
        this.last = other.last;
        this.first = other.first;
        this.mean = other.mean;
        this.sum = other.sum;

    }

    public InfluxSQL(Instant start, Instant stop, String measurement, String emsId, String deviceId, String field, String every, Boolean last, Boolean first, Boolean mean, String sum) {
        this.start = start;
        this.stop = stop;
        this.measurement = measurement;
        this.emsId = emsId;
        this.deviceId = deviceId;
        this.field = field;
        this.every = every;
        this.last = last;
        this.first = first;
        this.mean = mean;
        this.sum = sum;
    }

    /**
     * 参数类型
     */
    @Getter
    public enum ParamType {
        MEASUREMENT("_measurement"),
        DEVICEID("deviceId"),
        EMSID("emsId"),
        FIELD("_field");
        public final String code;
        ParamType(String code) {
            this.code = code;
        }
    }

}
