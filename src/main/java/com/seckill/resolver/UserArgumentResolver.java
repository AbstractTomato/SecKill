package com.seckill.resolver;

import com.seckill.exception.BusinessException;
import com.seckill.result.ResultCode;
import com.seckill.service.UserService;
import com.seckill.vo.UserSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 自动解析当前登录用户 —— Controller 参数上声明 UserSession 即可注入
 */
@Component
public class UserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserService userService;

    public UserArgumentResolver(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == UserSession.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        UserSession userSession = userService.getByToken(resolveToken(request));
        if (userSession == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userSession;
    }

    /** 从请求头解析 token，优先取 token 头，其次取 Authorization: Bearer xxx */
    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader("token");
        if (token != null && !token.isBlank()) {
            return token;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }

        return null;
    }
}
