package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.dto.request.LoginRequest;
import com.education.base.dto.response.AuthUserResponse;
import com.education.base.exception.UnauthorizedException;
import com.education.base.security.AllowPendingPasswordChange;
import com.education.base.security.AuthCookieService;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.AuthenticatedOnly;
import com.education.base.security.JwtAuthenticationFilter;
import com.education.base.security.JwtClaims;
import com.education.base.security.PublicEndpoint;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AuthService;
import com.education.base.service.AuthTokens;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đăng nhập / làm mới token / đăng xuất / đổi mật khẩu.
 * <p>
 * Access token và refresh token chỉ nằm trong cookie HttpOnly ({@link AuthCookieService}); body phản hồi
 * chỉ có thông tin người dùng ({@link AuthUserResponse}, gồm {@code mustChangePassword}).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
@AllowPendingPasswordChange
@Tag(name = "Xác thực", description = "Đăng nhập JWT (cookie HttpOnly access + refresh token), đổi mật khẩu")
public class AuthController {

    static final String REFRESH_TOKEN_INVALID = "REFRESH_TOKEN_INVALID";

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    @Operation(summary = "Đăng nhập", description = "Đặt cookie HttpOnly access token + refresh token và trả thông "
            + "tin người dùng. Sai mật khẩu nhiều lần liên tiếp sẽ khóa tạm thời tài khoản "
            + "(ACCOUNT_TEMPORARILY_LOCKED); gửi quá nhiều request trả HTTP 429.")
    @PostMapping("/login")
    @PublicEndpoint
    public ApiResponse<AuthUserResponse> login(@Valid @RequestBody LoginRequest body,
                                               HttpServletRequest request, HttpServletResponse response) {
        AuthTokens tokens = authService.login(body);
        authCookieService.writeSession(request, response, tokens);
        return ApiResponse.success("Đăng nhập thành công.", tokens.getUser());
    }

    @Operation(summary = "Làm mới access token bằng refresh token (cookie)",
            description = "Refresh token được xoay vòng: token cũ bị thu hồi, cookie mới được đặt. Refresh token "
                    + "không hợp lệ -> 401 và cả hai cookie bị xóa.")
    @PostMapping("/refresh")
    @PublicEndpoint
    public ApiResponse<AuthUserResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = AuthCookieService.readRefreshToken(request).orElse(null);
        AuthTokens tokens;
        try {
            if (refreshToken == null) {
                throw new UnauthorizedException(REFRESH_TOKEN_INVALID,
                        "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.");
            }
            tokens = authService.refresh(refreshToken);
        } catch (UnauthorizedException ex) {
            // Phiên đã chết: xóa cookie để trình duyệt thôi gửi token hỏng.
            authCookieService.clearSession(response);
            throw ex;
        }
        authCookieService.writeSession(request, response, tokens);
        return ApiResponse.success(tokens.getUser());
    }

    @Operation(summary = "Đăng xuất", description = "Thu hồi phiên đăng nhập hiện tại (theo refresh token trong "
            + "cookie, hoặc claim sid của access token) và xóa cả hai cookie. Không cần access token còn hạn; "
            + "các thiết bị khác không bị ảnh hưởng.")
    @PostMapping("/logout")
    @PublicEndpoint
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Long userId = SecurityUtils.currentUser().map(AuthUserPrincipal::getId).orElse(null);
        Object claims = request.getAttribute(JwtAuthenticationFilter.CLAIMS_ATTRIBUTE);
        String sessionId = claims instanceof JwtClaims jwt ? jwt.sessionId() : null;
        try {
            authService.logout(userId, sessionId, AuthCookieService.readRefreshToken(request).orElse(null));
        } finally {
            authCookieService.clearSession(response);
        }
        return ApiResponse.success("Đã đăng xuất.", null);
    }

    @Operation(summary = "Thông tin người dùng hiện tại (vai trò, quyền, cờ buộc đổi mật khẩu)",
            description = "Frontend gọi khi mở / tải lại trang để khôi phục phiên từ cookie.")
    @GetMapping("/me")
    @AuthenticatedOnly
    public ApiResponse<AuthUserResponse> me() {
        AuthUserPrincipal principal = SecurityUtils.requireCurrentUser();
        return ApiResponse.success(authService.me(principal));
    }

    @Operation(summary = "Đổi mật khẩu", description = "Thu hồi mọi phiên cũ, đặt cookie của phiên mới và trả "
            + "thông tin người dùng.")
    @PostMapping("/change-password")
    @AuthenticatedOnly
    public ApiResponse<AuthUserResponse> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                                        HttpServletRequest request, HttpServletResponse response) {
        Long userId = SecurityUtils.requireCurrentUser().getId();
        AuthTokens tokens = authService.changePassword(userId, body);
        authCookieService.writeSession(request, response, tokens);
        return ApiResponse.success("Đổi mật khẩu thành công.", tokens.getUser());
    }
}
