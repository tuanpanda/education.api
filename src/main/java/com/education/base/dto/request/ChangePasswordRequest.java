package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import com.education.base.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Đổi mật khẩu của chính người dùng đang đăng nhập.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    @NotBlank(message = "Mật khẩu hiện tại không được để trống")
    @ToString.Exclude
    private String oldPassword;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Pattern(regexp = DomainConstants.PASSWORD_PATTERN, message = DomainConstants.PASSWORD_POLICY_MESSAGE)
    @MaxUtf8Bytes(value = DomainConstants.PASSWORD_MAX_BYTES, message = DomainConstants.PASSWORD_MAX_BYTES_MESSAGE)
    @ToString.Exclude
    private String newPassword;
}
