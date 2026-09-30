package com.education.base.config;

import com.education.base.security.AuthRateLimitFilter;
import com.education.base.security.JwtTokenService;
import com.education.base.security.SlidingWindowRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Chống dò mật khẩu: bật {@link AuthSecurityProperties} (khóa tạm thời tài khoản, giới hạn tần suất) và
 * đăng ký {@link AuthRateLimitFilter} cho {@code /api/v1/auth/login}, {@code /api/v1/auth/refresh}.
 */
@Configuration
@EnableConfigurationProperties(AuthSecurityProperties.class)
public class AuthSecurityConfig {

    /** Ngay sau chuỗi filter của Spring Security (đã gắn header CORS cho phản hồi). */
    static final int RATE_LIMIT_FILTER_ORDER = SecurityProperties.DEFAULT_FILTER_ORDER + 1;

    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilter(AuthSecurityProperties properties,
                                                                           ObjectMapper objectMapper,
                                                                           JwtTokenService jwtTokenService,
                                                                           Clock clock) {
        AuthSecurityProperties.RateLimit config = properties.getRateLimit();
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(clock, config.getMaxTrackedKeys());
        FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>(
                new AuthRateLimitFilter(limiter, config, objectMapper, jwtTokenService));
        registration.addUrlPatterns(AuthRateLimitFilter.LOGIN_PATH, AuthRateLimitFilter.REFRESH_PATH);
        registration.setOrder(RATE_LIMIT_FILTER_ORDER);
        registration.setName("authRateLimitFilter");
        return registration;
    }
}
