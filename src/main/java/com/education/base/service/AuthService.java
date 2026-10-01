package com.education.base.service;

import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.dto.request.LoginRequest;
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
    AuthTokens login(LoginRequest request);

    /**
     * Xoay vòng refresh token (đọc từ cookie): token cũ bị thu hồi, trả cặp token mới cùng phiên. Dùng lại
     * refresh token đã bị xoay vòng sẽ thu hồi toàn bộ phiên.
     */
    AuthTokens refresh(String refreshToken);

    /**
     * Đăng xuất phiên hiện tại: thu hồi phiên của {@code refreshToken} (cookie; chữ ký hợp lệ và thuộc
     * {@code userId} nếu đã đăng nhập), nếu không thì phiên {@code sessionId} (claim {@code sid} của access
     * token). {@code userId} có thể {@code null} khi access token đã hết hạn: khi đó chỉ dựa vào refresh token.
     * Các phiên khác (thiết bị khác) không bị ảnh hưởng.
     */
    void logout(Long userId, String sessionId, String refreshToken);

    AuthUserResponse me(AuthUserPrincipal principal);

    /** Đổi mật khẩu, thu hồi mọi token/phiên cũ và trả cặp token mới (phiên mới). */
    AuthTokens changePassword(Long userId, ChangePasswordRequest request);
}
