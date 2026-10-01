package com.education.base.service.impl;

import com.education.base.config.JwtProperties;
import com.education.base.entity.RefreshTokenEntity;
import com.education.base.repository.RefreshTokenRepository;
import com.education.base.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    /** Giữ lại token đã hết hạn thêm một thời gian ngắn rồi mới dọn (khi người dùng đăng nhập lại). */
    static final Duration EXPIRED_RETENTION = Duration.ofDays(1);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IssuedRefreshToken createSession(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        int purged = refreshTokenRepository.deleteExpiredOfUser(userId, now.minus(EXPIRED_RETENTION));
        if (purged > 0) {
            log.debug("Đã dọn {} refresh token hết hạn của userId={}", purged, userId);
        }
        return issue(userId, UUID.randomUUID().toString(), now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RotationResult rotate(Long userId, String jti) {
        if (userId == null || jti == null || jti.isBlank()) {
            return RotationResult.rejected(Rejection.UNKNOWN);
        }
        Optional<RefreshTokenEntity> found = refreshTokenRepository.findByJtiForUpdate(jti);
        if (found.isEmpty() || !Objects.equals(found.get().getUserId(), userId)) {
            log.warn("Refresh token không có trong SYS_REFRESH_TOKENS: userId={}, jti={}", userId, jti);
            return RotationResult.rejected(Rejection.UNKNOWN);
        }
        RefreshTokenEntity current = found.get();
        LocalDateTime now = LocalDateTime.now(clock);

        if (current.getRevokedAt() != null) {
            return handleRevoked(current, now);
        }
        if (!current.getExpiresAt().isAfter(now)) {
            return RotationResult.rejected(Rejection.EXPIRED);
        }
        IssuedRefreshToken next = issue(userId, current.getFamilyId(), now);
        current.setRevokedAt(now);
        current.setReplacedBy(next.jti());
        refreshTokenRepository.save(current);
        return RotationResult.rotated(next);
    }

    private RotationResult handleRevoked(RefreshTokenEntity current, LocalDateTime now) {
        String familyId = current.getFamilyId();
        if (current.getReplacedBy() == null) {
            // Bị thu hồi do đăng xuất / đổi mật khẩu / phát hiện dùng lại trước đó.
            return RotationResult.rejected(Rejection.REVOKED);
        }
        Duration grace = jwtProperties.getRefreshReuseGrace();
        boolean withinGrace = grace != null && !grace.isZero() && !grace.isNegative()
                && !now.isAfter(current.getRevokedAt().plus(grace));
        if (withinGrace) {
            if (!refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNull(familyId)) {
                return RotationResult.rejected(Rejection.REVOKED);
            }
            // Nhiều tab làm mới đồng thời bằng cùng một token: cấp thêm token trong cùng phiên, không coi là tấn công.
            log.info("Refresh token vừa xoay vòng được dùng lại trong khoảng ân hạn: userId={}, sid={}",
                    current.getUserId(), familyId);
            return RotationResult.rotated(issue(current.getUserId(), familyId, now));
        }
        int revoked = refreshTokenRepository.revokeFamily(familyId, now);
        log.warn("PHÁT HIỆN DÙNG LẠI refresh token đã xoay vòng: userId={}, sid={}, jti={} -> thu hồi {} token của phiên",
                current.getUserId(), familyId, current.getJti(), revoked);
        return RotationResult.rejected(Rejection.REUSED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeSession(Long userId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        int revoked = refreshTokenRepository.revokeFamilyOfUser(sessionId, userId, LocalDateTime.now(clock));
        log.info("Thu hồi phiên sid={} của userId={}: {} refresh token", sessionId, userId, revoked);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeAllSessions(Long userId) {
        if (userId == null) {
            return;
        }
        int revoked = refreshTokenRepository.revokeAllOfUser(userId, LocalDateTime.now(clock));
        log.info("Thu hồi mọi phiên của userId={}: {} refresh token", userId, revoked);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSessionActive(String sessionId) {
        return sessionId != null && refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNull(sessionId);
    }

    private IssuedRefreshToken issue(Long userId, String familyId, LocalDateTime now) {
        LocalDateTime expiresAt = now.plus(jwtProperties.getRefreshTokenTtl());
        RefreshTokenEntity entity = RefreshTokenEntity.builder()
                .userId(userId)
                .jti(UUID.randomUUID().toString())
                .familyId(familyId)
                .expiresAt(expiresAt)
                .createdAt(now)
                .build();
        refreshTokenRepository.save(entity);
        return new IssuedRefreshToken(entity.getJti(), familyId, expiresAt);
    }
}
