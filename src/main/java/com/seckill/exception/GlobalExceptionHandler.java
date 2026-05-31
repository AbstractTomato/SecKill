package com.seckill.exception;

import com.seckill.result.Result;
import com.seckill.result.ResultCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 验证码错误 —— 响应中携带新验证码图片，前端可直接替换展示 */
    @ExceptionHandler(CaptchaException.class)
    public Result<?> handleCaptchaException(CaptchaException e) {
        return Result.fail(e.getResultCode(), e.getCaptcha());
    }

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        return Result.fail(e.getResultCode());
    }

    /** 并发注册时依赖 DB UNIQUE 约束兜底，返回手机号已注册 */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<?> handleDuplicateKeyException(DuplicateKeyException e) {
        return Result.fail(ResultCode.USER_EXISTS);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? ResultCode.PARAM_ERROR.getMessage() : fieldError.getDefaultMessage();
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) {
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), e.getMessage());
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }
}
