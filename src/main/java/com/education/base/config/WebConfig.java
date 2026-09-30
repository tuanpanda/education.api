package com.education.base.config;

import com.education.base.security.PermissionInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * CORS cho {@code /api/**} theo danh sách origin cấu hình ({@link CorsProperties}) và interceptor phân quyền.
 * <p>
 * Frontend gửi JWT qua header {@code Authorization: Bearer} (không dùng cookie) nên không bật
 * {@code allowCredentials}. Khi không cấu hình origin nào thì không đăng ký CORS mapping: request same-origin
 * vẫn chạy bình thường, trình duyệt chặn mọi request cross-origin.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public WebConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = corsProperties.allowedOrigins();
        if (origins.isEmpty()) {
            log.info("CORS: app.cors.allowed-origins rỗng -> chỉ cho phép same-origin.");
            return;
        }
        log.info("CORS: cho phép origin {}", origins);
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(HttpHeaders.CONTENT_DISPOSITION)
                .allowCredentials(false)
                .maxAge(3600);
    }

    /**
     * Kiểm tra quyền menu x chức năng ({@code @RequirePermission}) cho toàn bộ API nghiệp vụ.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PermissionInterceptor()).addPathPatterns("/api/**");
    }
}
