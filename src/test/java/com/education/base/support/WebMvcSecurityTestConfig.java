package com.education.base.support;

import com.education.base.config.SecurityConfig;
import com.education.base.security.JwtTokenService;
import com.education.base.service.AccessControlService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Nạp {@link SecurityConfig} thật (filter chain JWT, entry point 401, CORS) cho các test {@code @WebMvcTest}.
 * Dịch vụ JWT / phân quyền được mock; người dùng đăng nhập giả lập bằng {@link WithAuthUser}.
 */
@TestConfiguration
@Import(SecurityConfig.class)
public class WebMvcSecurityTestConfig {

    @Bean
    JwtTokenService jwtTokenService() {
        return Mockito.mock(JwtTokenService.class);
    }

    @Bean
    AccessControlService accessControlService() {
        return Mockito.mock(AccessControlService.class);
    }
}
