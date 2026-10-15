package com.education.base.service.quiz.impl;

import com.education.base.common.quiz.QuizConstants;
import com.education.base.common.quiz.QuizErrorCodes;
import com.education.base.exception.RateLimitedException;
import com.education.base.security.RateLimiter;
import com.education.base.security.SlidingWindowRateLimiter;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * GĐ4 - BR-26: cửa sổ trượt 60 giây, 60 lần lưu đáp án / attempt. Bộ nhớ, 1 instance (restart thì reset - đã chấp nhận).
 * Vượt -> 429 {@code RATE_LIMITED} + {@code Retry-After}.
 */
@Component
public class QuizSaveRateLimiter {

    static final int MAX_TRACKED_KEYS = 20_000;

    private final RateLimiter limiter;

    public QuizSaveRateLimiter(Clock clock) {
        this.limiter = new SlidingWindowRateLimiter(clock, MAX_TRACKED_KEYS);
    }

    public void acquire(Long attemptId) {
        RateLimiter.Decision d = limiter.tryAcquire("quiz-save:" + attemptId, QuizConstants.SAVE_RATE_LIMIT,
                Duration.ofSeconds(QuizConstants.SAVE_RATE_WINDOW_SEC));
        if (!d.allowed()) {
            throw new RateLimitedException(QuizErrorCodes.RATE_LIMITED, "Thao tác quá nhanh, thử lại sau ít giây.",
                    d.retryAfterSeconds());
        }
    }
}
