package com.example.aidoc.common;

/**
 * 业务异常，由全局异常处理器转换为统一响应。
 */
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }

    public BizException(String message, Throwable cause) {
        super(message, cause);
    }
}
