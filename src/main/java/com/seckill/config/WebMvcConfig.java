package com.seckill.config;

import com.seckill.resolver.UserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 注册 UserArgumentResolver，使 Controller 可直接声明 UserSession 参数
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserArgumentResolver userArgumentResolver;

    public WebMvcConfig(UserArgumentResolver userArgumentResolver) {
        this.userArgumentResolver = userArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(userArgumentResolver);
    }
}
