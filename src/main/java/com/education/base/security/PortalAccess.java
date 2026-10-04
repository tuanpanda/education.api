package com.education.base.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Endpoint của cổng học sinh ({@code /api/v1/portal/**}): chỉ tài khoản có {@link UserType} trong {@link #value()}
 * mới được gọi; nhân viên (kể cả {@code ROLE_ADMIN}) nhận 403.
 * <p>
 * Endpoint cổng KHÔNG BAO GIỜ nhận {@code studentId} từ request: học sinh luôn được xác định từ người dùng đăng
 * nhập qua bảng liên kết {@code EDU_USER_STUDENT_LINKS} ({@code PortalStudentContext}).
 * {@code PortalAccessRulesTest} bắt buộc mọi handler dưới {@code /api/v1/portal/**} có annotation này và ngược lại.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PortalAccess {

    /** Loại tài khoản được phép. Hợp đồng giai đoạn 0: chỉ {@link UserType#STUDENT}. */
    UserType[] value() default {UserType.STUDENT};
}
