package com.education.base.security;

import java.time.Instant;

/**
 * Thông tin đã xác thực chữ ký từ một JWT.
 *
 * @param userId       ID người dùng (claim {@code sub}).
 * @param username     tên đăng nhập.
 * @param tokenVersion phiên bản token của người dùng tại thời điểm phát hành (claim {@code ver}).
 * @param type         loại token.
 * @param expiresAt    thời điểm hết hạn.
 * @param jti          mã định danh token (claim {@code jti}); với refresh token khớp {@code SYS_REFRESH_TOKENS.JTI}.
 * @param sessionId    mã phiên đăng nhập (claim {@code sid}) = {@code SYS_REFRESH_TOKENS.FAMILY_ID};
 *                     {@code null} với token phát hành trước khi có xoay vòng refresh token.
 */
public record JwtClaims(Long userId, String username, Integer tokenVersion, TokenType type, Instant expiresAt,
                        String jti, String sessionId) {

    /** Tương thích ngược: token không có {@code jti} / {@code sid}. */
    public JwtClaims(Long userId, String username, Integer tokenVersion, TokenType type, Instant expiresAt) {
        this(userId, username, tokenVersion, type, expiresAt, null, null);
    }
}
