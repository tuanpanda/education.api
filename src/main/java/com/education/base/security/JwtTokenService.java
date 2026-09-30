package com.education.base.security;

import com.education.base.config.JwtProperties;
import com.education.base.config.JwtSecretPolicy;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Phát hành và xác thực JWT (HMAC-SHA) cho access token và refresh token.
 * <p>
 * Khóa ký được tạo MỘT lần trong constructor (secret yếu làm ứng dụng không khởi động được).
 */
@Component
public class JwtTokenService {

    static final String CLAIM_TOKEN_TYPE = "token_type";
    static final String CLAIM_TOKEN_VERSION = "ver";
    static final String CLAIM_USERNAME = "username";
    /** Mã phiên đăng nhập (family của refresh token). */
    static final String CLAIM_SESSION_ID = "sid";

    private final JwtProperties properties;

    private final Clock clock;

    private final SecretKey signingKey;

    public JwtTokenService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        JwtSecretPolicy.requireMinimumLength(properties.getSecret());
        this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Phát hành access token không gắn phiên (tương thích ngược).
     */
    public String generateAccessToken(Long userId, String username, Integer tokenVersion) {
        return generateAccessToken(userId, username, tokenVersion, null);
    }

    /**
     * Phát hành access token gắn với phiên đăng nhập {@code sessionId} (claim {@code sid}).
     */
    public String generateAccessToken(Long userId, String username, Integer tokenVersion, String sessionId) {
        return generate(userId, username, tokenVersion, TokenType.ACCESS, UUID.randomUUID().toString(), sessionId,
                properties.getAccessTokenTtl().toMillis());
    }

    /**
     * Phát hành refresh token với {@code jti} ngẫu nhiên, không gắn phiên (tương thích ngược).
     */
    public String generateRefreshToken(Long userId, String username, Integer tokenVersion) {
        return generateRefreshToken(userId, username, tokenVersion, UUID.randomUUID().toString(), null);
    }

    /**
     * Phát hành refresh token với {@code jti} đã lưu trong {@code SYS_REFRESH_TOKENS} và phiên {@code sessionId}.
     */
    public String generateRefreshToken(Long userId, String username, Integer tokenVersion,
                                       String jti, String sessionId) {
        return generate(userId, username, tokenVersion, TokenType.REFRESH, jti, sessionId,
                properties.getRefreshTokenTtl().toMillis());
    }

    /** Thời hạn access token tính bằng giây (trả cho client qua {@code expiresIn}). */
    public long getAccessTokenTtlSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }

    /** Thời hạn refresh token tính bằng giây. */
    public long getRefreshTokenTtlSeconds() {
        return properties.getRefreshTokenTtl().toSeconds();
    }

    /**
     * Xác thực chữ ký, issuer, thời hạn và loại token.
     *
     * @throws InvalidTokenException khi token không hợp lệ hoặc sai loại.
     */
    public JwtClaims parse(String token, TokenType expectedType) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException(InvalidTokenException.TOKEN_INVALID, "Token không được để trống.");
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new InvalidTokenException(InvalidTokenException.TOKEN_EXPIRED,
                    "Phiên đăng nhập đã hết hạn.", ex);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException(InvalidTokenException.TOKEN_INVALID, "Token không hợp lệ.", ex);
        }

        TokenType type = parseType(claims.get(CLAIM_TOKEN_TYPE, String.class));
        if (type != expectedType) {
            throw new InvalidTokenException(InvalidTokenException.TOKEN_INVALID, "Loại token không hợp lệ.");
        }
        Long userId;
        try {
            userId = Long.valueOf(claims.getSubject());
        } catch (NumberFormatException ex) {
            throw new InvalidTokenException(InvalidTokenException.TOKEN_INVALID, "Token không hợp lệ.", ex);
        }
        Number version = claims.get(CLAIM_TOKEN_VERSION, Number.class);
        return new JwtClaims(
                userId,
                claims.get(CLAIM_USERNAME, String.class),
                version == null ? 0 : version.intValue(),
                type,
                claims.getExpiration() == null ? null : claims.getExpiration().toInstant(),
                claims.getId(),
                claims.get(CLAIM_SESSION_ID, String.class));
    }

    private String generate(Long userId, String username, Integer tokenVersion, TokenType type,
                            String jti, String sessionId, long ttlMillis) {
        Instant now = clock.instant();
        JwtBuilder builder = Jwts.builder()
                .id(jti)
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMillis)))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_TOKEN_TYPE, type.name())
                .claim(CLAIM_TOKEN_VERSION, tokenVersion == null ? 0 : tokenVersion);
        if (sessionId != null) {
            builder.claim(CLAIM_SESSION_ID, sessionId);
        }
        return builder.signWith(signingKey).compact();
    }

    private static TokenType parseType(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return TokenType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
