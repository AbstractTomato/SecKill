package com.seckill.utils;

import java.security.SecureRandom;

/**
 * 随机生成工具 —— salt、昵称
 */
public final class RandomUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] SALT_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private RandomUtil() {
    }

    /** 生成指定长度的随机字符串作为用户 salt */
    public static String randomSalt(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(SALT_CHARS[RANDOM.nextInt(SALT_CHARS.length)]);
        }
        return builder.toString();
    }

    /** 生成随机昵称：用户0000 ~ 用户9999 */
    public static String randomNickname() {
        int number = RANDOM.nextInt(10000);
        return String.format("用户%04d", number);
    }
}
