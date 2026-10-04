package com.education.base.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kiểm tra cấu hình khi API được đưa ra Internet ({@code app.security.internet-facing=true}, biến môi trường
 * {@code APP_INTERNET_FACING} - {@code docker-compose.prod.yml} bật sẵn). Mặc định TẮT để Docker cục bộ (profile
 * {@code prod} trên http://localhost / IP LAN) chạy như cũ.
 * <p>
 * Khi bật, ứng dụng TỪ CHỐI KHỞI ĐỘNG nếu:
 * <ul>
 *     <li>cookie đăng nhập không {@code Secure} ({@code AUTH_COOKIE_SECURE=false});</li>
 *     <li>CORS cho phép origin không phải {@code https://} ({@code CORS_ALLOWED_ORIGINS}).</li>
 * </ul>
 * và cảnh báo (WARN) nếu: tắt giới hạn tần suất, {@code server.forward-headers-strategy=none} (mọi request mang IP
 * của reverse proxy -> giới hạn theo IP và nhật ký mất ý nghĩa), hoặc Swagger / OpenAPI đang bật.
 * Ngoài chế độ Internet, profile {@code prod} với cookie không {@code Secure} chỉ ghi WARN.
 */
@Slf4j
@Component
public class InternetExposureValidator implements InitializingBean {

    static final String PROPERTY = "app.security.internet-facing";

    private final Environment environment;
    private final AuthCookieProperties cookieProperties;
    private final CorsProperties corsProperties;

    public InternetExposureValidator(Environment environment, AuthCookieProperties cookieProperties,
                                     CorsProperties corsProperties) {
        this.environment = environment;
        this.cookieProperties = cookieProperties;
        this.corsProperties = corsProperties;
    }

    @Override
    public void afterPropertiesSet() {
        boolean internetFacing = environment.getProperty(PROPERTY, Boolean.class, false);
        if (!internetFacing) {
            if (environment.acceptsProfiles(Profiles.of("prod")) && !cookieProperties.isSecure()) {
                log.warn("Cookie đăng nhập KHÔNG Secure (AUTH_COOKIE_SECURE=false). Chỉ chấp nhận trong mạng nội bộ; "
                        + "đưa ra Internet phải dùng HTTPS và đặt APP_INTERNET_FACING=true.");
            }
            return;
        }
        List<String> errors = new ArrayList<>();
        if (!cookieProperties.isSecure()) {
            errors.add("AUTH_COOKIE_SECURE phải là true (cookie đăng nhập chỉ gửi qua HTTPS)");
        }
        for (String origin : corsProperties.allowedOrigins()) {
            if (!origin.toLowerCase(Locale.ROOT).startsWith("https://")) {
                errors.add("CORS_ALLOWED_ORIGINS chỉ được chứa origin https:// (gặp '" + origin + "')");
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Cấu hình không an toàn cho triển khai Internet ("
                    + PROPERTY + "=true): " + String.join("; ", errors));
        }
        warnIf(!environment.getProperty("app.security.auth.rate-limit.enabled", Boolean.class, true),
                "Giới hạn tần suất đăng nhập đang TẮT (AUTH_RATE_LIMIT_ENABLED=false).");
        warnIf(!environment.getProperty("app.security.rate-limit.enabled", Boolean.class, true),
                "Giới hạn tần suất tổng quát đang TẮT (RATE_LIMIT_ENABLED=false).");
        warnIf("none".equalsIgnoreCase(environment.getProperty("server.forward-headers-strategy", "")),
                "server.forward-headers-strategy=none: sau reverse proxy mọi request mang IP của proxy.");
        warnIf(environment.getProperty("springdoc.api-docs.enabled", Boolean.class, true),
                "Swagger / OpenAPI đang bật trên môi trường Internet.");
        log.info("Chế độ Internet: cookie Secure, CORS {} - OK.",
                corsProperties.allowedOrigins().isEmpty() ? "same-origin" : corsProperties.allowedOrigins());
    }

    private static void warnIf(boolean condition, String message) {
        if (condition) {
            log.warn("[Internet] {}", message);
        }
    }
}
