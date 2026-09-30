package com.education.base.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Endpoint mở cho MỌI người dùng đã đăng nhập, không cần mã quyền cụ thể (ví dụ: thông tin bản thân,
 * đăng xuất, cây menu sidebar).
 * <p>
 * {@link PermissionInterceptor} từ chối mặc định mọi handler {@code /api/**} không khai báo
 * {@link RequirePermission}, {@link AuthenticatedOnly} hoặc {@link PublicEndpoint}. Đặt trên method (ưu tiên)
 * hoặc trên class Controller.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AuthenticatedOnly {
}
