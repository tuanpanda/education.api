package com.education.base.security;

import com.education.base.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    private JwtProperties properties;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret("unit-test-secret-0123456789-abcdefghijklmnop");
        properties.setIssuer("education-api-test");
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setRefreshTokenTtl(Duration.ofDays(7));
    }

    private JwtTokenService serviceAt(Instant instant) {
        return new JwtTokenService(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void accessToken_roundTrip() {
        JwtTokenService service = serviceAt(NOW);
        String token = service.generateAccessToken(5L, "admin", 3);

        JwtClaims claims = service.parse(token, TokenType.ACCESS);

        assertThat(claims.userId()).isEqualTo(5L);
        assertThat(claims.username()).isEqualTo("admin");
        assertThat(claims.tokenVersion()).isEqualTo(3);
        assertThat(claims.type()).isEqualTo(TokenType.ACCESS);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(claims.jti()).isNotBlank();
        assertThat(claims.sessionId()).isNull();
        assertThat(service.getAccessTokenTtlSeconds()).isEqualTo(900);
        assertThat(service.getRefreshTokenTtlSeconds()).isEqualTo(7 * 24 * 3600);
    }

    @Test
    void sessionAndJti_areCarriedInClaims() {
        JwtTokenService service = serviceAt(NOW);

        JwtClaims access = service.parse(service.generateAccessToken(5L, "admin", 1, "sid-1"), TokenType.ACCESS);
        JwtClaims refresh = service.parse(
                service.generateRefreshToken(5L, "admin", 1, "jti-1", "sid-1"), TokenType.REFRESH);

        assertThat(access.sessionId()).isEqualTo("sid-1");
        assertThat(refresh.sessionId()).isEqualTo("sid-1");
        assertThat(refresh.jti()).isEqualTo("jti-1");
        assertThat(refresh.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void userType_isCarriedInClaims_andAbsentForLegacyOverloads() {
        JwtTokenService service = serviceAt(NOW);

        JwtClaims access = service.parse(
                service.generateAccessToken(9L, "hs00001", 0, "sid-9", UserType.STUDENT), TokenType.ACCESS);
        JwtClaims refresh = service.parse(
                service.generateRefreshToken(9L, "hs00001", 0, "jti-9", "sid-9", UserType.STUDENT), TokenType.REFRESH);
        JwtClaims legacy = service.parse(service.generateAccessToken(5L, "admin", 1, "sid-1"), TokenType.ACCESS);

        assertThat(access.userType()).isEqualTo("STUDENT");
        assertThat(refresh.userType()).isEqualTo("STUDENT");
        assertThat(access.sessionId()).isEqualTo("sid-9");
        assertThat(legacy.userType()).isNull();
    }

    @Test
    void refreshToken_cannotBeUsedAsAccessToken() {
        JwtTokenService service = serviceAt(NOW);
        String refresh = service.generateRefreshToken(5L, "admin", 0);

        assertThat(service.parse(refresh, TokenType.REFRESH).type()).isEqualTo(TokenType.REFRESH);
        assertThatThrownBy(() -> service.parse(refresh, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class)
                .extracting("errorCode").isEqualTo(InvalidTokenException.TOKEN_INVALID);
    }

    @Test
    void expiredToken_isRejectedWithExpiredCode() {
        String token = serviceAt(NOW).generateAccessToken(5L, "admin", 0);

        JwtTokenService later = serviceAt(NOW.plus(Duration.ofMinutes(16)));
        assertThatThrownBy(() -> later.parse(token, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class)
                .extracting("errorCode").isEqualTo(InvalidTokenException.TOKEN_EXPIRED);
    }

    @Test
    void tokenSignedWithOtherSecret_isRejected() {
        String token = serviceAt(NOW).generateAccessToken(5L, "admin", 0);

        JwtProperties other = new JwtProperties();
        other.setSecret("another-secret-0123456789-abcdefghijklmnopqrs");
        other.setIssuer("education-api-test");
        JwtTokenService otherService = new JwtTokenService(other, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> otherService.parse(token, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class)
                .extracting("errorCode").isEqualTo(InvalidTokenException.TOKEN_INVALID);
    }

    @Test
    void garbageOrBlankToken_isRejected() {
        JwtTokenService service = serviceAt(NOW);
        assertThatThrownBy(() -> service.parse("not-a-jwt", TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.parse(" ", TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void shortSecret_failsAtConstruction() {
        properties.setSecret("too-short-secret");

        assertThatThrownBy(() -> serviceAt(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
    }
}
