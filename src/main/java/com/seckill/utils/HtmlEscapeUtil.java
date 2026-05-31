package com.seckill.utils;

/**
 * 极小范围的 HTML 转义工具。
 *
 * <p>商品页 HTML 由服务端拼接并缓存到 Redis。商品名称、标题、详情这类字段
 * 来自数据库，进入 HTML 前必须转义，避免数据库里出现特殊字符时破坏页面结构，
 * 也避免后续管理后台接入后产生 XSS 风险。</p>
 */
public final class HtmlEscapeUtil {

    private HtmlEscapeUtil() {
    }

    public static String escape(String source) {
        if (source == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            switch (ch) {
                case '&' -> builder.append("&amp;");
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '"' -> builder.append("&quot;");
                case '\'' -> builder.append("&#39;");
                default -> builder.append(ch);
            }
        }
        return builder.toString();
    }
}
