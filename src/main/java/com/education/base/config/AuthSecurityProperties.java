package com.education.base.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Chống dò mật khẩu cho {@code /api/v1/auth/**}, tiền tố {@code app.security.auth}.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.security.auth")
public class AuthSecurityProperties {

    @Valid
    @NotNull
    private Lockout lockout = new Lockout();

    @Valid
    @NotNull
    private RateLimit rateLimit = new RateLimit();

    /**
     * Khóa tạm thời tài khoản khi đăng nhập sai liên tiếp: cứ mỗi {@code maxFailedAttempts} lần sai thì khóa;
     * lần khóa thứ n kéo dài {@code baseDuration * 2^(n-1)}, tối đa {@code maxDuration}.
     */
    @Data
    public static class Lockout {

        @Min(1)
        private int maxFailedAttempts = 5;

        @NotNull
        private Duration baseDuration = Duration.ofMinutes(15);

        @NotNull
        private Duration maxDuration = Duration.ofHours(24);
    }

    /**
     * Giới hạn tần suất (cửa sổ trượt, trong bộ nhớ, theo từng instance) cho đăng nhập và làm mới token.
     */
    @Data
    public static class RateLimit {

        private boolean enabled = true;

        @NotNull
        private Duration window = Duration.ofMinutes(5);

        /**
         * Số request đăng nhập tối đa mỗi IP trong một cửa sổ. Rộng (200 / 5 phút) vì cả trung tâm có thể dùng chung
         * một IP public (NAT): cả lớp học sinh đăng nhập cùng lúc. Từng tài khoản vẫn được bảo vệ bởi
         * {@link #loginPerUsername} và khóa tài khoản tạm thời.
         */
        @Min(1)
        private int loginPerIp = 200;

        /** Số request đăng nhập tối đa mỗi tên đăng nhập trong một cửa sổ. */
        @Min(1)
        private int loginPerUsername = 10;

        /** Số request làm mới token tối đa mỗi IP trong một cửa sổ. */
        @Min(1)
        private int refreshPerIp = 120;

        /** Số request làm mới token tối đa mỗi người dùng (theo refresh token hợp lệ) trong một cửa sổ. */
        @Min(1)
        private int refreshPerUser = 60;

        /** Số khóa (IP / tên đăng nhập) tối đa được theo dõi trong bộ nhớ. */
        @Min(100)
        private int maxTrackedKeys = 100_000;
    }
}
