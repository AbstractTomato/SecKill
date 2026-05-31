package com.seckill.vo;

/**
 * 登录响应 —— token + 用户信息
 */
public class LoginVO {

    private String token;
    private UserSession user;

    public LoginVO() {
    }

    public LoginVO(String token, UserSession user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserSession getUser() {
        return user;
    }

    public void setUser(UserSession user) {
        this.user = user;
    }
}
