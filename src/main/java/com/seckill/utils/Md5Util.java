package com.seckill.utils;

import org.apache.commons.codec.digest.DigestUtils;

/**
 * MD5 工具 —— 第二层加密：表单密码 + salt → 数据库密码
 * 第一层加密（原始密码 → 表单密码）由客户端完成
 */
public final class Md5Util {

    private Md5Util() {
    }

    public static String md5(String source) {
        return DigestUtils.md5Hex(source);
    }

    /** 第二层：MD5(客户端MD5 + salt) */
    public static String formPassToDbPass(String formPass, String salt) {
        return md5(formPass + salt);
    }
}
