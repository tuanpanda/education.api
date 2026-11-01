package com.education.base.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Kiểm tra {@link MaxCodePoints}.
 */
public class MaxCodePointsValidator implements ConstraintValidator<MaxCodePoints, CharSequence> {

    private int max;

    @Override
    public void initialize(MaxCodePoints annotation) {
        this.max = annotation.value();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null || length(value) <= max;
    }

    /** Số ký tự (code point) của chuỗi. */
    public static int length(CharSequence value) {
        return value == null ? 0 : Character.codePointCount(value, 0, value.length());
    }
}
