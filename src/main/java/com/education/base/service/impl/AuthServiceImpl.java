package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.dto.request.LoginRequest;
import com.education.base.dto.request.RefreshTokenRequest;
import com.education.base.dto.response.AuthTokenResponse;
import com.education.base.dto.response.AuthUserResponse;
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
import com.education.base.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    static final String ACCOUNT_INACTIVE = "ACCOUNT_INACTIVE";
    static final String REFRESH_TOKEN_INVALID = "REFRESH_TOKEN_INVALID";

    /**
     * BCrypt của một chuỗi ngẫu nhiên: so khớp khi không tìm thấy tài khoản để thời gian phản hồi
     * tương đương, tránh dò tên đăng nhập.
     */
    private static final String DUMMY_HASH = "$2a$10$lPMaBFKzPUQE6ytd.VBjh.m2rKow9FVTsXMo100WP93mO6bOOIKku";

    private final UserRepository userRepository;
    private final AccessControlService accessControlService;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenResponse login(LoginRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        Optional<UserEntity> found = findByUsername(username);
        if (found.isEmpty()) {
            passwordEncoder.matches(request.getPassword(), DUMMY_HASH);
            throw invalidCredentials();
        }
        UserEntity user = found.get();
        if (!passwordMatches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Đăng nhập thất bại: sai mật khẩu, username={}", user.getUsername());
            throw invalidCredentials();
        }
        ensureCanSignIn(user);

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Đăng nhập thành công: userId={}, username={}", user.getId(), user.getUsername());
        return issueTokens(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        JwtClaims claims;
        try {
            claims = jwtTokenService.parse(request.getRefreshToken(), TokenType.REFRESH);
        } catch (InvalidTokenException ex) {
            throw new UnauthorizedException(REFRESH_TOKEN_INVALID,
                    "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.");
        }
        UserEntity user = userRepository.findByIdAndIsDeleted(claims.userId(), PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new UnauthorizedException(REFRESH_TOKEN_INVALID,
                        "Phiên đăng nhập không còn hợp lệ, vui lòng đăng nhập lại."));
        ensureCanSignIn(user);
        if (!Objects.equals(tokenVersion(user), claims.tokenVersion())) {
            throw new UnauthorizedException(REFRESH_TOKEN_INVALID,
                    "Phiên đăng nhập đã bị thu hồi, vui lòng đăng nhập lại.");
        }
        return issueTokens(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(Long userId) {
        userRepository.findByIdAndIsDeleted(userId, PersistenceFlags.NOT_DELETED).ifPresent(user -> {
            user.setTokenVersion(tokenVersion(user) + 1);
            userRepository.save(user);
            log.info("Đăng xuất: userId={}, token cũ đã bị thu hồi", userId);
        });
    }

    @Override
    public AuthUserResponse me(AuthUserPrincipal principal) {
        return toUserResponse(principal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenResponse changePassword(Long userId, ChangePasswordRequest request) {
        UserEntity user = userRepository.findByIdAndIsDeleted(userId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new UnauthorizedException("UNAUTHORIZED", "Vui lòng đăng nhập để tiếp tục."));
        if (!passwordMatches(request.getOldPassword(), user.getPasswordHash())) {
            throw new OracleBusinessException("OLD_PASSWORD_INCORRECT", "Mật khẩu hiện tại không đúng.");
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new OracleBusinessException("PASSWORD_UNCHANGED", "Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(0);
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setTokenVersion(tokenVersion(user) + 1);
        user.setUpdatedBy(user.getUsername());
        userRepository.save(user);
        log.info("Đổi mật khẩu thành công: userId={}", userId);
        return issueTokens(user);
    }

    private Optional<UserEntity> findByUsername(String username) {
        if (username.isEmpty()) {
            return Optional.empty();
        }
        Optional<UserEntity> exact = userRepository.findByUsernameAndIsDeleted(username, PersistenceFlags.NOT_DELETED);
        if (exact.isPresent()) {
            return exact;
        }
        String lower = username.toLowerCase(Locale.ROOT);
        return lower.equals(username)
                ? Optional.empty()
                : userRepository.findByUsernameAndIsDeleted(lower, PersistenceFlags.NOT_DELETED);
    }

    private boolean passwordMatches(String raw, String hash) {
        if (raw == null || hash == null || hash.isBlank()) {
            return false;
        }
        try {
            return passwordEncoder.matches(raw, hash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static void ensureCanSignIn(UserEntity user) {
        if (DomainConstants.USER_STATUS_LOCKED.equals(user.getStatus())) {
            throw new UnauthorizedException(ACCOUNT_LOCKED,
                    "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }
        if (!DomainConstants.USER_STATUS_ACTIVE.equals(user.getStatus())) {
            throw new UnauthorizedException(ACCOUNT_INACTIVE,
                    "Tài khoản chưa được kích hoạt hoặc đã ngừng hoạt động.");
        }
    }

    private AuthTokenResponse issueTokens(UserEntity user) {
        AuthUserPrincipal principal = accessControlService.buildPrincipal(user);
        int version = tokenVersion(user);
        return AuthTokenResponse.builder()
                .accessToken(jwtTokenService.generateAccessToken(user.getId(), user.getUsername(), version))
                .refreshToken(jwtTokenService.generateRefreshToken(user.getId(), user.getUsername(), version))
                .tokenType(AuthTokenResponse.BEARER)
                .expiresIn(jwtTokenService.getAccessTokenTtlSeconds())
                .refreshExpiresIn(jwtTokenService.getRefreshTokenTtlSeconds())
                .user(toUserResponse(principal))
                .build();
    }

    static AuthUserResponse toUserResponse(AuthUserPrincipal principal) {
        return AuthUserResponse.builder()
                .id(principal.getId())
                .username(principal.getUsername())
                .fullName(principal.getFullName())
                .email(principal.getEmail())
                .roles(new ArrayList<>(principal.getRoles()))
                .permissions(new ArrayList<>(principal.getPermissions()))
                .mustChangePassword(principal.isMustChangePassword())
                .build();
    }

    private static int tokenVersion(UserEntity user) {
        return user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException(INVALID_CREDENTIALS, "Tên đăng nhập hoặc mật khẩu không đúng.");
    }
}
