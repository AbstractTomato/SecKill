package com.seckill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 登录/注册请求体
 * password 字段接收的是客户端已经做过第一层 MD5 的 32 位十六进制字符串
 */
public class AuthRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式错误")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{32}$", message = "密码必须是客户端 MD5 后的 32 位十六进制字符串")
    private String password;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9]{6}$", message = "验证码格式错误")
    private String captcha;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCaptcha() {
        return captcha;
    }

    public void setCaptcha(String captcha) {
        this.captcha = captcha;
    }
}
