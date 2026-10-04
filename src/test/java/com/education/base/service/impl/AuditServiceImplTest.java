package com.education.base.service.impl;

import com.education.base.audit.AuditActions;
import com.education.base.audit.AuditEvent;
import com.education.base.audit.AuditResult;
import com.education.base.audit.AuditUserTypeResolver;
import com.education.base.entity.AuditLogEntity;
import com.education.base.repository.AuditLogRepository;
import com.education.base.security.AuthUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-04T03:00:00Z"), ZONE);

    private AuditLogRepository repository;
    private PlatformTransactionManager transactionManager;
    private AuditUserTypeResolver resolver;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        transactionManager = mock(PlatformTransactionManager.class);
        resolver = null;
        request = new MockHttpServletRequest("POST", "/api/v1/admin/users/5/reset-password");
        request.setRemoteAddr("203.0.113.7");
        request.addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0)");
        request.addHeader(HttpHeaders.COOKIE, "edu_rt=refresh-cookie-secret");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-secret");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @SuppressWarnings("unchecked")
    private AuditServiceImpl service(boolean enabled) {
        ObjectProvider<AuditUserTypeResolver> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenAnswer(invocation -> resolver);
        return new AuditServiceImpl(repository, transactionManager, new ObjectMapper(), CLOCK, provider, enabled);
    }

    private static void login(long id, String username) {
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .id(id).username(username).fullName(username)
                .roles(List.of("ROLE_ADMIN")).permissions(Set.of()).build();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
    }

    private AuditLogEntity saved() {
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void record_capturesActorRequestAndTime_inOwnTransaction() {
        login(1L, "admin");
        resolver = principal -> "staff";

        service(true).record(AuditEvent.builder()
                .action(AuditActions.PASSWORD_RESET)
                .resource(AuditActions.RESOURCE_USER, 5L)
                .detail("reason", "Quên mật khẩu")
                .build());

        AuditLogEntity entity = saved();
        assertThat(entity.getEventTime()).isEqualTo(LocalDateTime.of(2026, 10, 4, 10, 0));
        assertThat(entity.getUserId()).isEqualTo(1L);
        assertThat(entity.getUsername()).isEqualTo("admin");
        assertThat(entity.getUserType()).isEqualTo("STAFF");
        assertThat(entity.getAction()).isEqualTo("PASSWORD_RESET");
        assertThat(entity.getResourceType()).isEqualTo("USER");
        assertThat(entity.getResourceId()).isEqualTo("5");
        assertThat(entity.getIp()).isEqualTo("203.0.113.7");
        assertThat(entity.getUserAgent()).isEqualTo("Mozilla/5.0 (Windows NT 10.0)");
        assertThat(entity.getResult()).isEqualTo("SUCCESS");
        assertThat(entity.getDetail()).isEqualTo("{\"reason\":\"Quên mật khẩu\"}");
        verify(transactionManager).getTransaction(argThat(definition ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @Test
    void record_neverPersistsPasswordsTokensOrCookies() {
        login(1L, "admin");

        service(true).record(AuditEvent.builder()
                .action("PASSWORD_CHANGED")
                .detail("password", "P@ssw0rd!")
                .detail("newPassword", "N3w-P@ss")
                .detail("refreshToken", "rt-value")
                .detail("Cookie", "edu_rt=x")
                .detail("authorization", "Bearer y")
                .detail("nested", Map.of("clientSecret", "s3cr3t", "ok", 1))
                .build());

        AuditLogEntity entity = saved();
        String everything = entity.toString() + entity.getDetail();
        assertThat(everything).doesNotContain("P@ssw0rd!", "N3w-P@ss", "rt-value", "edu_rt", "Bearer",
                "s3cr3t", "refresh-cookie-secret", "access-secret");
        assertThat(entity.getDetail()).contains("\"password\":\"[REDACTED]\"").contains("\"ok\":1");
    }

    @Test
    void actorOverride_isUsedAsIs_withoutMixingInTheCurrentPrincipal() {
        login(1L, "admin");
        resolver = principal -> "STAFF";

        service(true).record(AuditEvent.builder()
                .action(AuditActions.LOGIN_FAILED)
                .result(AuditResult.FAILURE)
                .actor(null, "khongtontai")
                .build());

        AuditLogEntity entity = saved();
        assertThat(entity.getUserId()).isNull();
        assertThat(entity.getUsername()).isEqualTo("khongtontai");
        assertThat(entity.getUserType()).isNull();
        assertThat(entity.getResult()).isEqualTo("FAILURE");
        assertThat(entity.getDetail()).isNull();
    }

    @Test
    void anonymousOutsideRequest_isStillRecorded() {
        RequestContextHolder.resetRequestAttributes();

        service(true).success("SCHEDULED_JOB", null, null, null);

        AuditLogEntity entity = saved();
        assertThat(entity.getUserId()).isNull();
        assertThat(entity.getUsername()).isNull();
        assertThat(entity.getIp()).isNull();
        assertThat(entity.getUserAgent()).isNull();
    }

    @Test
    void columnsAreTruncatedToOracleByteLimits() {
        request.removeHeader(HttpHeaders.USER_AGENT);
        request.addHeader(HttpHeaders.USER_AGENT, "ư".repeat(400));

        service(true).record(AuditEvent.builder()
                .action("x".repeat(80))
                .actor(null, "đ".repeat(200))
                .resource("USER", "9".repeat(300))
                .detail("note", "ệ".repeat(400))
                .detail("note2", "ệ".repeat(400))
                .detail("note3", "ệ".repeat(400))
                .detail("note4", "ệ".repeat(400))
                .build());

        AuditLogEntity entity = saved();
        assertThat(bytes(entity.getAction())).isLessThanOrEqualTo(AuditLogEntity.ACTION_MAX_BYTES);
        assertThat(bytes(entity.getUsername())).isLessThanOrEqualTo(AuditLogEntity.USERNAME_MAX_BYTES);
        assertThat(bytes(entity.getResourceId())).isLessThanOrEqualTo(AuditLogEntity.RESOURCE_ID_MAX_BYTES);
        assertThat(bytes(entity.getUserAgent())).isLessThanOrEqualTo(AuditLogEntity.USER_AGENT_MAX_BYTES);
        assertThat(bytes(entity.getDetail())).isGreaterThan(3000).isLessThanOrEqualTo(AuditLogEntity.DETAIL_MAX_BYTES);
        assertThat(entity.getUsername()).matches("đ+");
    }

    @Test
    void repositoryFailure_isSwallowed() {
        when(repository.save(any())).thenThrow(new DataAccessResourceFailureException("ORA-00942"));
        AuditServiceImpl service = service(true);

        assertThatCode(() -> {
            service.success(AuditActions.ROLE_DELETED, "ROLE", 1, null);
            service.failure(AuditActions.ROLE_DELETED, "ROLE", 1, null);
        }).doesNotThrowAnyException();
        verify(repository, times(2)).save(any());
    }

    @Test
    void transactionFailure_isSwallowed() {
        when(transactionManager.getTransaction(any())).thenThrow(new CannotCreateTransactionException("db down"));

        assertThatCode(() -> service(true).success(AuditActions.LOGOUT, "USER", 1, null))
                .doesNotThrowAnyException();
    }

    @Test
    void brokenUserTypeResolver_doesNotPreventRecording() {
        login(2L, "gv");
        resolver = principal -> {
            throw new IllegalStateException("boom");
        };

        service(true).success(AuditActions.LOGOUT, "USER", 2, null);

        assertThat(saved().getUserType()).isNull();
    }

    @Test
    void disabledOrIncompleteEvents_areIgnored() {
        service(false).success(AuditActions.LOGOUT, "USER", 1, null);
        service(true).record(null);
        service(true).record(AuditEvent.builder().action("  ").build());

        verify(repository, never()).save(any());
    }

    @Test
    void successInsideTransaction_isWrittenOnlyAfterCommit_failureImmediately() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        AuditServiceImpl service = service(true);

        service.success(AuditActions.FEE_CANCELLED, "TUITION_FEE", 3, Map.of("reason", "Học sinh nghỉ"));
        verify(repository, never()).save(any());

        service.failure(AuditActions.FEE_DELETED, "TUITION_FEE", 4, null);
        verify(repository, times(1)).save(argThat(entity -> "FEE_DELETED".equals(entity.getAction())));

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        verify(repository).save(argThat(entity -> "FEE_CANCELLED".equals(entity.getAction())
                && entity.getDetail().contains("Học sinh nghỉ")));
    }

    @Test
    void successInsideRolledBackTransaction_isNeverWritten() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        service(true).success(AuditActions.PAYMENT_VOIDED, "PAYMENT_TRANSACTION", 3, null);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(repository, never()).save(any());
    }

    private static int bytes(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
