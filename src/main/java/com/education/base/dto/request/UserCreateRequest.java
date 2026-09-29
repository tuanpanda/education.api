package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Tạo tài khoản người dùng mới.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCreateRequest {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Pattern(regexp = DomainConstants.USERNAME_PATTERN,
            message = "Tên đăng nhập 3-50 ký tự, chỉ gồm chữ, số, dấu chấm, gạch dưới, gạch ngang")
    private String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Pattern(regexp = DomainConstants.PASSWORD_PATTERN, message = DomainConstants.PASSWORD_POLICY_MESSAGE)
    @ToString.Exclude
    private String password;

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự")
    private String fullName;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;

    @Pattern(regexp = "^$|^[0-9+()\\s.-]{6,20}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @Pattern(regexp = DomainConstants.USER_STATUS_PATTERN,
            message = "Trạng thái chỉ nhận: ACTIVE, INACTIVE, LOCKED")
    private String status;

    /** Mặc định {@code true}: buộc đổi mật khẩu ở lần đăng nhập đầu tiên. */
    private Boolean mustChangePassword;

    @Builder.Default
    private List<@NotNull(message = "roleId không được để trống") Long> roleIds = new ArrayList<>();
}
