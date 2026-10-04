package com.education.base.audit;

import com.education.base.dto.request.ChangePasswordRequest;
import com.education.base.dto.request.LoginRequest;
import com.education.base.dto.request.VoidTransactionRequest;
import com.education.base.dto.response.AuthUserResponse;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.exception.OracleBusinessException;
import com.education.base.exception.UnauthorizedException;
import com.education.base.security.JwtClaims;
import com.education.base.security.JwtTokenService;
import com.education.base.security.TokenType;
import com.education.base.service.AuditService;
import com.education.base.service.AuthService;
import com.education.base.service.AuthTokens;
import com.education.base.service.PaymentService;
import com.education.base.service.RoleAdminService;
import com.education.base.service.TuitionFeeService;
import com.education.base.service.UserAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ghi nhật ký qua AOP: sự kiện đúng action / kết quả / đối tượng, không bao giờ chứa mật khẩu hay token, lỗi
 * nghiệp vụ được ném lại nguyên vẹn và lỗi ghi nhật ký không làm hỏng nghiệp vụ.
 */
class AuditTrailAspectTest {

    private static final String PASSWORD = "Sup3r-Secret!pw";
    private static final String REFRESH_TOKEN = "eyJhbGciOiJIUzI1NiJ9.refresh.signature";

    private AuditService auditService;
    private JwtTokenService jwtTokenService;
    private AuditTrailAspect aspect;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        auditService = mock(AuditService.class);
        jwtTokenService = mock(JwtTokenService.class);
        ObjectProvider<JwtTokenService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jwtTokenService);
        aspect = new AuditTrailAspect(auditService, provider);
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target, Class<T> type) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setInterfaces(type);
        factory.addAspect(aspect);
        return (T) factory.getProxy();
    }

    private AuditEvent recorded() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        return captor.getValue();
    }

    private static void assertNoSecrets(AuditEvent event, String... secrets) {
        String text = event.toString();
        for (String secret : secrets) {
            assertThat(text).doesNotContain(secret);
        }
    }

    // ---- AuthService ---------------------------------------------------------------------------

    @Test
    void loginSuccess_recordsActorFromIssuedSession_withoutPasswordOrTokens() {
        AuthService target = mock(AuthService.class);
        AuthTokens tokens = AuthTokens.builder()
                .accessToken("access-token-value")
                .refreshToken(REFRESH_TOKEN)
                .user(AuthUserResponse.builder().id(7L).username("thungan").build())
                .build();
        when(target.login(any())).thenReturn(tokens);

        AuthTokens result = proxy(target, AuthService.class).login(new LoginRequest("thungan", PASSWORD));

        assertThat(result).isSameAs(tokens);
        AuditEvent event = recorded();
        assertThat(event.getAction()).isEqualTo(AuditActions.LOGIN_SUCCESS);
        assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.getActorUserId()).isEqualTo(7L);
        assertThat(event.getActorUsername()).isEqualTo("thungan");
        assertThat(event.getResourceType()).isEqualTo(AuditActions.RESOURCE_USER);
        assertThat(event.getResourceId()).isEqualTo("7");
        assertNoSecrets(event, PASSWORD, REFRESH_TOKEN, "access-token-value");
    }

    @Test
    void loginFailure_recordsAttemptedUsernameAndRethrowsOriginalError() {
        AuthService target = mock(AuthService.class);
        UnauthorizedException error = new UnauthorizedException("INVALID_CREDENTIALS", "Sai tên đăng nhập");
        when(target.login(any())).thenThrow(error);

        assertThatThrownBy(() -> proxy(target, AuthService.class)
                .login(new LoginRequest("  hacker  ", PASSWORD)))
                .isSameAs(error);

        AuditEvent event = recorded();
        assertThat(event.getAction()).isEqualTo(AuditActions.LOGIN_FAILED);
        assertThat(event.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(event.getActorUserId()).isNull();
        assertThat(event.getActorUsername()).isEqualTo("hacker");
        assertThat(event.getDetails()).containsEntry("errorCode", "INVALID_CREDENTIALS");
        assertNoSecrets(event, PASSWORD);
    }

    @Test
    void changePassword_recordsUserButNeverThePasswords() {
        AuthService target = mock(AuthService.class);
        when(target.changePassword(anyLong(), any())).thenReturn(AuthTokens.builder().build());
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("Old-" + PASSWORD);
        request.setNewPassword(PASSWORD);

        proxy(target, AuthService.class).changePassword(5L, request);

        AuditEvent event = recorded();
        assertThat(event.getAction()).isEqualTo(AuditActions.PASSWORD_CHANGED);
        assertThat(event.getResourceId()).isEqualTo("5");
        assertNoSecrets(event, PASSWORD, "Old-" + PASSWORD);
    }

    @Test
    void logoutWithExpiredAccessToken_identifiesUserFromRefreshTokenWithoutLoggingIt() {
        AuthService target = mock(AuthService.class);
        when(jwtTokenService.parse(REFRESH_TOKEN, TokenType.REFRESH))
                .thenReturn(new JwtClaims(9L, "giaovien", 1, TokenType.REFRESH, Instant.now()));

        proxy(target, AuthService.class).logout(null, null, REFRESH_TOKEN);

        AuditEvent event = recorded();
        assertThat(event.getAction()).isEqualTo(AuditActions.LOGOUT);
        assertThat(event.getActorUserId()).isEqualTo(9L);
        assertThat(event.getActorUsername()).isEqualTo("giaovien");
        assertNoSecrets(event, REFRESH_TOKEN);
    }

    @Test
    void logoutWithoutAnyIdentity_isNotRecorded() {
        AuthService target = mock(AuthService.class);

        proxy(target, AuthService.class).logout(null, null, null);

        verify(target).logout(null, null, null);
        verify(auditService, never()).record(any());
    }

    @Test
    void refresh_onlyTokenReuseIsRecorded() {
        AuthService target = mock(AuthService.class);
        when(jwtTokenService.parse(REFRESH_TOKEN, TokenType.REFRESH))
                .thenReturn(new JwtClaims(3L, "ketoan", 1, TokenType.REFRESH, Instant.now()));
        AuthService proxy = proxy(target, AuthService.class);

        // Làm mới bình thường / lỗi thông thường: không ghi (tránh làm đầy nhật ký).
        when(target.refresh(any())).thenReturn(AuthTokens.builder().build());
        proxy.refresh(REFRESH_TOKEN);
        doThrow(new UnauthorizedException("REFRESH_TOKEN_EXPIRED", "hết hạn")).when(target).refresh(any());
        assertThatThrownBy(() -> proxy.refresh(REFRESH_TOKEN)).isInstanceOf(UnauthorizedException.class);
        verify(auditService, never()).record(any());

        doThrow(new UnauthorizedException(AuditTrailAspect.REFRESH_TOKEN_REUSED, "reuse")).when(target).refresh(any());
        assertThatThrownBy(() -> proxy.refresh(REFRESH_TOKEN)).isInstanceOf(UnauthorizedException.class);

        AuditEvent event = recorded();
        assertThat(event.getAction()).isEqualTo(AuditActions.TOKEN_REUSE_DETECTED);
        assertThat(event.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(event.getActorUserId()).isEqualTo(3L);
        assertNoSecrets(event, REFRESH_TOKEN);
    }

    @Test
    void readOnlyMethodsAreNotRecorded() {
        AuthService target = mock(AuthService.class);
        proxy(target, AuthService.class).me(null);
        UserAdminService users = mock(UserAdminService.class);
        proxy(users, UserAdminService.class).getById(1L);
        proxy(users, UserAdminService.class).search(null);

        verify(auditService, never()).record(any());
    }

    // ---- UserAdminService ----------------------------------------------------------------------

    @Test
    void resetPassword_neverRecordsTheNewPassword() {
        UserAdminService target = mock(UserAdminService.class);

        proxy(target, UserAdminService.class).resetPassword(12L, PASSWORD, 1L);

        AuditEvent event = recorded();
        assertThat(event.getAction()).isEqualTo(AuditActions.PASSWORD_RESET);
        assertThat(event.getResourceType()).isEqualTo(AuditActions.RESOURCE_USER);
        assertThat(event.getResourceId()).isEqualTo("12");
        assertThat(event.getDetails()).isEmpty();
        assertNoSecrets(event, PASSWORD);
    }

    @Test
    void changeStatus_mapsToLockOrUnlock() {
        UserAdminService target = mock(UserAdminService.class);
        UserAdminService proxy = proxy(target, UserAdminService.class);

        proxy.changeStatus(4L, false, 1L);
        proxy.changeStatus(4L, true, 1L);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(2)).record(captor.capture());
        assertThat(captor.getAllValues()).extracting(AuditEvent::getAction)
                .containsExactly(AuditActions.ACCOUNT_LOCKED, AuditActions.ACCOUNT_UNLOCKED);
        assertThat(captor.getAllValues()).extracting(AuditEvent::getResourceId).containsOnly("4");
    }

    @Test
    void assignRoles_recordsRoleIds() {
        UserAdminService target = mock(UserAdminService.class);

        proxy(target, UserAdminService.class).assignRoles(4L, List.of(1L, 2L));

        AuditEvent event = recorded();
        assertThat(event.getAction()).isEqualTo(AuditActions.USER_ROLES_CHANGED);
        assertThat(event.getDetails()).containsEntry("roleIds", List.of(1L, 2L));
    }

    // ---- RoleAdminService / PaymentService / TuitionFeeService -----------------------------------

    @Test
    void roleDelete_recorded() {
        RoleAdminService target = mock(RoleAdminService.class);

        proxy(target, RoleAdminService.class).delete(8L);

        AuditEvent event = recorded();
        assertThat(event.getAction()).isEqualTo(AuditActions.ROLE_DELETED);
        assertThat(event.getResourceId()).isEqualTo("8");
    }

    @Test
    void voidTransaction_recordsReasonAndAmount() {
        PaymentService target = mock(PaymentService.class);
        when(target.voidTransaction(eq(21L), any())).thenReturn(PaymentTransactionDto.builder()
                .id(21L).amount(new BigDecimal("1500000")).receiptNo("PT-0001").tuitionFeeId(3L).build());

        proxy(target, PaymentService.class).voidTransaction(21L, new VoidTransactionRequest("Thu nhầm"));

        AuditEvent event = recorded();
        assertThat(event.getAction()).isEqualTo(AuditActions.PAYMENT_VOIDED);
        assertThat(event.getResourceType()).isEqualTo(AuditActions.RESOURCE_PAYMENT_TRANSACTION);
        assertThat(event.getResourceId()).isEqualTo("21");
        assertThat(event.getDetails())
                .containsEntry("reason", "Thu nhầm")
                .containsEntry("amount", new BigDecimal("1500000"))
                .containsEntry("receiptNo", "PT-0001")
                .containsEntry("tuitionFeeId", 3L);
    }

    @Test
    void feeDeleteFailure_recordsFailureAndRethrows() {
        TuitionFeeService target = mock(TuitionFeeService.class);
        OracleBusinessException error = new OracleBusinessException("FEE_NOT_DELETABLE", "Không xóa được");
        doThrow(error).when(target).delete(30L);

        assertThatThrownBy(() -> proxy(target, TuitionFeeService.class).delete(30L)).isSameAs(error);

        AuditEvent event = recorded();
        assertThat(event.getAction()).isEqualTo(AuditActions.FEE_DELETED);
        assertThat(event.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(event.getResourceType()).isEqualTo(AuditActions.RESOURCE_TUITION_FEE);
        assertThat(event.getResourceId()).isEqualTo("30");
        assertThat(event.getDetails()).containsEntry("errorCode", "FEE_NOT_DELETABLE");
    }

    @Test
    void auditFailure_neverBreaksTheBusinessCall() {
        RoleAdminService target = mock(RoleAdminService.class);
        doThrow(new IllegalStateException("audit down")).when(auditService).record(any());

        proxy(target, RoleAdminService.class).delete(8L);

        verify(target).delete(8L);
    }

    @Test
    void longErrorMessagesAreTruncated() {
        Map<String, Object> details = AuditTrailAspect.errorDetails(new IllegalStateException("x".repeat(1000)));
        assertThat(details).containsEntry("errorCode", "IllegalStateException");
        assertThat((String) details.get("message")).hasSize(303).endsWith("...");
    }

    // ---- Bảng ánh xạ ---------------------------------------------------------------------------

    @Test
    void everySpecKeyMatchesARealInterfaceMethod() throws ClassNotFoundException {
        Set<String> keys = aspect.registeredSpecs().keySet();
        for (String key : keys) {
            String[] parts = key.split("#");
            Class<?> type = Class.forName("com.education.base.service." + parts[0]);
            assertThat(type.isInterface()).isTrue();
            Set<String> methods = Arrays.stream(type.getMethods()).map(Method::getName).collect(Collectors.toSet());
            assertThat(methods).as("%s không còn method %s", parts[0], parts[1]).contains(parts[1]);
        }
    }

    @Test
    void coversAllSensitiveOperationsOfPhase0() {
        assertThat(aspect.registeredSpecs().keySet()).contains(
                "AuthService#login", "AuthService#logout", "AuthService#changePassword", "AuthService#refresh",
                "UserAdminService#create", "UserAdminService#update", "UserAdminService#delete",
                "UserAdminService#changeStatus", "UserAdminService#resetPassword", "UserAdminService#assignRoles",
                "RoleAdminService#create", "RoleAdminService#update", "RoleAdminService#delete",
                "RoleAdminService#updatePermissions",
                "PaymentService#confirmPayment", "PaymentService#voidTransaction", "PaymentService#refundTransaction",
                "TuitionFeeService#cancel", "TuitionFeeService#delete");
        // confirmPayment của TuitionFeeService ủy quyền sang PaymentService: không ghi 2 lần.
        assertThat(aspect.registeredSpecs()).doesNotContainKey("TuitionFeeService#confirmPayment");
    }
}
