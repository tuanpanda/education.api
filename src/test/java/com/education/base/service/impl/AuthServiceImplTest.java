package com.education.base.service.impl;

import com.education.base.config.AuthSecurityProperties;
import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.dto.request.LoginRequest;
import com.education.base.entity.UserEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.exception.UnauthorizedException;
import com.education.base.repository.UserRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.InvalidTokenException;
import com.education.base.security.JwtClaims;
import com.education.base.security.JwtTokenService;
import com.education.base.security.TokenType;
import com.education.base.service.AccessControlService;
import com.education.base.service.AuthTokens;
import com.education.base.service.RefreshTokenService;
import com.education.base.service.RefreshTokenService.IssuedRefreshToken;
import com.education.base.service.RefreshTokenService.Rejection;
import com.education.base.service.RefreshTokenService.RotationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private static final String PASSWORD = "Secret@123";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW_INSTANT = Instant.parse("2026-09-30T03:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZONE);
    private static final IssuedRefreshToken SESSION = new IssuedRefreshToken("jti-1", "sid-1", NOW.plusDays(7));

    @Mock
    private UserRepository userRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private RefreshTokenService refreshTokenService;

    private PasswordEncoder encoder;
    private AuthSecurityProperties properties;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        encoder = spy(ENCODER);
        properties = new AuthSecurityProperties();
        service = new AuthServiceImpl(userRepository, accessControlService, jwtTokenService, encoder,
                refreshTokenService, properties, Clock.fixed(NOW_INSTANT, ZONE));
        lenient().when(jwtTokenService.generateAccessToken(anyLong(), anyString(), anyInt(), anyString()))
                .thenReturn("access");
        lenient().when(jwtTokenService.generateRefreshToken(anyLong(), anyString(), anyInt(), anyString(), anyString()))
                .thenReturn("refresh");
        lenient().when(jwtTokenService.getAccessTokenTtlSeconds()).thenReturn(900L);
        lenient().when(refreshTokenService.createSession(anyLong())).thenReturn(SESSION);
        lenient().when(accessControlService.buildPrincipal(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity user = inv.getArgument(0);
            return AuthUserPrincipal.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .fullName(user.getFullName())
                    .roles(List.of("ROLE_TEACHER"))
                    .permissions(Set.of("MENU_STUDENT_LIST:VIEW"))
                    .mustChangePassword(Integer.valueOf(1).equals(user.getMustChangePassword()))
                    .tokenVersion(user.getTokenVersion())
                    .build();
        });
    }

    private static UserEntity user(String status) {
        return UserEntity.builder()
                .id(10L)
                .username("teacher1")
                .fullName("Giáo viên 1")
                .passwordHash(ENCODER.encode(PASSWORD))
                .status(status)
                .isDeleted(0)
                .mustChangePassword(1)
                .tokenVersion(2)
                .failedLoginCount(0)
                .build();
    }

    private static JwtClaims refreshClaims(int version, String jti) {
        return new JwtClaims(10L, "teacher1", version, TokenType.REFRESH, null, jti, "sid-1");
    }

    // ------------------------------------------------------------------ login

    @Test
    void login_success_returnsTokensAndCreatesSession() {
        UserEntity user = user("ACTIVE");
        when(userRepository.findByUsernameAndIsDeleted("teacher1", 0)).thenReturn(Optional.of(user));

        AuthTokens response = service.login(new LoginRequest("teacher1", PASSWORD));

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        assertThat(response.getAccessTokenTtlSeconds()).isEqualTo(900L);
        assertThat(response.getUser().getUsername()).isEqualTo("teacher1");
        assertThat(response.getUser().getRoles()).containsExactly("ROLE_TEACHER");
        assertThat(response.getUser().getPermissions()).containsExactly("MENU_STUDENT_LIST:VIEW");
        assertThat(response.getUser().isMustChangePassword()).isTrue();
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
        verify(refreshTokenService).createSession(10L);
        verify(jwtTokenService).generateAccessToken(10L, "teacher1", 2, "sid-1");
        verify(jwtTokenService).generateRefreshToken(10L, "teacher1", 2, "jti-1", "sid-1");
    }

    @Test
    void login_usernameIsCaseInsensitiveFallback() {
        when(userRepository.findByUsernameAndIsDeleted("Teacher1", 0)).thenReturn(Optional.empty());
        when(userRepository.findByUsernameAndIsDeleted("teacher1", 0)).thenReturn(Optional.of(user("ACTIVE")));

        assertThat(service.login(new LoginRequest("Teacher1", PASSWORD)).getAccessToken()).isEqualTo("access");
    }

    @Test
    void login_wrongPassword_countsFailureWithoutLocking() {
        when(userRepository.findByUsernameAndIsDeleted("teacher1", 0)).thenReturn(Optional.of(user("ACTIVE")));
        when(userRepository.findFailedLoginCountById(10L)).thenReturn(Optional.of(1));

        assertThatThrownBy(() -> service.login(new LoginRequest("teacher1", "wrong")))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.INVALID_CREDENTIALS);
        verify(userRepository).incrementFailedLoginCount(10L);
        verify(userRepository, never()).lockUntil(anyLong(), any());
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).createSession(anyLong());
    }

    @Test
    void login_fifthFailure_locksFor15Minutes() {
        when(userRepository.findByUsernameAndIsDeleted("teacher1", 0)).thenReturn(Optional.of(user("ACTIVE")));
        when(userRepository.findFailedLoginCountById(10L)).thenReturn(Optional.of(5));

        assertThatThrownBy(() -> service.login(new LoginRequest("teacher1", "wrong")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("15 phút")
                .extracting("errorCode").isEqualTo(AuthServiceImpl.ACCOUNT_TEMPORARILY_LOCKED);
        verify(userRepository).lockUntil(10L, NOW.plusMinutes(15));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_repeatedLockout_doublesDuration() {
        when(userRepository.findByUsernameAndIsDeleted("teacher1", 0)).thenReturn(Optional.of(user("ACTIVE")));
        when(userRepository.findFailedLoginCountById(10L)).thenReturn(Optional.of(10));

        assertThatThrownBy(() -> service.login(new LoginRequest("teacher1", "wrong")))
                .extracting("errorCode").isEqualTo(AuthServiceImpl.ACCOUNT_TEMPORARILY_LOCKED);
        verify(userRepository).lockUntil(10L, NOW.plusMinutes(30));
    }

    @Test
    void lockoutDuration_doublesAndIsCappedAt24h() {
        AuthSecurityProperties.Lockout policy = new AuthSecurityProperties.Lockout();
        assertThat(AuthServiceImpl.lockoutDuration(1, policy)).isEqualTo(Duration.ofMinutes(15));
        assertThat(AuthServiceImpl.lockoutDuration(2, policy)).isEqualTo(Duration.ofMinutes(30));
        assertThat(AuthServiceImpl.lockoutDuration(3, policy)).isEqualTo(Duration.ofMinutes(60));
        assertThat(AuthServiceImpl.lockoutDuration(7, policy)).isEqualTo(Duration.ofMinutes(15 * 64));
        assertThat(AuthServiceImpl.lockoutDuration(8, policy)).isEqualTo(Duration.ofHours(24));
        assertThat(AuthServiceImpl.lockoutDuration(1000, policy)).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void login_whileTemporarilyLocked_rejectedEvenWithCorrectPassword() {
        UserEntity user = user("ACTIVE");
        user.setFailedLoginCount(5);
        user.setLockedUntil(NOW.plusMinutes(7).plusSeconds(10));
        when(userRepository.findByUsernameAndIsDeleted("teacher1", 0)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("teacher1", PASSWORD)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Tài khoản tạm thời bị khóa do nhập sai mật khẩu nhiều lần. Vui lòng thử lại sau 8 phút.")
                .extracting("errorCode").isEqualTo(AuthServiceImpl.ACCOUNT_TEMPORARILY_LOCKED);
        // Vẫn so khớp BCrypt (thời gian phản hồi như bình thường) nhưng không đếm thêm lần sai, không tạo phiên.
        verify(encoder).matches(eq(PASSWORD), anyString());
        verify(userRepository, never()).incrementFailedLoginCount(anyLong());
        verify(refreshTokenService, never()).createSession(anyLong());
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void login_afterLockExpired_successResetsCounters() {
        UserEntity user = user("ACTIVE");
        user.setFailedLoginCount(5);
        user.setLockedUntil(NOW.minusSeconds(1));
        when(userRepository.findByUsernameAndIsDeleted("teacher1", 0)).thenReturn(Optional.of(user));

        service.login(new LoginRequest("teacher1", PASSWORD));

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void minutesRemaining_roundsUpAndIsAtLeastOne() {
        assertThat(AuthServiceImpl.minutesRemaining(NOW.plusSeconds(1), NOW)).isEqualTo(1);
        assertThat(AuthServiceImpl.minutesRemaining(NOW.plusMinutes(15), NOW)).isEqualTo(15);
        assertThat(AuthServiceImpl.minutesRemaining(NOW.minusMinutes(1), NOW)).isEqualTo(1);
    }

    @Test
    void login_unknownUser_rejectedWithSameCodeAndStillHashes() {
        when(userRepository.findByUsernameAndIsDeleted("ghost", 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost", PASSWORD)))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.INVALID_CREDENTIALS);
        verify(encoder).matches(eq(PASSWORD), anyString());
        verify(userRepository, never()).incrementFailedLoginCount(anyLong());
    }

    @Test
    void login_lockedUser_rejected() {
        when(userRepository.findByUsernameAndIsDeleted("teacher1", 0)).thenReturn(Optional.of(user("LOCKED")));

        assertThatThrownBy(() -> service.login(new LoginRequest("teacher1", PASSWORD)))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.ACCOUNT_LOCKED);
    }

    @Test
    void login_inactiveUser_rejected() {
        when(userRepository.findByUsernameAndIsDeleted("teacher1", 0)).thenReturn(Optional.of(user("INACTIVE")));

        assertThatThrownBy(() -> service.login(new LoginRequest("teacher1", PASSWORD)))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.ACCOUNT_INACTIVE);
    }

    @Test
    void login_legacyNonBcryptHash_rejectedGracefully() {
        UserEntity legacy = user("ACTIVE");
        legacy.setPasswordHash("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
        when(userRepository.findByUsernameAndIsDeleted("teacher1", 0)).thenReturn(Optional.of(legacy));
        when(userRepository.findFailedLoginCountById(10L)).thenReturn(Optional.of(1));

        assertThatThrownBy(() -> service.login(new LoginRequest("teacher1", PASSWORD)))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.INVALID_CREDENTIALS);
    }

    // ---------------------------------------------------------------- refresh

    @Test
    void refresh_validToken_rotatesInSameSession() {
        when(jwtTokenService.parse("r1", TokenType.REFRESH)).thenReturn(refreshClaims(2, "jti-old"));
        when(userRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(user("ACTIVE")));
        when(refreshTokenService.rotate(10L, "jti-old")).thenReturn(RotationResult.rotated(
                new IssuedRefreshToken("jti-new", "sid-1", NOW.plusDays(7))));

        AuthTokens response = service.refresh("r1");

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        verify(jwtTokenService).generateRefreshToken(10L, "teacher1", 2, "jti-new", "sid-1");
        verify(jwtTokenService).generateAccessToken(10L, "teacher1", 2, "sid-1");
    }

    @Test
    void refresh_reusedToken_rejectedWithReuseCode() {
        when(jwtTokenService.parse("r1", TokenType.REFRESH)).thenReturn(refreshClaims(2, "jti-old"));
        when(userRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(user("ACTIVE")));
        when(refreshTokenService.rotate(10L, "jti-old")).thenReturn(RotationResult.rejected(Rejection.REUSED));

        assertThatThrownBy(() -> service.refresh("r1"))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.REFRESH_TOKEN_REUSED);
    }

    @Test
    void refresh_unknownOrRevokedToken_rejected() {
        when(jwtTokenService.parse("r1", TokenType.REFRESH)).thenReturn(refreshClaims(2, null));
        when(userRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(user("ACTIVE")));
        when(refreshTokenService.rotate(10L, null)).thenReturn(RotationResult.rejected(Rejection.UNKNOWN));

        assertThatThrownBy(() -> service.refresh("r1"))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.REFRESH_TOKEN_INVALID);
    }

    @Test
    void refresh_revokedVersion_rejectedWithoutRotation() {
        when(jwtTokenService.parse("r1", TokenType.REFRESH)).thenReturn(refreshClaims(1, "jti-old"));
        when(userRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(user("ACTIVE")));

        assertThatThrownBy(() -> service.refresh("r1"))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.REFRESH_TOKEN_INVALID);
        verify(refreshTokenService, never()).rotate(anyLong(), any());
    }

    @Test
    void refresh_invalidToken_rejected() {
        when(jwtTokenService.parse("bad", TokenType.REFRESH))
                .thenThrow(new InvalidTokenException(InvalidTokenException.TOKEN_EXPIRED, "expired"));

        assertThatThrownBy(() -> service.refresh("bad"))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.REFRESH_TOKEN_INVALID);
    }

    @Test
    void refresh_lockedUser_rejected() {
        when(jwtTokenService.parse("r1", TokenType.REFRESH)).thenReturn(refreshClaims(2, "jti-old"));
        when(userRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(user("LOCKED")));

        assertThatThrownBy(() -> service.refresh("r1"))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.ACCOUNT_LOCKED);
    }

    // ----------------------------------------------------------------- logout

    @Test
    void logout_revokesSessionOfAccessToken_withoutBumpingTokenVersion() {
        service.logout(10L, "sid-1", null);

        verify(refreshTokenService).revokeSession(10L, "sid-1");
        verify(userRepository, never()).incrementTokenVersion(anyLong());
        verify(userRepository, never()).save(any());
    }

    @Test
    void logout_prefersSessionOfOwnRefreshToken() {
        when(jwtTokenService.parse("r1", TokenType.REFRESH))
                .thenReturn(new JwtClaims(10L, "teacher1", 2, TokenType.REFRESH, null, "jti", "sid-of-refresh"));

        service.logout(10L, "sid-1", "r1");

        verify(refreshTokenService).revokeSession(10L, "sid-of-refresh");
    }

    @Test
    void logout_ignoresRefreshTokenOfAnotherUser() {
        when(jwtTokenService.parse("r1", TokenType.REFRESH))
                .thenReturn(new JwtClaims(99L, "other", 0, TokenType.REFRESH, null, "jti", "sid-other"));

        service.logout(10L, "sid-1", "r1");

        verify(refreshTokenService).revokeSession(10L, "sid-1");
        verify(refreshTokenService, never()).revokeSession(anyLong(), eq("sid-other"));
    }

    @Test
    void logout_withExpiredAccessToken_revokesSessionOfRefreshCookie() {
        when(jwtTokenService.parse("r1", TokenType.REFRESH))
                .thenReturn(new JwtClaims(10L, "teacher1", 2, TokenType.REFRESH, null, "jti", "sid-of-refresh"));

        service.logout(null, null, "r1");

        verify(refreshTokenService).revokeSession(10L, "sid-of-refresh");
    }

    @Test
    void logout_anonymousWithInvalidRefreshToken_revokesNothing() {
        when(jwtTokenService.parse("forged", TokenType.REFRESH))
                .thenThrow(new InvalidTokenException(InvalidTokenException.TOKEN_INVALID, "bad"));

        service.logout(null, "sid-ignored", "forged");
        service.logout(null, null, null);

        verify(refreshTokenService, never()).revokeSession(any(), any());
    }

    @Test
    void refresh_missingToken_rejectedWithoutParsing() {
        assertThatThrownBy(() -> service.refresh(" "))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.REFRESH_TOKEN_INVALID);
        verify(jwtTokenService, never()).parse(any(), any());
    }

    @Test
    void logout_legacyTokenWithoutSession_revokesNothing() {
        service.logout(10L, null, null);

        verify(refreshTokenService, never()).revokeSession(anyLong(), any());
        verify(userRepository, never()).incrementTokenVersion(anyLong());
    }

    // -------------------------------------------------------- change password

    @Test
    void changePassword_success_bumpsVersionAtomicallyAndRevokesAllSessions() {
        UserEntity user = user("ACTIVE");
        when(userRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(user));
        when(userRepository.findTokenVersionById(10L)).thenReturn(Optional.of(3));

        AuthTokens response = service.changePassword(10L, new ChangePasswordRequest(PASSWORD, "NewPass@456"));

        assertThat(ENCODER.matches("NewPass@456", user.getPasswordHash())).isTrue();
        assertThat(user.getMustChangePassword()).isZero();
        assertThat(user.getTokenVersion()).isEqualTo(3);
        assertThat(user.getPasswordChangedAt()).isEqualTo(NOW);
        assertThat(response.getUser().isMustChangePassword()).isFalse();
        InOrder order = inOrder(userRepository, refreshTokenService);
        order.verify(userRepository).incrementTokenVersion(10L);
        order.verify(refreshTokenService).revokeAllSessions(10L);
        order.verify(refreshTokenService).createSession(10L);
        verify(jwtTokenService).generateAccessToken(eq(10L), eq("teacher1"), eq(3), eq("sid-1"));
    }

    @Test
    void changePassword_wrongOldPassword_isBusinessError() {
        when(userRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(user("ACTIVE")));

        assertThatThrownBy(() -> service.changePassword(10L, new ChangePasswordRequest("wrong", "NewPass@456")))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("OLD_PASSWORD_INCORRECT");
    }

    @Test
    void changePassword_samePassword_rejected() {
        when(userRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(user("ACTIVE")));

        assertThatThrownBy(() -> service.changePassword(10L, new ChangePasswordRequest(PASSWORD, PASSWORD)))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("PASSWORD_UNCHANGED");
    }
}
