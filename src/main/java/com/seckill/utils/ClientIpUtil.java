package com.seckill.utils;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpUtil {

    private static final String UNKNOWN = "unknown";

    private ClientIpUtil() {
    }

    public static String resolveClientIp(HttpServletRequest request) {
        String ip = firstValidIp(request.getHeader("X-Forwarded-For"));
        if (ip == null) {
            ip = firstValidIp(request.getHeader("X-Real-IP"));
        }
        if (ip == null) {
            ip = firstValidIp(request.getHeader("Proxy-Client-IP"));
        }
        if (ip == null) {
            ip = firstValidIp(request.getHeader("WL-Proxy-Client-IP"));
        }
        if (ip == null) {
            ip = request.getRemoteAddr();
        }
        return ip == null || ip.isBlank() ? UNKNOWN : ip.trim();
    }

    private static String firstValidIp(String headerValue) {
        if (headerValue == null || headerValue.isBlank() || UNKNOWN.equalsIgnoreCase(headerValue)) {
            return null;
        }

        String[] candidates = headerValue.split(",");
        for (String candidate : candidates) {
            String ip = candidate.trim();
            if (!ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip)) {
                return ip;
            }
        }
        return null;
    }
}
