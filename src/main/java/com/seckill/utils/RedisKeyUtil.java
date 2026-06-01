package com.seckill.utils;

/**
 * Redis Key 命名工具，统一管理 key 格式，避免散落字符串
 */
public final class RedisKeyUtil {

    private RedisKeyUtil() {
    }

    /** 验证码 key: captcha:<手机号> */
    public static String captchaKey(String phone) {
        return "captcha:" + phone;
    }

    public static String captchaRateKey(String ip) {
        return "captcha:rate:" + ip;
    }

    /** 登录 token key: token:<token> */
    public static String tokenKey(String token) {
        return "token:" + token;
    }

    public static String goodsListPageKey() {
        return "goods:list:page";
    }

    public static String goodsDetailPageKey(Long goodsId) {
        return "goods:detail:page:" + goodsId;
    }

    public static String goodsListKey() {
        return "goods:list";
    }

    public static String seckillStockKey(Long goodsId) {
        return "seckill:stock:" + goodsId;
    }

    public static String seckillOrderedKey(Long goodsId) {
        return "seckill:ordered:" + goodsId;
    }

    public static String seckillMessageKey(Long goodsId, Long userId) {
        return "seckill:msg:" + goodsId + ":" + userId;
    }

    public static String seckillRetryKey(Long goodsId, Long userId) {
        return "seckill:retry:" + goodsId + ":" + userId;
    }

    public static String seckillRateKey(Long goodsId, Long userId) {
        return "seckill:rate:" + goodsId + ":" + userId;
    }

    public static String goodsListLockKey() {
        return "goods:list:lock";
    }
}
