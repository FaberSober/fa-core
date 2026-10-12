package com.faber.core.exception;

/**
 * 业务异常，仅记录警告日志，不记录错误堆栈。
 */
public class BuzzWarnException extends BuzzException {
    public BuzzWarnException(String message) {
        super(message);
    }
}
