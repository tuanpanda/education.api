package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.dto.request.LoginRequest;
import com.education.base.dto.request.LogoutRequest;
import com.education.base.dto.request.RefreshTokenRequest;
import com.education.base.dto.response.AuthTokenResponse;
import com.education.base.dto.response.AuthUserResponse;
import com.education.base.security.AllowPendingPasswordChange;
import com.education.base.security.AuthenticatedOnly;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.JwtAuthenticationFilter;
import com.education.base.security.JwtClaims;
import com.education.base.security.PublicEndpoint;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đăng nhập / làm mới token / đăng xuất / đổi mật khẩu (JWT, refresh token xoay vòng).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
@AllowPendingPasswordChange
@Tag(name = "Xác thực", description = "Đăng nhập JWT (access + refresh token), đổi mật khẩu")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Đăng nhập", description = "Trả access token, refresh token và thông tin người dùng. "
            + "Sai mật khẩu nhiều lần liên tiếp sẽ khóa tạm thời tài khoản (ACCOUNT_TEMPORARILY_LOCKED); "
            + "gửi quá nhiều request trả HTTP 429.")
    @PostMapping("/login")
    @PublicEndpoint
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("Đăng nhập thành công.", authService.login(request));
    }

    @Operation(summary = "Làm mới access token bằng refresh token",
            description = "Refresh token được xoay vòng: token cũ bị thu hồi, luôn lưu refresh token mới trả về.")
    @PostMapping("/refresh")
    @PublicEndpoint
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @Operation(summary = "Đăng xuất", description = "Thu hồi phiên đăng nhập hiện tại (các thiết bị khác không bị "
            + "ảnh hưởng). Body không bắt buộc: {\"refreshToken\": \"...\"}.")
    @PostMapping("/logout")
    @AuthenticatedOnly
    public ApiResponse<Void> logout(@Valid @RequestBody(required = false) LogoutRequest body,
                                    HttpServletRequest request) {
        Long userId = SecurityUtils.requireCurrentUser().getId();
        Object claims = request.getAttribute(JwtAuthenticationFilter.CLAIMS_ATTRIBUTE);
        String sessionId = claims instanceof JwtClaims jwt ? jwt.sessionId() : null;
        authService.logout(userId, sessionId, body == null ? null : body.getRefreshToken());
        return ApiResponse.success("Đã đăng xuất.", null);
    }

    @Operation(summary = "Thông tin người dùng hiện tại (vai trò, quyền)")
    @GetMapping("/me")
    @AuthenticatedOnly
    public ApiResponse<AuthUserResponse> me() {
        AuthUserPrincipal principal = SecurityUtils.requireCurrentUser();
        return ApiResponse.success(authService.me(principal));
    }

    @Operation(summary = "Đổi mật khẩu", description = "Thu hồi mọi phiên cũ và trả cặp token mới.")
    @PostMapping("/change-password")
    @AuthenticatedOnly
    public ApiResponse<AuthTokenResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = SecurityUtils.requireCurrentUser().getId();
        return ApiResponse.success("Đổi mật khẩu thành công.", authService.changePassword(userId, request));
    }
}
