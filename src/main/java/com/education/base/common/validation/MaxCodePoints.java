package com.education.base.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Chuỗi không được dài quá {@link #value()} <b>ký tự</b> (đếm code point, giống {@code VARCHAR2(n CHAR)} của Oracle
 * AL32UTF8; {@code null} hợp lệ). Khác {@code @Size} (đếm UTF-16) và khác giới hạn byte: 1000 ký tự tiếng Việt có
 * dấu là hợp lệ với cột 1000 CHAR (contract fee-range §16, BUG-FEE-RANGE-04).
 */
@Documented
@Constraint(validatedBy = MaxCodePointsValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxCodePoints {

    int value();

    String message() default "Giá trị vượt quá độ dài cho phép";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
