package com.education.base.security;

import lombok.Getter;

/**
 * JWT không hợp lệ (sai chữ ký, sai loại, hết hạn...).
 */
@Getter
public class InvalidTokenException extends RuntimeException {

    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String TOKEN_INVALID = "TOKEN_INVALID";

    private final String errorCode;

    public InvalidTokenException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public InvalidTokenException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
