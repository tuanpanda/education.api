package com.education.base.config;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Quy tắc an toàn cho JWT secret:
 * <ul>
 *     <li>tối thiểu {@value #MIN_SECRET_BYTES} byte UTF-8 (HS256 cần khóa 256 bit) ở mọi profile;</li>
 *     <li>ở profile {@code prod}: không được dùng secret dev/test/ví dụ đã công khai trong mã nguồn.</li>
 * </ul>
 */
public final class JwtSecretPolicy {

    public static final int MIN_SECRET_BYTES = 32;

    public static final String PROD_PROFILE = "prod";

    /** Các secret đã công khai trong repo (application-dev.yml, application-test.yml, .env.example). */
    static final Set<String> KNOWN_NON_PRODUCTION_SECRETS = Set.of(
            "dev-only-education-jwt-secret-change-me-0123456789",
            "test-only-education-jwt-secret-0123456789abcdef",
            "replace-with-a-random-secret-of-at-least-32-characters");

    private static final String[] NON_PRODUCTION_MARKERS = {"dev-only-", "test-only-", "replace-with-", "change-me"};

    private JwtSecretPolicy() {
    }

    /**
     * @throws IllegalStateException nếu secret rỗng hoặc ngắn hơn {@value #MIN_SECRET_BYTES} byte UTF-8.
     */
    public static void requireMinimumLength(String secret) {
        int bytes = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.security.jwt.secret (JWT_SECRET) phải có tối thiểu "
                    + MIN_SECRET_BYTES + " byte UTF-8 (hiện có " + bytes + " byte). "
                    + "Tạo secret ngẫu nhiên, ví dụ: openssl rand -base64 48");
        }
    }

    /**
     * Kiểm tra đầy đủ khi khởi động.
     *
     * @param prodProfileActive {@code true} nếu profile {@code prod} đang bật.
     * @throws IllegalStateException nếu secret không đạt yêu cầu.
     */
    public static void validate(String secret, boolean prodProfileActive) {
        requireMinimumLength(secret);
        if (prodProfileActive && isKnownNonProductionSecret(secret)) {
            throw new IllegalStateException("Profile prod đang dùng JWT secret dev/test/ví dụ đã công khai "
                    + "trong mã nguồn. Đặt biến môi trường JWT_SECRET bằng một secret ngẫu nhiên riêng "
                    + "(>= " + MIN_SECRET_BYTES + " byte, ví dụ: openssl rand -base64 48).");
        }
    }

    static boolean isKnownNonProductionSecret(String secret) {
        if (secret == null) {
            return false;
        }
        String trimmed = secret.trim();
        if (KNOWN_NON_PRODUCTION_SECRETS.contains(trimmed)) {
            return true;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String marker : NON_PRODUCTION_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
