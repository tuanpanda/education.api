package com.education.base.security;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bộ giới hạn tần suất cửa sổ trượt (sliding log) lưu trong bộ nhớ, an toàn đa luồng.
 * <p>
 * Mỗi khóa giữ danh sách thời điểm các request được chấp nhận trong cửa sổ gần nhất. Chỉ phù hợp cho các
 * endpoint lưu lượng thấp (đăng nhập, làm mới token) và áp dụng theo từng instance ứng dụng.
 */
public class SlidingWindowRateLimiter {

    private static final int CLEANUP_EVERY = 1024;

    private final Clock clock;
    private final int maxTrackedKeys;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicInteger callsSinceCleanup = new AtomicInteger();

    public SlidingWindowRateLimiter(Clock clock, int maxTrackedKeys) {
        this.clock = clock;
        this.maxTrackedKeys = maxTrackedKeys;
    }

    /**
     * Ghi nhận một request cho {@code key} nếu chưa vượt {@code limit} trong {@code window}.
     *
     * @return kết quả; khi bị từ chối kèm số giây nên chờ.
     */
    public Decision tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long windowMillis = window.toMillis();
        maybeCleanup(now);

        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= maxTrackedKeys) {
                cleanup(now);
                if (buckets.size() >= maxTrackedKeys) {
                    // Quá tải bộ nhớ theo dõi: không chặn khóa mới (khóa tài khoản vẫn bảo vệ từng tài khoản).
                    return Decision.ALLOWED;
                }
            }
            bucket = buckets.computeIfAbsent(key, k -> new Bucket(windowMillis));
        }
        synchronized (bucket) {
            bucket.windowMillis = windowMillis;
            bucket.evictBefore(now - windowMillis);
            if (bucket.hits.size() >= limit) {
                long oldest = bucket.hits.peekFirst();
                long retryAfterMillis = Math.max(oldest + windowMillis - now, 1);
                return Decision.rejected((retryAfterMillis + 999) / 1000);
            }
            bucket.hits.addLast(now);
            return Decision.ALLOWED;
        }
    }

    /** Số khóa đang được theo dõi (phục vụ test / giám sát). */
    public int trackedKeys() {
        return buckets.size();
    }

    private void maybeCleanup(long now) {
        if (callsSinceCleanup.incrementAndGet() >= CLEANUP_EVERY) {
            callsSinceCleanup.set(0);
            cleanup(now);
        }
    }

    private void cleanup(long now) {
        buckets.entrySet().removeIf(entry -> {
            Bucket bucket = entry.getValue();
            synchronized (bucket) {
                bucket.evictBefore(now - bucket.windowMillis);
                return bucket.hits.isEmpty();
            }
        });
    }

    private static final class Bucket {
        private final Deque<Long> hits = new ArrayDeque<>();
        private long windowMillis;

        private Bucket(long windowMillis) {
            this.windowMillis = windowMillis;
        }

        private void evictBefore(long threshold) {
            while (!hits.isEmpty() && hits.peekFirst() <= threshold) {
                hits.pollFirst();
            }
        }
    }

    /**
     * @param allowed           {@code true} nếu request được chấp nhận.
     * @param retryAfterSeconds số giây nên chờ khi bị từ chối (0 nếu được chấp nhận).
     */
    public record Decision(boolean allowed, long retryAfterSeconds) {

        public static final Decision ALLOWED = new Decision(true, 0);

        public static Decision rejected(long retryAfterSeconds) {
            return new Decision(false, Math.max(retryAfterSeconds, 1));
        }
    }
}
