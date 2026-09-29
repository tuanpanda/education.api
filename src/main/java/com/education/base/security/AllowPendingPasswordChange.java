package com.education.base.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Cho phép gọi endpoint ngay cả khi người dùng đang bị buộc đổi mật khẩu
 * ({@code SYS_USERS.MUST_CHANGE_PASSWORD = 1}). Các endpoint khác trả {@code 403 PASSWORD_CHANGE_REQUIRED}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AllowPendingPasswordChange {
}
