package com.seckill.vo;

/**
 * 验证码响应 —— base64 图片 + 过期秒数
 */
public class CaptchaVO {

    private String imageBase64;
    private long expireSeconds;

    public CaptchaVO() {
    }

    public CaptchaVO(String imageBase64, long expireSeconds) {
        this.imageBase64 = imageBase64;
        this.expireSeconds = expireSeconds;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(long expireSeconds) {
        this.expireSeconds = expireSeconds;
    }
}
