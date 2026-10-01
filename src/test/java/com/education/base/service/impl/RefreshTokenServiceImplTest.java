package com.education.base.service.impl;

import com.education.base.config.JwtProperties;
import com.education.base.entity.RefreshTokenEntity;
import com.education.base.repository.RefreshTokenRepository;
import com.education.base.service.RefreshTokenService.IssuedRefreshToken;
import com.education.base.service.RefreshTokenService.Rejection;
import com.education.base.service.RefreshTokenService.RotationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Xoay vòng refresh token, phát hiện dùng lại, khoảng ân hạn cho nhiều tab, thu hồi phiên.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-09-30T03:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZoneOffset.UTC);

    @Mock
    private RefreshTokenRepository repository;

    private JwtProperties properties;
    private RefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret("unit-test-secret-0123456789-abcdefghijklmnop");
        properties.setRefreshTokenTtl(Duration.ofDays(7));
        properties.setRefreshReuseGrace(Duration.ofSeconds(10));
        service = new RefreshTokenServiceImpl(repository, properties, Clock.fixed(NOW_INSTANT, ZoneOffset.UTC));
        lenient().when(repository.save(any(RefreshTokenEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static RefreshTokenEntity token(String jti, LocalDateTime revokedAt, String replacedBy) {
        return RefreshTokenEntity.builder()
                .id(1L).userId(10L).jti(jti).familyId("family-1")
                .expiresAt(NOW.plusDays(3)).revokedAt(revokedAt).replacedBy(replacedBy)
                .createdAt(NOW.minusDays(4))
                .build();
    }

    @Test
    void createSession_startsNewFamilyAndPurgesExpiredTokens() {
        IssuedRefreshToken first = service.createSession(10L);
        IssuedRefreshToken second = service.createSession(10L);

        assertThat(first.sessionId()).isNotBlank().isNotEqualTo(second.sessionId());
        assertThat(first.jti()).isNotBlank().isNotEqualTo(first.sessionId());
        assertThat(first.expiresAt()).isEqualTo(NOW.plusDays(7));
        ArgumentCaptor<RefreshTokenEntity> saved = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(repository, times(2)).save(saved.capture());
        RefreshTokenEntity row = saved.getAllValues().get(0);
        assertThat(row.getUserId()).isEqualTo(10L);
        assertThat(row.getJti()).isEqualTo(first.jti());
        assertThat(row.getFamilyId()).isEqualTo(first.sessionId());
        assertThat(row.getRevokedAt()).isNull();
        verify(repository, times(2)).deleteExpiredOfUser(10L, NOW.minusDays(1));
    }

    @Test
    void rotate_validToken_revokesItAndIssuesSuccessorInSameFamily() {
        RefreshTokenEntity current = token("jti-1", null, null);
        when(repository.findByJtiForUpdate("jti-1")).thenReturn(Optional.of(current));

        RotationResult result = service.rotate(10L, "jti-1");

        assertThat(result.isRotated()).isTrue();
        assertThat(result.issued().sessionId()).isEqualTo("family-1");
        assertThat(result.issued().jti()).isNotEqualTo("jti-1");
        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        assertThat(current.getReplacedBy()).isEqualTo(result.issued().jti());
        ArgumentCaptor<RefreshTokenEntity> saved = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(repository, times(2)).save(saved.capture());
        List<RefreshTokenEntity> rows = saved.getAllValues();
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.getJti()).isEqualTo(result.issued().jti());
            assertThat(row.getFamilyId()).isEqualTo("family-1");
            assertThat(row.getRevokedAt()).isNull();
        });
        verify(repository, never()).revokeFamily(anyString(), any());
    }

    @Test
    void rotate_reuseOfRotatedToken_revokesWholeFamily() {
        RefreshTokenEntity rotated = token("jti-1", NOW.minusMinutes(5), "jti-2");
        when(repository.findByJtiForUpdate("jti-1")).thenReturn(Optional.of(rotated));
        when(repository.revokeFamily("family-1", NOW)).thenReturn(1);

        RotationResult result = service.rotate(10L, "jti-1");

        assertThat(result.isRotated()).isFalse();
        assertThat(result.rejection()).isEqualTo(Rejection.REUSED);
        verify(repository).revokeFamily("family-1", NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_concurrentRefreshWithinGrace_issuesTokenInSameFamily() {
        RefreshTokenEntity rotated = token("jti-1", NOW.minusSeconds(3), "jti-2");
        when(repository.findByJtiForUpdate("jti-1")).thenReturn(Optional.of(rotated));
        when(repository.existsByFamilyIdAndRevokedAtIsNull("family-1")).thenReturn(true);

        RotationResult result = service.rotate(10L, "jti-1");

        assertThat(result.isRotated()).isTrue();
        assertThat(result.issued().sessionId()).isEqualTo("family-1");
        verify(repository, never()).revokeFamily(anyString(), any());
    }

    @Test
    void rotate_withinGraceButFamilyAlreadyRevoked_rejected() {
        RefreshTokenEntity rotated = token("jti-1", NOW.minusSeconds(3), "jti-2");
        when(repository.findByJtiForUpdate("jti-1")).thenReturn(Optional.of(rotated));
        when(repository.existsByFamilyIdAndRevokedAtIsNull("family-1")).thenReturn(false);

        assertThat(service.rotate(10L, "jti-1").rejection()).isEqualTo(Rejection.REVOKED);
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_graceDisabled_reuseIsAlwaysDetected() {
        properties.setRefreshReuseGrace(Duration.ZERO);
        RefreshTokenEntity rotated = token("jti-1", NOW.minusSeconds(1), "jti-2");
        when(repository.findByJtiForUpdate("jti-1")).thenReturn(Optional.of(rotated));

        assertThat(service.rotate(10L, "jti-1").rejection()).isEqualTo(Rejection.REUSED);
        verify(repository).revokeFamily("family-1", NOW);
    }

    @Test
    void rotate_tokenRevokedByLogout_rejectedAsRevoked() {
        RefreshTokenEntity loggedOut = token("jti-1", NOW.minusMinutes(1), null);
        when(repository.findByJtiForUpdate("jti-1")).thenReturn(Optional.of(loggedOut));

        assertThat(service.rotate(10L, "jti-1").rejection()).isEqualTo(Rejection.REVOKED);
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_unknownJtiOrOtherUser_rejected() {
        when(repository.findByJtiForUpdate("missing")).thenReturn(Optional.empty());
        when(repository.findByJtiForUpdate("jti-1")).thenReturn(Optional.of(token("jti-1", null, null)));

        assertThat(service.rotate(10L, "missing").rejection()).isEqualTo(Rejection.UNKNOWN);
        assertThat(service.rotate(99L, "jti-1").rejection()).isEqualTo(Rejection.UNKNOWN);
        assertThat(service.rotate(10L, null).rejection()).isEqualTo(Rejection.UNKNOWN);
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_expiredRow_rejected() {
        RefreshTokenEntity expired = token("jti-1", null, null);
        expired.setExpiresAt(NOW.minusSeconds(1));
        when(repository.findByJtiForUpdate("jti-1")).thenReturn(Optional.of(expired));

        assertThat(service.rotate(10L, "jti-1").rejection()).isEqualTo(Rejection.EXPIRED);
    }

    @Test
    void revokeSession_onlyTargetsFamilyOfUser() {
        service.revokeSession(10L, "family-1");
        service.revokeSession(10L, " ");

        verify(repository).revokeFamilyOfUser("family-1", 10L, NOW);
        verify(repository, times(1)).revokeFamilyOfUser(anyString(), any(), any());
    }

    @Test
    void revokeAllSessions_andIsSessionActive() {
        when(repository.existsByFamilyIdAndRevokedAtIsNull("family-1")).thenReturn(true);

        service.revokeAllSessions(10L);

        verify(repository).revokeAllOfUser(10L, NOW);
        assertThat(service.isSessionActive("family-1")).isTrue();
        assertThat(service.isSessionActive(null)).isFalse();
    }
}
