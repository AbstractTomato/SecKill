package com.seckill.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.seckill.annotation.RateLimit;
import com.seckill.result.Result;
import com.seckill.result.ResultCode;
import com.seckill.utils.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final double PERMITS_PER_SECOND = 50.0;

    private final ConcurrentHashMap<String, RateLimiter> ipLimiters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod) || !shouldRateLimit(handlerMethod)) {
            return true;
        }

        String clientIp = ClientIpUtil.resolveClientIp(request);
        RateLimiter rateLimiter = ipLimiters.computeIfAbsent(clientIp, key -> RateLimiter.create(PERMITS_PER_SECOND));
        if (rateLimiter.tryAcquire()) {
            return true;
        }

        writeRateLimitedResponse(response);
        return false;
    }

    private boolean shouldRateLimit(HandlerMethod handlerMethod) {
        return handlerMethod.getBeanType().isAnnotationPresent(RateLimit.class)
                || handlerMethod.getMethod().isAnnotationPresent(RateLimit.class);
    }

    private void writeRateLimitedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(ResultCode.RATE_LIMITED));
    }
}
