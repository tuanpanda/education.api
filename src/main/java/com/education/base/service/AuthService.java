package com.education.base.service;

import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.dto.request.LoginRequest;
import com.education.base.dto.request.RefreshTokenRequest;
import com.education.base.dto.response.AuthTokenResponse;
import com.education.base.dto.response.AuthUserResponse;
import com.education.base.security.AuthUserPrincipal;

/**
 * Xác thực người dùng bằng JWT (access token stateless + refresh token xoay vòng lưu trong DB).
 */
public interface AuthService {

    /**
     * Đăng nhập, tạo phiên mới. Sai mật khẩu liên tiếp nhiều lần sẽ khóa tạm thời tài khoản
     * ({@code ACCOUNT_TEMPORARILY_LOCKED}).
     */
    AuthTokenResponse login(LoginRequest request);

    /**
     * Xoay vòng refresh token: token cũ bị thu hồi, trả cặp token mới cùng phiên. Dùng lại refresh token đã
     * bị xoay vòng sẽ thu hồi toàn bộ phiên.
     */
    AuthTokenResponse refresh(RefreshTokenRequest request);

    /**
     * Đăng xuất phiên hiện tại: thu hồi phiên của {@code refreshToken} (nếu gửi kèm và thuộc người dùng),
     * nếu không thì phiên {@code sessionId} (claim {@code sid} của access token). Các phiên khác
     * (thiết bị khác) không bị ảnh hưởng.
     */
    void logout(Long userId, String sessionId, String refreshToken);

    AuthUserResponse me(AuthUserPrincipal principal);

    /** Đổi mật khẩu, thu hồi mọi token/phiên cũ và trả cặp token mới (phiên mới). */
    AuthTokenResponse changePassword(Long userId, ChangePasswordRequest request);
}
