package com.education.base.security;

import java.time.Duration;

/**
 * Bộ giới hạn tần suất theo khóa (IP, người dùng...). Cài đặt mặc định {@link SlidingWindowRateLimiter} lưu trong
 * bộ nhớ, theo từng instance; khi chạy nhiều instance API có thể thay bằng cài đặt dùng chung (Redis / DB) chỉ bằng
 * cách khai báo một bean {@code RateLimiter} khác ({@code AuthSecurityConfig} dùng {@code @ConditionalOnMissingBean}).
 */
public interface RateLimiter {

    /**
     * Ghi nhận một request cho {@code key} nếu chưa vượt {@code limit} trong {@code window}.
     *
     * @return kết quả; khi bị từ chối kèm số giây nên chờ.
     */
    Decision tryAcquire(String key, int limit, Duration window);

    /**
     * @param allowed           {@code true} nếu request được chấp nhận.
     * @param retryAfterSeconds số giây nên chờ khi bị từ chối (0 nếu được chấp nhận).
     */
    record Decision(boolean allowed, long retryAfterSeconds) {

        public static final Decision ALLOWED = new Decision(true, 0);

        public static Decision rejected(long retryAfterSeconds) {
            return new Decision(false, Math.max(retryAfterSeconds, 1));
        }
    }
}
