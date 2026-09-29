package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.dto.request.LoginRequest;
import com.education.base.dto.request.RefreshTokenRequest;
import com.education.base.dto.response.AuthTokenResponse;
import com.education.base.dto.response.AuthUserResponse;
import com.education.base.security.AllowPendingPasswordChange;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đăng nhập / làm mới token / đăng xuất / đổi mật khẩu (JWT stateless).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
@AllowPendingPasswordChange
@Tag(name = "Xác thực", description = "Đăng nhập JWT (access + refresh token), đổi mật khẩu")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Đăng nhập", description = "Trả access token, refresh token và thông tin người dùng.")
    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("Đăng nhập thành công.", authService.login(request));
    }

    @Operation(summary = "Làm mới access token bằng refresh token")
    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @Operation(summary = "Đăng xuất", description = "Thu hồi mọi access/refresh token đã cấp cho tài khoản.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout(SecurityUtils.requireCurrentUser().getId());
        return ApiResponse.success("Đã đăng xuất.", null);
    }

    @Operation(summary = "Thông tin người dùng hiện tại (vai trò, quyền)")
    @GetMapping("/me")
    public ApiResponse<AuthUserResponse> me() {
        AuthUserPrincipal principal = SecurityUtils.requireCurrentUser();
        return ApiResponse.success(authService.me(principal));
    }

    @Operation(summary = "Đổi mật khẩu", description = "Thu hồi token cũ và trả cặp token mới.")
    @PostMapping("/change-password")
    public ApiResponse<AuthTokenResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = SecurityUtils.requireCurrentUser().getId();
        return ApiResponse.success("Đổi mật khẩu thành công.", authService.changePassword(userId, request));
    }
}
