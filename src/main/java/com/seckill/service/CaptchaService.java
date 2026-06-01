package com.seckill.service;

import com.seckill.exception.BusinessException;
import com.seckill.exception.CaptchaException;
import com.seckill.result.ResultCode;
import com.seckill.utils.CaptchaUtil;
import com.seckill.utils.RedisKeyUtil;
import com.seckill.vo.CaptchaVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class CaptchaService {

    private static final Duration CAPTCHA_TTL = Duration.ofSeconds(60 * 10);
    private static final Duration CAPTCHA_RATE_TTL = Duration.ofSeconds(60);
    private static final long CAPTCHA_RATE_LIMIT = 10L;

    private final StringRedisTemplate stringRedisTemplate;

    public CaptchaService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 生成验证码，写入 Redis，返回 base64 图片及有效期
     */
    public CaptchaVO generateCaptcha(String phone, String clientIp) {
        checkCaptchaRateLimit(clientIp);
        return generateCaptcha(phone);
    }

    public CaptchaVO generateCaptcha(String phone) {
        String code = CaptchaUtil.generateCode();
        String key = RedisKeyUtil.captchaKey(phone);

        // 先删旧 key，防止新旧验证码共存
        stringRedisTemplate.delete(key);
        stringRedisTemplate.opsForValue().set(key, code, CAPTCHA_TTL);

        return new CaptchaVO(CaptchaUtil.createImageBase64(code), CAPTCHA_TTL.toSeconds());
    }

    /**
     * 校验验证码，区分大小写，校验通过后立即删除 key（一次性使用）
     */
    public void validateCaptcha(String phone, String inputCaptcha) {
        String key = RedisKeyUtil.captchaKey(phone);
        String storedCaptcha = stringRedisTemplate.opsForValue().get(key);

        if (storedCaptcha == null) {
            throw new BusinessException(ResultCode.CAPTCHA_EXPIRED);
        }

        if (!storedCaptcha.equals(inputCaptcha)) {
            // 输错时自动刷新验证码
            CaptchaVO newCaptcha = generateCaptcha(phone);
            throw new CaptchaException(ResultCode.CAPTCHA_ERROR, newCaptcha);
        }

        stringRedisTemplate.delete(key);
    }

    private void checkCaptchaRateLimit(String clientIp) {
        String key = RedisKeyUtil.captchaRateKey(clientIp);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, CAPTCHA_RATE_TTL);
        }
        if (count != null && count > CAPTCHA_RATE_LIMIT) {
            throw new BusinessException(ResultCode.CAPTCHA_RATE_LIMITED);
        }
    }
}
