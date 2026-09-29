package com.education.base.support;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Giả lập người dùng đã đăng nhập bằng JWT ({@code AuthUserPrincipal}) trong test.
 * Mặc định là quản trị viên {@code ROLE_ADMIN}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@WithSecurityContext(factory = WithAuthUserSecurityContextFactory.class)
public @interface WithAuthUser {

    long id() default 1L;

    String username() default "admin";

    String[] roles() default {"ROLE_ADMIN"};

    String[] permissions() default {};

    boolean mustChangePassword() default false;
}
