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
 */
public record JwtClaims(Long userId, String username, Integer tokenVersion, TokenType type, Instant expiresAt) {
}
