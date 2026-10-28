package com.faber.core.vo.socket;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;


/**
 * socket任务通用vo
 */
@Data
public class SocketTaskVo implements Serializable {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 任务执行总数
     */
    private int total;

    /**
     * 任务执行当前数量
     */
    private int cur;

    /**
     * 任务执行错误数量
     */
    private int error;

    /** 任务状态：RUNNING、COMPLETED、FAILED；未设置时兼容原进度协议。 */
    private String status;

    /** 任务完成结果或失败原因。 */
    private String message;

    /**
     * 任务开始时间
     */
    private Date startTime = new Date();

    public synchronized void addCur() {
        this.cur++;
    }
    
}
