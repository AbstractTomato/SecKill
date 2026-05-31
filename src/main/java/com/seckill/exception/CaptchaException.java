package com.seckill.exception;

import com.seckill.result.ResultCode;
import com.seckill.vo.CaptchaVO;

/**
 * 验证码错误异常 —— 携带自动刷新的新验证码，返回给前端直接替换展示
 */
public class CaptchaException extends BusinessException {

    private final CaptchaVO captcha;

    public CaptchaException(ResultCode resultCode, CaptchaVO captcha) {
        super(resultCode);
        this.captcha = captcha;
    }

    public CaptchaVO getCaptcha() {
        return captcha;
    }
}
