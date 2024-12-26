package com.faber.core.config.influx.rest;

import com.faber.core.annotation.FaLogBiz;
import com.faber.core.annotation.FaLogOpr;
import com.faber.core.annotation.LogNoRet;
import com.faber.core.config.influx.service.InfluxDBService;
import com.faber.core.config.influx.vo.dto.TestDto;
import com.faber.core.enums.LogCrudEnum;
import com.faber.core.utils.BaseResHandler;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@FaLogBiz("influx测试")
@RestController
@RequestMapping("/api/base/influx")
public class TestInfluxDBController extends BaseResHandler {

    @Resource
    InfluxDBService influxDBService;

    @FaLogOpr(value = "查询", crud = LogCrudEnum.R)
    @LogNoRet
    @RequestMapping(value = "/query", method = RequestMethod.POST)
    @ResponseBody
    public TestDto query(@RequestBody TestDto test)  throws IllegalAccessException {
        TestDto result = new TestDto();
        try {
            Object response = influxDBService.queryInfluxDB(test.getEmsId(), test.getDeviceId(), new TestDto());
            result = (TestDto) response;
        } catch (Exception e) {
            e.printStackTrace(); // SLF4J
            System.err.println("Failed to query InfluxDB: " + e.getMessage());
        }
        return result;
    }

    @FaLogOpr(value = "写入", crud = LogCrudEnum.R)
    @LogNoRet
    @RequestMapping(value = "/write", method = RequestMethod.POST)
    @ResponseBody
    public boolean write(@RequestBody TestDto test)  throws IllegalAccessException {
        try {
            test.setTime(Instant.now());
            influxDBService.writeToInfluxDB(test);
            return true;
        } catch (Exception e) {
            e.printStackTrace(); // 或者使用更合适的日志框架如 SLF4J
            System.err.println("Failed to write to InfluxDB: " + e.getMessage());
            return false;
        }
    }
}
