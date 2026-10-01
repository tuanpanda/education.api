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
 * JWT nằm trong cookie HttpOnly nên bật {@code allowCredentials} (frontend gọi {@code fetch} với
 * {@code credentials: 'include'}); vì vậy chỉ chấp nhận origin khai báo tường minh ({@link CorsProperties} từ chối
 * {@code *}) và chỉ cho phép các header cần thiết ({@link #ALLOWED_HEADERS}). Khi không
 * cấu hình origin nào thì không đăng ký CORS mapping: request same-origin vẫn chạy bình thường, trình duyệt chặn
 * mọi request cross-origin.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    /** Header request được phép gửi cross-origin. */
    static final String[] ALLOWED_HEADERS = {
            HttpHeaders.ACCEPT, HttpHeaders.ACCEPT_LANGUAGE, HttpHeaders.CONTENT_TYPE, "X-Requested-With"
    };

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
                .allowedHeaders(ALLOWED_HEADERS)
                .exposedHeaders(HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.RETRY_AFTER)
                .allowCredentials(true)
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
