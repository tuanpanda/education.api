package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Quản trị viên đặt lại mật khẩu cho người dùng (người dùng bị buộc đổi ở lần đăng nhập kế tiếp).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Pattern(regexp = DomainConstants.PASSWORD_PATTERN, message = DomainConstants.PASSWORD_POLICY_MESSAGE)
    @ToString.Exclude
    private String newPassword;
}
