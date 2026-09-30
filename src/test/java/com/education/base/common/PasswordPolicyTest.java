package com.education.base.common;

import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.dto.request.ResetPasswordRequest;
import com.education.base.dto.request.UserCreateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thông báo chính sách mật khẩu (quản trị tạo người dùng / đặt lại mật khẩu, đổi mật khẩu) phải khớp giới hạn độ dài
 * của {@link DomainConstants#PASSWORD_PATTERN}: 8-72 ký tự (BCrypt chỉ dùng 72 byte đầu).
 */
class PasswordPolicyTest {

    private static final String MIN_OK = "a1" + "x".repeat(6);
    private static final String MAX_OK = "a1" + "x".repeat(70);
    private static final String TOO_SHORT = "a1" + "x".repeat(5);
    private static final String TOO_LONG = "a1" + "x".repeat(71);

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void policyMessage_matchesPatternLengthBounds() {
        Matcher bounds = Pattern.compile("\\.\\{(\\d+),(\\d+)}").matcher(DomainConstants.PASSWORD_PATTERN);
        assertThat(bounds.find()).isTrue();
        assertThat(bounds.group(1)).isEqualTo("8");
        assertThat(bounds.group(2)).isEqualTo(String.valueOf(DomainConstants.PASSWORD_MAX_BYTES)).isEqualTo("72");
        assertThat(DomainConstants.PASSWORD_POLICY_MESSAGE)
                .contains("8 đến 72 ký tự")
                .doesNotContain("100");
    }

    @Test
    void adminResetPassword_boundsMatchPolicy() {
        assertThat(messages(new ResetPasswordRequest(MIN_OK))).isEmpty();
        assertThat(messages(new ResetPasswordRequest(MAX_OK))).isEmpty();
        assertThat(messages(new ResetPasswordRequest(TOO_SHORT))).containsExactly(DomainConstants.PASSWORD_POLICY_MESSAGE);
        assertThat(messages(new ResetPasswordRequest(TOO_LONG))).contains(DomainConstants.PASSWORD_POLICY_MESSAGE);
    }

    @Test
    void adminCreateUser_passwordBoundsMatchPolicy() {
        assertThat(passwordMessages(MIN_OK)).isEmpty();
        assertThat(passwordMessages(MAX_OK)).isEmpty();
        assertThat(passwordMessages(TOO_SHORT)).containsExactly(DomainConstants.PASSWORD_POLICY_MESSAGE);
        assertThat(passwordMessages(TOO_LONG)).contains(DomainConstants.PASSWORD_POLICY_MESSAGE);
    }

    @Test
    void changePassword_boundsMatchPolicy() {
        assertThat(messages(new ChangePasswordRequest("Old@1234", MAX_OK))).isEmpty();
        assertThat(messages(new ChangePasswordRequest("Old@1234", TOO_LONG)))
                .contains(DomainConstants.PASSWORD_POLICY_MESSAGE);
    }

    private <T> List<String> messages(T request) {
        return validator.validate(request).stream().map(ConstraintViolation::getMessage).toList();
    }

    private List<String> passwordMessages(String password) {
        UserCreateRequest request = new UserCreateRequest();
        request.setPassword(password);
        return validator.validateProperty(request, "password").stream().map(ConstraintViolation::getMessage).toList();
    }
}
