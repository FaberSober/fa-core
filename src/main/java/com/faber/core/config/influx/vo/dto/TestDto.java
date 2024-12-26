package com.faber.core.config.influx.vo.dto;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import lombok.Data;

import java.time.Instant;

/**
 * @description:   test
 * @author: zjb
 * @createTime: 2024/12/26
 */
@Data
@Measurement(name = "test")
public class TestDto {
    //ems编号
    @Column(tag = true)
    private String emsId;
    //@ApiModelProperty("设备ID")
    @Column(tag = true)
    private String deviceId;
    //1970-01-01T00:00:00Z 以来的毫秒数
    @Column(timestamp = true)
    private Instant time;
    @Column
    private long commStatus;
}
