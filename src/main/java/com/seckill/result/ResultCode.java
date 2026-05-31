package com.seckill.result;

/**
 * 业务错误码枚举
 */
public enum ResultCode {

    SUCCESS(0, "success"),
    PARAM_ERROR(40000, "参数错误"),
    CAPTCHA_EXPIRED(40001, "验证码已失效，请刷新"),
    CAPTCHA_ERROR(40002, "验证码错误"),
    USER_EXISTS(40003, "手机号已注册"),
    USER_NOT_FOUND(40004, "用户不存在，请先注册"),
    PASSWORD_ERROR(40005, "密码错误"),
    UNAUTHORIZED(40006, "未登录或登录已过期"),
    GOODS_NOT_FOUND(40007, "商品不存在或不在本次秒杀活动中"),
    SECKILL_NOT_STARTED(40008, "秒杀尚未开始"),
    SECKILL_ENDED(40009, "秒杀已结束"),
    SECKILL_REPEAT(40010, "此商品已抢过"),
    SECKILL_SOLD_OUT(40011, "已售罄"),
    SECKILL_BUSY(40012, "系统繁忙，请稍后重试"),
    SYSTEM_ERROR(50000, "系统异常");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
