package com.education.base.audit;

import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.dto.request.LoginRequest;
import com.education.base.dto.response.AuthUserResponse;
import com.education.base.exception.UnauthorizedException;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.service.AuditService;
import com.education.base.service.AuthService;
import com.education.base.service.AuthTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

/**
 * Aspect được Spring áp dụng cho bean service thật (proxy CGLIB như Spring Boot) và bọc NGOÀI transaction của
 * service: lúc ghi nhật ký, transaction nghiệp vụ đã commit / rollback xong.
 */
@SpringJUnitConfig(AuditTrailAspectWiringTest.Config.class)
class AuditTrailAspectWiringTest {

    @Autowired
    private AuthService authService;
    @Autowired
    private AuditService auditService;
    @Autowired
    private RecordingTransactionManager transactionManager;

    private final List<String> transactionStateAtRecord = new ArrayList<>();

    @BeforeEach
    void setUp() {
        reset(auditService);
        transactionStateAtRecord.clear();
        transactionManager.events.clear();
        doAnswer(invocation -> {
            AuditEvent event = invocation.getArgument(0);
            transactionStateAtRecord.add(event.getAction() + ":"
                    + TransactionSynchronizationManager.isActualTransactionActive() + ":"
                    + String.join(",", transactionManager.events));
            return null;
        }).when(auditService).record(any());
    }

    @Test
    void successIsRecordedAfterTheServiceTransactionCommitted() {
        authService.changePassword(1L, new ChangePasswordRequest());

        assertThat(transactionStateAtRecord).containsExactly("PASSWORD_CHANGED:false:begin,commit");
    }

    @Test
    void failureIsRecordedAfterRollback_andErrorPropagates() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("x", "wrong")))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(transactionStateAtRecord).containsExactly("LOGIN_FAILED:false:begin,rollback");
    }

    @Test
    void unmappedMethodsAreNotRecorded() {
        authService.me(null);

        assertThat(transactionStateAtRecord).isEmpty();
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import(AuditTrailAspect.class)
    static class Config {

        @Bean
        AuditService auditService() {
            return Mockito.mock(AuditService.class);
        }

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        AuthService authService() {
            return new FakeAuthService();
        }
    }

    static class FakeAuthService implements AuthService {

        @Override
        @Transactional
        public AuthTokens login(LoginRequest request) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Sai mật khẩu");
        }

        @Override
        @Transactional
        public AuthTokens refresh(String refreshToken) {
            return AuthTokens.builder().build();
        }

        @Override
        @Transactional
        public void logout(Long userId, String sessionId, String refreshToken) {
        }

        @Override
        public AuthUserResponse me(AuthUserPrincipal principal) {
            return null;
        }

        @Override
        @Transactional
        public AuthTokens changePassword(Long userId, ChangePasswordRequest request) {
            return AuthTokens.builder().build();
        }
    }

    /** Transaction manager tối thiểu: ghi lại begin / commit / rollback. */
    static class RecordingTransactionManager extends AbstractPlatformTransactionManager
            implements PlatformTransactionManager {

        final List<String> events = new ArrayList<>();

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            events.add("begin");
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            events.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            events.add("rollback");
        }
    }
}
