package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cặp token JWT trả về sau khi đăng nhập / làm mới / đổi mật khẩu.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthTokenResponse {

    public static final String BEARER = "Bearer";

    private String accessToken;

    private String refreshToken;

    @Builder.Default
    private String tokenType = BEARER;

    /** Thời hạn access token (giây). */
    private long expiresIn;

    /** Thời hạn refresh token (giây). */
    private long refreshExpiresIn;

    private AuthUserResponse user;
}
