package com.seckill.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.bean.User;
import com.seckill.dto.AuthRequest;
import com.seckill.exception.BusinessException;
import com.seckill.mapper.UserMapper;
import com.seckill.result.ResultCode;
import com.seckill.utils.Md5Util;
import com.seckill.utils.RandomUtil;
import com.seckill.utils.RedisKeyUtil;
import com.seckill.vo.LoginVO;
import com.seckill.vo.UserSession;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
public class UserService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final int SALT_LENGTH = 10;

    private final UserMapper userMapper;
    private final CaptchaService captchaService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public UserService(UserMapper userMapper,
                       CaptchaService captchaService,
                       StringRedisTemplate stringRedisTemplate,
                       ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.captchaService = captchaService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 登录：先校验验证码，再校验密码，成功返回 token
     */
    public LoginVO login(AuthRequest request) {
        // 先过验证码，拦截无效请求，不查 DB
        captchaService.validateCaptcha(request.getPhone(), request.getCaptcha());

        User user = userMapper.selectByPhone(request.getPhone());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 第二层 MD5：表单密码 + salt
        String dbPassword = Md5Util.formPassToDbPass(request.getPassword(), user.getSalt());
        if (!dbPassword.equals(user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        userMapper.updateLastLoginDate(user.getId());

        UserSession session = toSession(user);
        String token = UUID.randomUUID().toString().replace("-", "");
        saveToken(token, session);
        return new LoginVO(token, session);
    }

    /**
     * 注册：先校验验证码，再检查手机号唯一性，写入新用户
     */
    @Transactional
    public void register(AuthRequest request) {
        captchaService.validateCaptcha(request.getPhone(), request.getCaptcha());

        User existingUser = userMapper.selectByPhone(request.getPhone());
        if (existingUser != null) {
            throw new BusinessException(ResultCode.USER_EXISTS);
        }

        String salt = RandomUtil.randomSalt(SALT_LENGTH);
        User user = new User();
        user.setPhone(request.getPhone());
        user.setNickname(RandomUtil.randomNickname());
        user.setSalt(salt);
        // 注册时用第二层 MD5 加密后入库
        user.setPassword(Md5Util.formPassToDbPass(request.getPassword(), salt));

        userMapper.insert(user);
    }

    /**
     * 从 Redis 中根据 token 获取用户 session，token 不存在或已过期返回 null
     */
    public UserSession getByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String json = stringRedisTemplate.opsForValue().get(RedisKeyUtil.tokenKey(token));
        if (json == null) {
            return null;
        }

        try {
            return objectMapper.readValue(json, UserSession.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private void saveToken(String token, UserSession userSession) {
        try {
            String json = objectMapper.writeValueAsString(userSession);
            stringRedisTemplate.opsForValue().set(RedisKeyUtil.tokenKey(token), json, TOKEN_TTL);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }

    private UserSession toSession(User user) {
        return new UserSession(user.getId(), user.getPhone(), user.getNickname());
    }
}
