package com.education.base.service;

import com.education.base.dto.response.AuthUserResponse;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;

/**
 * Cặp token vừa phát hành (đăng nhập / làm mới / đổi mật khẩu). Chỉ dùng nội bộ: controller ghi token vào
 * cookie HttpOnly ({@code AuthCookieService}) và chỉ trả {@link #getUser()} trong body.
 */
@Value
@Builder
public class AuthTokens {

    @ToString.Exclude
    String accessToken;

    @ToString.Exclude
    String refreshToken;

    /** Thời hạn access token (giây) - cũng là {@code Max-Age} của cookie. */
    long accessTokenTtlSeconds;

    /** Thời hạn refresh token (giây) - cũng là {@code Max-Age} của cookie. */
    long refreshTokenTtlSeconds;

    AuthUserResponse user;
}
