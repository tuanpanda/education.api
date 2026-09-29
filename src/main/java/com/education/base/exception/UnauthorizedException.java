package com.education.base.exception;

import lombok.Getter;

/**
 * Lỗi xác thực (sai thông tin đăng nhập, token không hợp lệ, tài khoản bị khóa...).
 * {@link GlobalExceptionHandler} trả HTTP 401.
 */
@Getter
public class UnauthorizedException extends RuntimeException {

    private final String errorCode;

    public UnauthorizedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
