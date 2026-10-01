package com.education.base.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Thuộc tính cookie phiên đăng nhập (access token, refresh token, CSRF), tiền tố {@code app.security.cookie}.
 * <ul>
 *     <li>{@code secure} ({@code AUTH_COOKIE_SECURE}): gắn thuộc tính {@code Secure} - trình duyệt chỉ gửi cookie
 *     qua HTTPS. Mặc định {@code true}; profile {@code dev} mặc định {@code false} (chạy http://localhost).
 *     Chạy production qua HTTP thuần (không phải localhost) thì trình duyệt bỏ cookie {@code Secure} và không
 *     đăng nhập được: phải dùng HTTPS hoặc đặt {@code AUTH_COOKIE_SECURE=false} (không khuyến nghị).</li>
 *     <li>{@code same-site} ({@code AUTH_COOKIE_SAME_SITE}): {@code Strict} (mặc định) hoặc {@code Lax}.
 *     Không hỗ trợ {@code None}: UI và API phải cùng "site" (cùng host khác cổng, hoặc cùng tên miền gốc).</li>
 * </ul>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.security.cookie")
public class AuthCookieProperties {

    private boolean secure = true;

    @NotNull
    @Pattern(regexp = "(?i)strict|lax", message = "app.security.cookie.same-site chỉ nhận Strict hoặc Lax")
    private String sameSite = "Strict";

    /** Giá trị SameSite đã chuẩn hóa ({@code Strict} / {@code Lax}). */
    public String normalizedSameSite() {
        return "lax".equalsIgnoreCase(sameSite) ? "Lax" : "Strict";
    }
}
