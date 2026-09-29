package com.education.base.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Cấu hình JWT, ánh xạ từ tiền tố {@code app.security.jwt} trong {@code application.yml}.
 * <p>
 * Secret đọc từ biến môi trường {@code JWT_SECRET}. Profile {@code prod} KHÔNG có giá trị mặc định,
 * ứng dụng sẽ không khởi động nếu thiếu secret.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    /**
     * Khóa ký HMAC-SHA (chuỗi UTF-8, tối thiểu 32 ký tự để đạt 256 bit cho HS256).
     */
    @NotBlank(message = "app.security.jwt.secret (JWT_SECRET) không được để trống")
    @Size(min = 32, message = "app.security.jwt.secret (JWT_SECRET) phải có tối thiểu 32 ký tự")
    private String secret;

    /** Giá trị claim {@code iss}. */
    @NotBlank
    private String issuer = "education-api";

    /** Thời hạn access token (mặc định 15 phút). */
    @NotNull
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    /** Thời hạn refresh token (mặc định 7 ngày). */
    @NotNull
    private Duration refreshTokenTtl = Duration.ofDays(7);
}
