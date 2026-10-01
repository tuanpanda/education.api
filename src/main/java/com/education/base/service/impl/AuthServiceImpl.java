package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.PersistenceFlags;
import com.education.base.config.AuthSecurityProperties;
import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.dto.request.LoginRequest;
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
import com.education.base.service.AuthTokens;
import com.education.base.service.RefreshTokenService;
import com.education.base.service.RefreshTokenService.IssuedRefreshToken;
import com.education.base.service.RefreshTokenService.RotationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
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
    static final String ACCOUNT_TEMPORARILY_LOCKED = "ACCOUNT_TEMPORARILY_LOCKED";
    static final String ACCOUNT_INACTIVE = "ACCOUNT_INACTIVE";
    static final String REFRESH_TOKEN_INVALID = "REFRESH_TOKEN_INVALID";
    static final String REFRESH_TOKEN_REUSED = "REFRESH_TOKEN_REUSED";

    /**
     * BCrypt của một chuỗi ngẫu nhiên: so khớp khi không tìm thấy tài khoản để thời gian phản hồi
     * tương đương, tránh dò tên đăng nhập.
     */
    private static final String DUMMY_HASH = "$2a$10$lPMaBFKzPUQE6ytd.VBjh.m2rKow9FVTsXMo100WP93mO6bOOIKku";

    /** Chặn tràn số khi nhân đôi thời gian khóa. */
    private static final int MAX_LOCKOUT_DOUBLINGS = 20;

    private final UserRepository userRepository;
    private final AccessControlService accessControlService;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuthSecurityProperties authSecurityProperties;
    private final Clock clock;

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = UnauthorizedException.class)
    public AuthTokens login(LoginRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        Optional<UserEntity> found = findByUsername(username);
        if (found.isEmpty()) {
            passwordEncoder.matches(request.getPassword(), DUMMY_HASH);
            throw invalidCredentials();
        }
        UserEntity user = found.get();
        LocalDateTime now = LocalDateTime.now(clock);
        // Luôn so khớp BCrypt (kể cả khi đang khóa tạm thời) để thời gian phản hồi không lộ trạng thái.
        boolean passwordOk = passwordMatches(request.getPassword(), user.getPasswordHash());
        if (isTemporarilyLocked(user, now)) {
            log.warn("Đăng nhập bị chặn: tài khoản đang khóa tạm thời tới {}, username={}",
                    user.getLockedUntil(), user.getUsername());
            throw temporarilyLocked(user.getLockedUntil(), now);
        }
        if (!passwordOk) {
            registerFailedLogin(user, now);
        }
        ensureCanSignIn(user);

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        userRepository.save(user);
        IssuedRefreshToken session = refreshTokenService.createSession(user.getId());
        log.info("Đăng nhập thành công: userId={}, username={}, sid={}",
                user.getId(), user.getUsername(), session.sessionId());
        return issueTokens(user, tokenVersion(user), session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = UnauthorizedException.class)
    public AuthTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException(REFRESH_TOKEN_INVALID,
                    "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.");
        }
        JwtClaims claims;
        try {
            claims = jwtTokenService.parse(refreshToken, TokenType.REFRESH);
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
        RotationResult result = refreshTokenService.rotate(user.getId(), claims.jti());
        if (!result.isRotated()) {
            throw switch (result.rejection()) {
                case REUSED -> new UnauthorizedException(REFRESH_TOKEN_REUSED,
                        "Phát hiện phiên đăng nhập bị dùng lại bất thường, phiên đã bị thu hồi. "
                                + "Vui lòng đăng nhập lại.");
                case EXPIRED -> new UnauthorizedException(REFRESH_TOKEN_INVALID,
                        "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.");
                default -> new UnauthorizedException(REFRESH_TOKEN_INVALID,
                        "Phiên đăng nhập đã bị thu hồi, vui lòng đăng nhập lại.");
            };
        }
        return issueTokens(user, tokenVersion(user), result.issued());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(Long userId, String sessionId, String refreshToken) {
        JwtClaims refreshClaims = verifiedRefreshClaims(userId, refreshToken);
        Long owner = userId;
        String target = null;
        if (refreshClaims != null) {
            owner = refreshClaims.userId();
            target = refreshClaims.sessionId();
        }
        if ((target == null || target.isBlank()) && userId != null) {
            target = sessionId;
        }
        if (owner == null || target == null || target.isBlank()) {
            log.info("Đăng xuất: userId={} không xác định được phiên (chưa đăng nhập / token cũ không có sid), "
                    + "không thu hồi gì", owner);
            return;
        }
        refreshTokenService.revokeSession(owner, target);
        log.info("Đăng xuất: userId={}, đã thu hồi phiên sid={}", owner, target);
    }

    @Override
    public AuthUserResponse me(AuthUserPrincipal principal) {
        return toUserResponse(principal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthTokens changePassword(Long userId, ChangePasswordRequest request) {
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
        user.setPasswordChangedAt(LocalDateTime.now(clock));
        user.setUpdatedBy(user.getUsername());
        userRepository.save(user);
        int version = bumpTokenVersion(user);
        refreshTokenService.revokeAllSessions(userId);
        IssuedRefreshToken session = refreshTokenService.createSession(userId);
        log.info("Đổi mật khẩu thành công: userId={}, mọi phiên cũ đã bị thu hồi", userId);
        return issueTokens(user, version, session);
    }

    /**
     * Tăng {@code TOKEN_VERSION} nguyên tử ở DB rồi đồng bộ giá trị mới vào entity.
     */
    private int bumpTokenVersion(UserEntity user) {
        userRepository.incrementTokenVersion(user.getId());
        int version = userRepository.findTokenVersionById(user.getId()).orElse(tokenVersion(user) + 1);
        user.setTokenVersion(version);
        return version;
    }

    /**
     * Ghi nhận một lần sai mật khẩu (tăng nguyên tử ở DB); đủ ngưỡng thì khóa tạm thời. Luôn ném lỗi.
     */
    private void registerFailedLogin(UserEntity user, LocalDateTime now) {
        AuthSecurityProperties.Lockout policy = authSecurityProperties.getLockout();
        userRepository.incrementFailedLoginCount(user.getId());
        int failures = userRepository.findFailedLoginCountById(user.getId())
                .orElse(nz(user.getFailedLoginCount()) + 1);
        log.warn("Đăng nhập thất bại: sai mật khẩu, username={}, số lần sai liên tiếp={}", user.getUsername(), failures);
        if (failures > 0 && failures % policy.getMaxFailedAttempts() == 0) {
            Duration lock = lockoutDuration(failures / policy.getMaxFailedAttempts(), policy);
            LocalDateTime until = now.plus(lock);
            userRepository.lockUntil(user.getId(), until);
            log.warn("Khóa tạm thời tài khoản username={} trong {} phút (lần sai thứ {})",
                    user.getUsername(), lock.toMinutes(), failures);
            throw temporarilyLocked(until, now);
        }
        throw invalidCredentials();
    }

    /**
     * Lần khóa thứ {@code lockoutNumber} (bắt đầu từ 1): {@code base * 2^(n-1)}, tối đa {@code max}.
     */
    static Duration lockoutDuration(int lockoutNumber, AuthSecurityProperties.Lockout policy) {
        int doublings = Math.min(Math.max(lockoutNumber - 1, 0), MAX_LOCKOUT_DOUBLINGS);
        Duration duration = policy.getBaseDuration().multipliedBy(1L << doublings);
        return duration.compareTo(policy.getMaxDuration()) > 0 ? policy.getMaxDuration() : duration;
    }

    private static boolean isTemporarilyLocked(UserEntity user, LocalDateTime now) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
    }

    static long minutesRemaining(LocalDateTime until, LocalDateTime now) {
        long seconds = Math.max(Duration.between(now, until).getSeconds(), 0);
        return Math.max((seconds + 59) / 60, 1);
    }

    private static UnauthorizedException temporarilyLocked(LocalDateTime until, LocalDateTime now) {
        return new UnauthorizedException(ACCOUNT_TEMPORARILY_LOCKED,
                "Tài khoản tạm thời bị khóa do nhập sai mật khẩu nhiều lần. Vui lòng thử lại sau "
                        + minutesRemaining(until, now) + " phút.");
    }

    /**
     * Claim của refresh token gửi kèm khi đăng xuất: chỉ khi chữ ký hợp lệ và (nếu đã đăng nhập) thuộc chính
     * người dùng {@code userId}.
     */
    private JwtClaims verifiedRefreshClaims(Long userId, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        try {
            JwtClaims claims = jwtTokenService.parse(refreshToken, TokenType.REFRESH);
            if (claims == null || claims.userId() == null) {
                return null;
            }
            if (userId == null || Objects.equals(claims.userId(), userId)) {
                return claims;
            }
            log.warn("Đăng xuất: refresh token gửi kèm không thuộc userId={}, bỏ qua", userId);
        } catch (InvalidTokenException ex) {
            log.debug("Đăng xuất: refresh token gửi kèm không hợp lệ ({}), dùng sid của access token", ex.getErrorCode());
        }
        return null;
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

    private AuthTokens issueTokens(UserEntity user, int version, IssuedRefreshToken session) {
        AuthUserPrincipal principal = accessControlService.buildPrincipal(user);
        return AuthTokens.builder()
                .accessToken(jwtTokenService.generateAccessToken(user.getId(), user.getUsername(), version,
                        session.sessionId()))
                .refreshToken(jwtTokenService.generateRefreshToken(user.getId(), user.getUsername(), version,
                        session.jti(), session.sessionId()))
                .accessTokenTtlSeconds(jwtTokenService.getAccessTokenTtlSeconds())
                .refreshTokenTtlSeconds(jwtTokenService.getRefreshTokenTtlSeconds())
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

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException(INVALID_CREDENTIALS, "Tên đăng nhập hoặc mật khẩu không đúng.");
    }
}
