package com.education.base.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Khai báo quyền cần có để gọi một endpoint. Người dùng chỉ cần có MỘT trong các mã quyền liệt kê
 * (dạng {@code MENU_CODE:FUNCTION_CODE}, xem {@link Permissions}).
 * <p>
 * Đặt trên method (ưu tiên) hoặc trên class Controller (áp dụng cho mọi method chưa khai báo).
 * Endpoint không có annotation chỉ yêu cầu đăng nhập. Người dùng có {@link Permissions#ADMIN_ROLE}
 * luôn được phép. Kiểm tra bởi {@link PermissionInterceptor}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequirePermission {

    /** Danh sách mã quyền, thỏa một trong số đó là đủ. */
    String[] value();
}
