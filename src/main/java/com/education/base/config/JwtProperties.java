package com.education.base.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Cấu hình JWT, ánh xạ từ tiền tố {@code app.security.jwt}.
 * <p>
 * Secret lấy từ biến môi trường {@code JWT_SECRET}. Chỉ profile {@code dev} (mặc định khi chạy local,
 * xem {@code application-dev.yml}) và profile test có giá trị mặc định; các profile khác (đặc biệt {@code prod})
 * không khởi động được nếu thiếu secret. Độ dài tối thiểu (32 byte UTF-8) và việc cấm secret dev/test ở
 * profile {@code prod} được kiểm tra bởi {@link JwtSecretPolicy} / {@link JwtSecretStartupValidator}.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    /**
     * Khóa ký HMAC-SHA (chuỗi UTF-8, tối thiểu 32 byte để đạt 256 bit cho HS256).
     */
    @NotBlank(message = "app.security.jwt.secret (JWT_SECRET) không được để trống")
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

    /**
     * Khoảng "ân hạn" cho refresh token vừa bị xoay vòng: nhiều tab cùng làm mới bằng một refresh token
     * trong khoảng này không bị coi là dùng lại (không thu hồi cả phiên). {@code 0} để tắt.
     */
    @NotNull
    private Duration refreshReuseGrace = Duration.ofSeconds(10);
}
