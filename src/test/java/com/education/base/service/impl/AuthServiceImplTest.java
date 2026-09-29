package com.education.base.service.impl;

import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.dto.request.LoginRequest;
import com.education.base.dto.request.RefreshTokenRequest;
import com.education.base.dto.response.AuthTokenResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private static final String PASSWORD = "Secret@123";

    @Mock
    private UserRepository userRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private JwtTokenService jwtTokenService;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, accessControlService, jwtTokenService, ENCODER);
        lenient().when(jwtTokenService.generateAccessToken(anyLong(), anyString(), anyInt())).thenReturn("access");
        lenient().when(jwtTokenService.generateRefreshToken(anyLong(), anyString(), anyInt())).thenReturn("refresh");
        lenient().when(jwtTokenService.getAccessTokenTtlSeconds()).thenReturn(900L);
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
                .build();
    }

    @Test
    void login_success_returnsTokensAndUser() {
        UserEntity user = user("ACTIVE");
        when(userRepository.findByUsernameAndIsDeleted("teacher1", 0)).thenReturn(Optional.of(user));

        AuthTokenResponse response = service.login(new LoginRequest("teacher1", PASSWORD));

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900L);
        assertThat(response.getUser().getUsername()).isEqualTo("teacher1");
        assertThat(response.getUser().getRoles()).containsExactly("ROLE_TEACHER");
        assertThat(response.getUser().getPermissions()).containsExactly("MENU_STUDENT_LIST:VIEW");
        assertThat(response.getUser().isMustChangePassword()).isTrue();
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(jwtTokenService).generateAccessToken(10L, "teacher1", 2);
    }

    @Test
    void login_usernameIsCaseInsensitiveFallback() {
        when(userRepository.findByUsernameAndIsDeleted("Teacher1", 0)).thenReturn(Optional.empty());
        when(userRepository.findByUsernameAndIsDeleted("teacher1", 0)).thenReturn(Optional.of(user("ACTIVE")));

        assertThat(service.login(new LoginRequest("Teacher1", PASSWORD)).getAccessToken()).isEqualTo("access");
    }

    @Test
    void login_wrongPassword_rejected() {
        when(userRepository.findByUsernameAndIsDeleted("teacher1", 0)).thenReturn(Optional.of(user("ACTIVE")));

        assertThatThrownBy(() -> service.login(new LoginRequest("teacher1", "wrong")))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.INVALID_CREDENTIALS);
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_unknownUser_rejectedWithSameCode() {
        when(userRepository.findByUsernameAndIsDeleted("ghost", 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost", PASSWORD)))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.INVALID_CREDENTIALS);
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

        assertThatThrownBy(() -> service.login(new LoginRequest("teacher1", PASSWORD)))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.INVALID_CREDENTIALS);
    }

    @Test
    void refresh_validToken_issuesNewPair() {
        when(jwtTokenService.parse("r1", TokenType.REFRESH))
                .thenReturn(new JwtClaims(10L, "teacher1", 2, TokenType.REFRESH, null));
        when(userRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(user("ACTIVE")));

        AuthTokenResponse response = service.refresh(new RefreshTokenRequest("r1"));

        assertThat(response.getAccessToken()).isEqualTo("access");
    }

    @Test
    void refresh_revokedVersion_rejected() {
        when(jwtTokenService.parse("r1", TokenType.REFRESH))
                .thenReturn(new JwtClaims(10L, "teacher1", 1, TokenType.REFRESH, null));
        when(userRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(user("ACTIVE")));

        assertThatThrownBy(() -> service.refresh(new RefreshTokenRequest("r1")))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.REFRESH_TOKEN_INVALID);
    }

    @Test
    void refresh_invalidToken_rejected() {
        when(jwtTokenService.parse("bad", TokenType.REFRESH))
                .thenThrow(new InvalidTokenException(InvalidTokenException.TOKEN_EXPIRED, "expired"));

        assertThatThrownBy(() -> service.refresh(new RefreshTokenRequest("bad")))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.REFRESH_TOKEN_INVALID);
    }

    @Test
    void refresh_lockedUser_rejected() {
        when(jwtTokenService.parse("r1", TokenType.REFRESH))
                .thenReturn(new JwtClaims(10L, "teacher1", 2, TokenType.REFRESH, null));
        when(userRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(user("LOCKED")));

        assertThatThrownBy(() -> service.refresh(new RefreshTokenRequest("r1")))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(AuthServiceImpl.ACCOUNT_LOCKED);
    }

    @Test
    void logout_bumpsTokenVersion() {
        UserEntity user = user("ACTIVE");
        when(userRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(user));

        service.logout(10L);

        assertThat(user.getTokenVersion()).isEqualTo(3);
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_success_clearsFlagAndRevokesOldTokens() {
        UserEntity user = user("ACTIVE");
        when(userRepository.findByIdAndIsDeleted(10L, 0)).thenReturn(Optional.of(user));

        AuthTokenResponse response = service.changePassword(10L, new ChangePasswordRequest(PASSWORD, "NewPass@456"));

        assertThat(ENCODER.matches("NewPass@456", user.getPasswordHash())).isTrue();
        assertThat(user.getMustChangePassword()).isZero();
        assertThat(user.getTokenVersion()).isEqualTo(3);
        assertThat(user.getPasswordChangedAt()).isNotNull();
        assertThat(response.getUser().isMustChangePassword()).isFalse();
        verify(jwtTokenService).generateAccessToken(eq(10L), eq("teacher1"), eq(3));
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
