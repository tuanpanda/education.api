package com.education.base.common.validation;

import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.dto.request.ResetPasswordRequest;
import com.education.base.dto.request.UserCreateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mật khẩu mới tối đa 72 byte UTF-8 (giới hạn của BCrypt).
 */
class MaxUtf8BytesValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** 70 byte ASCII + 1 ký tự có dấu (2 byte) = 72 byte. */
    private static final String AT_LIMIT = "a1" + "x".repeat(68) + "ă";
    /** 71 byte ASCII + 1 ký tự có dấu (2 byte) = 73 byte (chỉ 72 ký tự). */
    private static final String OVER_LIMIT = "a1" + "x".repeat(69) + "ă";

    @Test
    void utf8Length_countsBytesNotChars() {
        assertThat(MaxUtf8BytesValidator.utf8Length("ăâ")).isEqualTo(4);
        assertThat(MaxUtf8BytesValidator.utf8Length(AT_LIMIT)).isEqualTo(72);
        assertThat(MaxUtf8BytesValidator.utf8Length(OVER_LIMIT)).isEqualTo(73);
        assertThat(OVER_LIMIT.length()).isLessThanOrEqualTo(72);
    }

    @Test
    void changePassword_newPasswordOver72Bytes_isRejectedWithVietnameseMessage() {
        assertThat(validator.validate(new ChangePasswordRequest("Old@1234", AT_LIMIT))).isEmpty();

        Set<ConstraintViolation<ChangePasswordRequest>> violations =
                validator.validate(new ChangePasswordRequest("Old@1234", OVER_LIMIT));
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactly("Mật khẩu không được vượt quá 72 byte (ký tự có dấu tiếng Việt chiếm 2-3 byte)");
    }

    @Test
    void resetAndCreate_areCappedToo() {
        assertThat(validator.validate(new ResetPasswordRequest(OVER_LIMIT))).isNotEmpty();
        UserCreateRequest create = UserCreateRequest.builder()
                .username("teacher9").password(OVER_LIMIT).fullName("Giáo viên 9").build();
        assertThat(validator.validate(create)).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("password");
    }
}
