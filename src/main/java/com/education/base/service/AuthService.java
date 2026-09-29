package com.education.base.service;

import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.dto.request.LoginRequest;
import com.education.base.dto.request.RefreshTokenRequest;
import com.education.base.dto.response.AuthTokenResponse;
import com.education.base.dto.response.AuthUserResponse;
import com.education.base.security.AuthUserPrincipal;

/**
 * Xác thực người dùng bằng JWT stateless (access + refresh token).
 */
public interface AuthService {

    AuthTokenResponse login(LoginRequest request);

    /** Cấp cặp token mới từ refresh token (refresh token cũ vẫn hợp lệ tới khi hết hạn hoặc bị thu hồi). */
    AuthTokenResponse refresh(RefreshTokenRequest request);

    /** Thu hồi mọi token đã phát hành cho người dùng (tăng {@code TOKEN_VERSION}). */
    void logout(Long userId);

    AuthUserResponse me(AuthUserPrincipal principal);

    /** Đổi mật khẩu, thu hồi token cũ và trả cặp token mới. */
    AuthTokenResponse changePassword(Long userId, ChangePasswordRequest request);
}
