package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Thông tin người dùng đang đăng nhập kèm vai trò và quyền phẳng.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthUserResponse {

    private Long id;

    private String username;

    private String fullName;

    private String email;

    /** Mã vai trò, ví dụ {@code ROLE_ADMIN}. */
    @Builder.Default
    private List<String> roles = new ArrayList<>();

    /** Mã quyền dạng {@code MENU_CODE:FUNCTION_CODE}. */
    @Builder.Default
    private List<String> permissions = new ArrayList<>();

    /** {@code true}: phải đổi mật khẩu trước khi dùng các chức năng khác. */
    private boolean mustChangePassword;
}
