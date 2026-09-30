package com.education.base.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowRateLimiterTest {

    /** Đồng hồ điều khiển được cho test. */
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-30T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void rejectsAfterLimitAndRecoversWhenWindowSlides() {
        MutableClock clock = new MutableClock();
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(clock, 1000);
        Duration window = Duration.ofMinutes(1);

        assertThat(limiter.tryAcquire("k", 3, window).allowed()).isTrue();
        clock.advance(Duration.ofSeconds(20));
        assertThat(limiter.tryAcquire("k", 3, window).allowed()).isTrue();
        assertThat(limiter.tryAcquire("k", 3, window).allowed()).isTrue();

        SlidingWindowRateLimiter.Decision rejected = limiter.tryAcquire("k", 3, window);
        assertThat(rejected.allowed()).isFalse();
        // Request đầu tiên rời cửa sổ sau 40 giây nữa.
        assertThat(rejected.retryAfterSeconds()).isEqualTo(40);

        // Khóa khác không bị ảnh hưởng.
        assertThat(limiter.tryAcquire("other", 3, window).allowed()).isTrue();

        clock.advance(Duration.ofSeconds(40));
        assertThat(limiter.tryAcquire("k", 3, window).allowed()).isTrue();
        assertThat(limiter.tryAcquire("k", 3, window).allowed()).isFalse();
    }

    @Test
    void rejectedRequestsDoNotExtendTheWindow() {
        MutableClock clock = new MutableClock();
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(clock, 1000);
        Duration window = Duration.ofSeconds(10);

        assertThat(limiter.tryAcquire("k", 1, window).allowed()).isTrue();
        for (int i = 0; i < 5; i++) {
            clock.advance(Duration.ofSeconds(1));
            assertThat(limiter.tryAcquire("k", 1, window).allowed()).isFalse();
        }
        clock.advance(Duration.ofSeconds(5));
        assertThat(limiter.tryAcquire("k", 1, window).allowed()).isTrue();
    }

    @Test
    void expiredKeysAreEvictedWhenCapacityIsReached() {
        MutableClock clock = new MutableClock();
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(clock, 100);
        Duration window = Duration.ofSeconds(10);
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire("k" + i, 5, window);
        }
        assertThat(limiter.trackedKeys()).isEqualTo(100);

        clock.advance(Duration.ofSeconds(11));
        assertThat(limiter.tryAcquire("new", 5, window).allowed()).isTrue();
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }
}
