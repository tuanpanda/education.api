package com.education.base.service;

import java.time.LocalDateTime;

/**
 * Lưu vết refresh token ({@code SYS_REFRESH_TOKENS}): tạo phiên, xoay vòng, phát hiện dùng lại, thu hồi.
 */
public interface RefreshTokenService {

    /** Tạo phiên đăng nhập mới (family mới) và ghi refresh token đầu tiên. */
    IssuedRefreshToken createSession(Long userId);

    /**
     * Xoay vòng refresh token {@code jti} của {@code userId}: token hợp lệ bị thu hồi và thay bằng token mới
     * cùng phiên. Dùng lại một token đã bị xoay vòng (ngoài khoảng ân hạn) thu hồi TOÀN BỘ phiên.
     * Không ném lỗi (để việc thu hồi được commit); kết quả từ chối trả qua {@link RotationResult}.
     */
    RotationResult rotate(Long userId, String jti);

    /** Thu hồi một phiên (đăng xuất) của {@code userId}. */
    void revokeSession(Long userId, String sessionId);

    /** Thu hồi mọi phiên của người dùng (đổi mật khẩu...). */
    void revokeAllSessions(Long userId);

    /** Phiên còn hiệu lực (còn refresh token chưa bị thu hồi). */
    boolean isSessionActive(String sessionId);

    /**
     * Refresh token vừa được ghi nhận.
     *
     * @param jti       claim {@code jti}.
     * @param sessionId family / claim {@code sid}.
     * @param expiresAt thời điểm hết hạn lưu trong DB.
     */
    record IssuedRefreshToken(String jti, String sessionId, LocalDateTime expiresAt) {
    }

    /** Lý do từ chối làm mới. */
    enum Rejection {
        /** Không tìm thấy token (token cũ trước khi có xoay vòng, token giả, sai người dùng). */
        UNKNOWN,
        /** Token hết hạn. */
        EXPIRED,
        /** Phiên đã bị thu hồi (đăng xuất, đổi mật khẩu...). */
        REVOKED,
        /** Token đã bị xoay vòng nhưng lại được dùng lại: nghi bị đánh cắp, cả phiên bị thu hồi. */
        REUSED
    }

    /**
     * @param issued    token mới khi thành công.
     * @param rejection lý do khi bị từ chối.
     */
    record RotationResult(IssuedRefreshToken issued, Rejection rejection) {

        public static RotationResult rotated(IssuedRefreshToken issued) {
            return new RotationResult(issued, null);
        }

        public static RotationResult rejected(Rejection rejection) {
            return new RotationResult(null, rejection);
        }

        public boolean isRotated() {
            return issued != null;
        }
    }
}
