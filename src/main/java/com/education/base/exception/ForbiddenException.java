package com.education.base.exception;

import lombok.Getter;

/**
 * Người dùng đã đăng nhập nhưng không đủ quyền. {@link GlobalExceptionHandler} trả HTTP 403.
 */
@Getter
public class ForbiddenException extends RuntimeException {

    private final String errorCode;

    public ForbiddenException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
