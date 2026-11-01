package com.education.base.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Đếm theo ký tự (code point) như Oracle CHAR trong AL32UTF8 (BUG-FEE-RANGE-04). */
class MaxCodePointsValidatorTest {

    @Test
    void length_countsCodePoints_notBytesOrUtf16Units() {
        assertThat(MaxCodePointsValidator.length(null)).isZero();
        assertThat(MaxCodePointsValidator.length("")).isZero();
        assertThat(MaxCodePointsValidator.length("Nhận xét")).isEqualTo(8);
        assertThat(MaxCodePointsValidator.length("ệ".repeat(1000))).isEqualTo(1000);
        // Ký tự ngoài BMP (cặp surrogate) = 1 ký tự.
        assertThat(MaxCodePointsValidator.length("\uD835\uDC00\uD83D\uDE00")).isEqualTo(2);
    }

    @Test
    void isValid_boundary() {
        MaxCodePointsValidator v = new MaxCodePointsValidator();
        v.initialize(annotation(1000));
        assertThat(v.isValid(null, null)).isTrue();
        assertThat(v.isValid("ệ".repeat(1000), null)).isTrue();
        assertThat(v.isValid("ệ".repeat(1001), null)).isFalse();
        assertThat(v.isValid("\uD835\uDC00".repeat(1000), null)).isTrue();
    }

    private static MaxCodePoints annotation(int max) {
        return new MaxCodePoints() {
            @Override
            public int value() {
                return max;
            }

            @Override
            public String message() {
                return "";
            }

            @Override
            public Class<?>[] groups() {
                return new Class<?>[0];
            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<? extends jakarta.validation.Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return MaxCodePoints.class;
            }
        };
    }
}
