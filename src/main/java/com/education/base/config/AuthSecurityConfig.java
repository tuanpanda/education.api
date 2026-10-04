package com.education.base.config;

import com.education.base.security.AuthRateLimitFilter;
import com.education.base.security.JwtTokenService;
import com.education.base.security.RateLimiter;
import com.education.base.security.RequestRateLimitFilter;
import com.education.base.security.SlidingWindowRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Chống dò mật khẩu / lạm dụng API: bật {@link AuthSecurityProperties} (khóa tạm thời tài khoản, giới hạn đăng nhập
 * / làm mới token), {@link RateLimitProperties} (quy tắc tổng quát: đổi mật khẩu, upload, {@code /api/v1/portal/**})
 * và đăng ký hai filter dùng chung một {@link RateLimiter}.
 */
@Configuration
@EnableConfigurationProperties({AuthSecurityProperties.class, RateLimitProperties.class})
public class AuthSecurityConfig {

    /** Ngay sau chuỗi filter của Spring Security (đã gắn header CORS cho phản hồi). */
    static final int RATE_LIMIT_FILTER_ORDER = SecurityProperties.DEFAULT_FILTER_ORDER + 1;

    /** Filter quy tắc tổng quát chạy sau filter đăng nhập / refresh. */
    static final int REQUEST_RATE_LIMIT_FILTER_ORDER = RATE_LIMIT_FILTER_ORDER + 1;

    /**
     * Bộ đếm trong bộ nhớ (theo từng instance). Chạy nhiều instance API: khai báo bean {@link RateLimiter} dùng chung
     * (Redis / DB) để thay thế.
     */
    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter rateLimiter(AuthSecurityProperties authProperties, RateLimitProperties rateLimitProperties,
                                   Clock clock) {
        int maxTrackedKeys = Math.max(authProperties.getRateLimit().getMaxTrackedKeys(),
                rateLimitProperties.getMaxTrackedKeys());
        return new SlidingWindowRateLimiter(clock, maxTrackedKeys);
    }

    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilter(AuthSecurityProperties properties,
                                                                           ObjectMapper objectMapper,
                                                                           JwtTokenService jwtTokenService,
                                                                           RateLimiter rateLimiter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>(
                new AuthRateLimitFilter(rateLimiter, properties.getRateLimit(), objectMapper, jwtTokenService));
        registration.addUrlPatterns(AuthRateLimitFilter.LOGIN_PATH, AuthRateLimitFilter.REFRESH_PATH);
        registration.setOrder(RATE_LIMIT_FILTER_ORDER);
        registration.setName("authRateLimitFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequestRateLimitFilter> requestRateLimitFilter(RateLimitProperties properties,
                                                                                 ObjectMapper objectMapper,
                                                                                 RateLimiter rateLimiter) {
        FilterRegistrationBean<RequestRateLimitFilter> registration = new FilterRegistrationBean<>(
                new RequestRateLimitFilter(rateLimiter, properties, objectMapper));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(REQUEST_RATE_LIMIT_FILTER_ORDER);
        registration.setName("requestRateLimitFilter");
        return registration;
    }
}
