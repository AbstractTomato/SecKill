package com.seckill.controller;

import com.seckill.annotation.RateLimit;
import com.seckill.dto.AuthRequest;
import com.seckill.result.Result;
import com.seckill.service.CaptchaService;
import com.seckill.service.UserService;
import com.seckill.utils.ClientIpUtil;
import com.seckill.vo.CaptchaVO;
import com.seckill.vo.LoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RateLimit
@RestController
public class UserController {

    private final UserService userService;
    private final CaptchaService captchaService;

    public UserController(UserService userService, CaptchaService captchaService) {
        this.userService = userService;
        this.captchaService = captchaService;
    }

    /** 获取/刷新验证码图片 */
    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha(@RequestParam
                                     @Pattern(regexp = "^1\\d{10}$", message = "手机号格式错误")
                                     String phone,
                                     HttpServletRequest request) {
        return Result.success(captchaService.generateCaptcha(phone, ClientIpUtil.resolveClientIp(request)));
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody AuthRequest request) {
        return Result.success(userService.login(request));
    }

    /** 注册 */
    @PostMapping("/register")
    public Result<String> register(@Valid @RequestBody AuthRequest request) {
        userService.register(request);
        return Result.success("注册成功，请重新登录");
    }
}
