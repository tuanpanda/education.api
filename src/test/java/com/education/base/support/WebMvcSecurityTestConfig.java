package com.education.base.support;

import com.education.base.config.SecurityConfig;
import com.education.base.security.JwtTokenService;
import com.education.base.service.AccessControlService;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Nạp {@link SecurityConfig} thật (filter chain JWT, entry point 401, CORS) cho các test {@code @WebMvcTest}.
 * Dịch vụ JWT / phân quyền được mock; người dùng đăng nhập giả lập bằng {@link WithAuthUser}.
 * <p>
 * MockMvc tự động gắn CSRF token hợp lệ cho MỌI request ({@link #csrfByDefault()}) để test nghiệp vụ không phải
 * lặp {@code .with(csrf())}. Hành vi CSRF thật (thiếu / sai token bị 403) được kiểm tra riêng ở
 * {@code CsrfProtectionTest} với MockMvc không có customizer này.
 */
@TestConfiguration
@Import(SecurityConfig.class)
public class WebMvcSecurityTestConfig {

    @Bean
    MockMvcBuilderCustomizer csrfByDefault() {
        return builder -> builder.defaultRequest(MockMvcRequestBuilders.get("/").with(csrf()));
    }

    @Bean
    JwtTokenService jwtTokenService() {
        return Mockito.mock(JwtTokenService.class);
    }

    @Bean
    AccessControlService accessControlService() {
        return Mockito.mock(AccessControlService.class);
    }
}
