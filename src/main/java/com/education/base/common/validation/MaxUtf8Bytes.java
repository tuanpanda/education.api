package com.education.base.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Chuỗi không được dài quá {@link #value()} byte khi mã hóa UTF-8 ({@code null} hợp lệ).
 * <p>
 * Dùng cho mật khẩu: BCrypt chỉ dùng 72 byte đầu, ký tự tiếng Việt có dấu chiếm 2-3 byte.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "Giá trị vượt quá độ dài cho phép";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
