package com.education.base.exception;

import lombok.Getter;

/** GĐ4: vượt giới hạn tần suất -> HTTP 429 {@code RATE_LIMITED} + header {@code Retry-After} (giây, >= 1). */
@Getter
public class RateLimitedException extends RuntimeException {

    private final String errorCode;
    private final long retryAfterSeconds;

    public RateLimitedException(String errorCode, String message, long retryAfterSeconds) {
        super(message);
        this.errorCode = errorCode;
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
}
