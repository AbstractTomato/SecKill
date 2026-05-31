package com.seckill.vo;

/**
 * 存入 Redis 的用户会话信息，不包含敏感字段（password、salt）
 */
public class UserSession {

    private Long id;
    private String phone;
    private String nickname;

    public UserSession() {
    }

    public UserSession(Long id, String phone, String nickname) {
        this.id = id;
        this.phone = phone;
        this.nickname = nickname;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
