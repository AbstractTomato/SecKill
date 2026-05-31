package com.seckill.exception;

import com.seckill.result.ResultCode;

/**
 * 通用业务异常，携带 ResultCode 供 GlobalExceptionHandler 统一处理
 */
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public ResultCode getResultCode() {
        return resultCode;
    }
}
