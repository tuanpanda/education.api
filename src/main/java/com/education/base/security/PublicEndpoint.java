package com.education.base.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Endpoint công khai: {@link PermissionInterceptor} bỏ qua mọi kiểm tra (đăng nhập, quyền, buộc đổi mật khẩu).
 * <p>
 * Annotation này KHÔNG tự mở endpoint: đường dẫn vẫn phải được {@code permitAll} trong {@code SecurityConfig}
 * (đăng nhập, làm mới token, health check, trang gốc). Chỉ dùng cho các endpoint đó.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PublicEndpoint {
}
