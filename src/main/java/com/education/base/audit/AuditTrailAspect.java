package com.education.base.audit;

import com.education.base.dto.request.ConfirmPaymentRequest;
import com.education.base.dto.request.LoginRequest;
import com.education.base.dto.request.RefundTransactionRequest;
import com.education.base.dto.request.RolePermissionUpdateRequest;
import com.education.base.dto.request.TuitionFeeCancelRequest;
import com.education.base.dto.request.VoidTransactionRequest;
import com.education.base.dto.response.AuthUserResponse;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.dto.response.RoleResponseDto;
import com.education.base.dto.response.RoleSummaryDto;
import com.education.base.dto.response.TuitionFeeDetailResponse;
import com.education.base.dto.response.UserResponseDto;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.exception.UnauthorizedException;
import com.education.base.security.JwtClaims;
import com.education.base.security.JwtTokenService;
import com.education.base.security.TokenType;
import com.education.base.service.AuditService;
import com.education.base.service.AuthTokens;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Ghi nhật ký tự động cho các nghiệp vụ nhạy cảm mà KHÔNG sửa mã nghiệp vụ (giảm xung đột khi gộp nhánh):
 * bọc các method của {@code AuthService}, {@code UserAdminService}, {@code RoleAdminService},
 * {@code PaymentService}, {@code TuitionFeeService} theo bảng {@link #specs()} (khóa = {@code Interface#method}).
 * <p>
 * Thứ tự: aspect này bọc NGOÀI transaction của service (order nhỏ hơn {@code TransactionInterceptor}), nên kết
 * quả {@code SUCCESS} chỉ được ghi sau khi transaction nghiệp vụ đã commit; lỗi của nghiệp vụ được ghi
 * {@code FAILURE} rồi ném lại nguyên vẹn. Lỗi trong chính aspect / {@link AuditService} bị nuốt (chỉ log).
 * <p>
 * Không bao giờ đưa mật khẩu ({@code LoginRequest.password}, {@code resetPassword(newPassword)},
 * {@code ChangePasswordRequest}), token hay cookie vào chi tiết. {@code AuditTrailAspectTest} kiểm tra mọi khóa
 * trong bảng vẫn khớp một method có thật của interface (đổi tên method sẽ làm test đỏ).
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class AuditTrailAspect {

    static final String AUTH = "AuthService";
    static final String USER_ADMIN = "UserAdminService";
    static final String ROLE_ADMIN = "RoleAdminService";
    static final String PAYMENT = "PaymentService";
    static final String TUITION_FEE = "TuitionFeeService";

    static final String REFRESH_TOKEN_REUSED = "REFRESH_TOKEN_REUSED";
    private static final int MAX_ERROR_MESSAGE_CHARS = 300;

    private final AuditService auditService;
    private final ObjectProvider<JwtTokenService> jwtTokenService;
    private final Map<String, Spec> specs = specs();

    public AuditTrailAspect(AuditService auditService, ObjectProvider<JwtTokenService> jwtTokenService) {
        this.auditService = auditService;
        this.jwtTokenService = jwtTokenService;
    }

    @Around("execution(* com.education.base.service.AuthService.*(..))")
    public Object auditAuth(ProceedingJoinPoint pjp) throws Throwable {
        return audit(AUTH, pjp);
    }

    @Around("execution(* com.education.base.service.UserAdminService.*(..))")
    public Object auditUserAdmin(ProceedingJoinPoint pjp) throws Throwable {
        return audit(USER_ADMIN, pjp);
    }

    @Around("execution(* com.education.base.service.RoleAdminService.*(..))")
    public Object auditRoleAdmin(ProceedingJoinPoint pjp) throws Throwable {
        return audit(ROLE_ADMIN, pjp);
    }

    @Around("execution(* com.education.base.service.PaymentService.*(..))")
    public Object auditPayment(ProceedingJoinPoint pjp) throws Throwable {
        return audit(PAYMENT, pjp);
    }

    @Around("execution(* com.education.base.service.TuitionFeeService.*(..))")
    public Object auditTuitionFee(ProceedingJoinPoint pjp) throws Throwable {
        return audit(TUITION_FEE, pjp);
    }

    /** Các khóa {@code Interface#method} được ghi nhật ký (phục vụ test). */
    Map<String, Spec> registeredSpecs() {
        return specs;
    }

    private Object audit(String service, ProceedingJoinPoint pjp) throws Throwable {
        Spec spec = specs.get(service + "#" + pjp.getSignature().getName());
        if (spec == null) {
            return pjp.proceed();
        }
        Object[] args = pjp.getArgs();
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable error) {
            emit(() -> spec.onFailure(this, args, error));
            throw error;
        }
        emit(() -> spec.onSuccess(this, args, result));
        return result;
    }

    private void emit(Supplier<Builder> factory) {
        try {
            Builder builder = factory.get();
            AuditEvent event = builder == null ? null : builder.build();
            if (event != null) {
                auditService.record(event);
            }
        } catch (RuntimeException ex) {
            log.warn("Không dựng được sự kiện nhật ký: {}", ex.toString());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Bảng ánh xạ method -> sự kiện
    // ---------------------------------------------------------------------------------------------

    private static Map<String, Spec> specs() {
        Map<String, Spec> m = new LinkedHashMap<>();

        // ---- AuthService --------------------------------------------------------------------
        m.put(AUTH + "#login", new Spec(
                (a, args, result) -> () -> {
                    AuthUserResponse user = result instanceof AuthTokens tokens ? tokens.getUser() : null;
                    return AuditEvent.builder()
                            .action(AuditActions.LOGIN_SUCCESS)
                            .resource(AuditActions.RESOURCE_USER, user == null ? null : user.getId())
                            .actor(user == null ? null : user.getId(), user == null ? null : user.getUsername())
                            // Chưa có SecurityContext lúc đăng nhập: lấy loại tài khoản từ kết quả (V17_1).
                            .actorUserType(user == null ? null : user.getUserType())
                            .build();
                },
                (a, args, error) -> () -> {
                    String username = args.length > 0 && args[0] instanceof LoginRequest login
                            ? login.getUsername() : null;
                    return AuditEvent.builder()
                            .action(AuditActions.LOGIN_FAILED)
                            .result(AuditResult.FAILURE)
                            .resource(AuditActions.RESOURCE_USER, null)
                            .actor(null, username == null ? null : username.trim())
                            .details(errorDetails(error))
                            .build();
                }));
        m.put(AUTH + "#logout", new Spec(
                (a, args, result) -> () -> {
                    Long userId = arg(args, 0, Long.class);
                    if (userId != null) {
                        // Còn access token: người thao tác = người dùng đăng nhập hiện tại.
                        return AuditEvent.builder()
                                .action(AuditActions.LOGOUT)
                                .resource(AuditActions.RESOURCE_USER, userId)
                                .build();
                    }
                    // Access token đã hết hạn: lấy người dùng từ refresh token (đã kiểm tra chữ ký).
                    JwtClaims claims = a.refreshClaims(arg(args, 2, String.class));
                    if (claims == null || claims.userId() == null) {
                        return null;
                    }
                    return AuditEvent.builder()
                            .action(AuditActions.LOGOUT)
                            .resource(AuditActions.RESOURCE_USER, claims.userId())
                            .actor(claims.userId(), claims.username())
                            .build();
                },
                null));
        m.put(AUTH + "#refresh", new Spec(
                null,
                (a, args, error) -> () -> {
                    if (!(error instanceof UnauthorizedException ex) || !REFRESH_TOKEN_REUSED.equals(ex.getErrorCode())) {
                        return null;
                    }
                    JwtClaims claims = a.refreshClaims(arg(args, 0, String.class));
                    Long userId = claims == null ? null : claims.userId();
                    return AuditEvent.builder()
                            .action(AuditActions.TOKEN_REUSE_DETECTED)
                            .result(AuditResult.FAILURE)
                            .resource(AuditActions.RESOURCE_USER, userId)
                            .actor(userId, claims == null ? null : claims.username())
                            .details(errorDetails(error))
                            .build();
                }));
        m.put(AUTH + "#changePassword", simple(AuditActions.PASSWORD_CHANGED, AuditActions.RESOURCE_USER, 0, null));

        // ---- UserAdminService ---------------------------------------------------------------
        m.put(USER_ADMIN + "#create", new Spec(
                (a, args, result) -> () -> {
                    UserResponseDto user = result instanceof UserResponseDto dto ? dto : null;
                    Map<String, Object> details = new LinkedHashMap<>();
                    if (user != null) {
                        details.put("username", user.getUsername());
                        details.put("roles", roleCodes(user.getRoles()));
                    }
                    return AuditEvent.builder()
                            .action(AuditActions.ACCOUNT_CREATED)
                            .resource(AuditActions.RESOURCE_USER, user == null ? null : user.getId())
                            .details(details)
                            .build();
                },
                failure(AuditActions.ACCOUNT_CREATED, AuditActions.RESOURCE_USER, -1)));
        m.put(USER_ADMIN + "#update", simple(AuditActions.ACCOUNT_UPDATED, AuditActions.RESOURCE_USER, 0, null));
        m.put(USER_ADMIN + "#delete", simple(AuditActions.ACCOUNT_DELETED, AuditActions.RESOURCE_USER, 0, null));
        m.put(USER_ADMIN + "#changeStatus", new Spec(
                (a, args, result) -> () -> AuditEvent.builder()
                        .action(statusAction(args))
                        .resource(AuditActions.RESOURCE_USER, arg(args, 0, Long.class))
                        .details(result instanceof UserResponseDto dto
                                ? mapOf("username", dto.getUsername(), "status", dto.getStatus()) : Map.of())
                        .build(),
                (a, args, error) -> () -> AuditEvent.builder()
                        .action(statusAction(args))
                        .result(AuditResult.FAILURE)
                        .resource(AuditActions.RESOURCE_USER, arg(args, 0, Long.class))
                        .details(errorDetails(error))
                        .build()));
        // resetPassword(id, newPassword, currentUserId): newPassword KHÔNG BAO GIỜ được ghi.
        m.put(USER_ADMIN + "#resetPassword", simple(AuditActions.PASSWORD_RESET, AuditActions.RESOURCE_USER, 0, null));
        m.put(USER_ADMIN + "#assignRoles", simple(AuditActions.USER_ROLES_CHANGED, AuditActions.RESOURCE_USER, 0,
                (args, result) -> mapOf(
                        "roleIds", arg(args, 1, List.class),
                        "roles", result instanceof UserResponseDto dto ? roleCodes(dto.getRoles()) : null)));

        // ---- RoleAdminService ---------------------------------------------------------------
        m.put(ROLE_ADMIN + "#create", new Spec(
                (a, args, result) -> () -> {
                    RoleResponseDto role = result instanceof RoleResponseDto dto ? dto : null;
                    return AuditEvent.builder()
                            .action(AuditActions.ROLE_CREATED)
                            .resource(AuditActions.RESOURCE_ROLE, role == null ? null : role.getId())
                            .details(role == null ? Map.of() : mapOf("roleCode", role.getRoleCode()))
                            .build();
                },
                failure(AuditActions.ROLE_CREATED, AuditActions.RESOURCE_ROLE, -1)));
        m.put(ROLE_ADMIN + "#update", simple(AuditActions.ROLE_UPDATED, AuditActions.RESOURCE_ROLE, 0,
                (args, result) -> result instanceof RoleResponseDto dto
                        ? mapOf("roleCode", dto.getRoleCode(), "status", dto.getStatus()) : Map.of()));
        m.put(ROLE_ADMIN + "#delete", simple(AuditActions.ROLE_DELETED, AuditActions.RESOURCE_ROLE, 0, null));
        m.put(ROLE_ADMIN + "#updatePermissions", simple(AuditActions.ROLE_PERMISSIONS_CHANGED,
                AuditActions.RESOURCE_ROLE, 0,
                (args, result) -> mapOf("permissions",
                        permissionSummary(arg(args, 1, RolePermissionUpdateRequest.class)))));

        // ---- PaymentService (TuitionFeeService#confirmPayment ủy quyền sang đây: chỉ ghi một lần) ----
        m.put(PAYMENT + "#confirmPayment", new Spec(
                (a, args, result) -> () -> {
                    PaymentTransactionDto tx = result instanceof PaymentTransactionDto dto ? dto : null;
                    ConfirmPaymentRequest request = arg(args, 1, ConfirmPaymentRequest.class);
                    return AuditEvent.builder()
                            .action(AuditActions.PAYMENT_CONFIRMED)
                            .resource(AuditActions.RESOURCE_PAYMENT_TRANSACTION, tx == null ? null : tx.getId())
                            .details(mapOf(
                                    "tuitionFeeId", arg(args, 0, Long.class),
                                    "amount", tx == null ? null : tx.getAmount(),
                                    "paymentMethod", tx != null ? tx.getPaymentMethod()
                                            : request == null ? null : request.getPaymentMethod(),
                                    "receiptNo", tx == null ? null : tx.getReceiptNo()))
                            .build();
                },
                failure(AuditActions.PAYMENT_CONFIRMED, AuditActions.RESOURCE_TUITION_FEE, 0)));
        m.put(PAYMENT + "#voidTransaction", simple(AuditActions.PAYMENT_VOIDED,
                AuditActions.RESOURCE_PAYMENT_TRANSACTION, 0,
                (args, result) -> {
                    VoidTransactionRequest request = arg(args, 1, VoidTransactionRequest.class);
                    PaymentTransactionDto tx = result instanceof PaymentTransactionDto dto ? dto : null;
                    return mapOf(
                            "reason", request == null ? null : request.getReason(),
                            "amount", tx == null ? null : tx.getAmount(),
                            "receiptNo", tx == null ? null : tx.getReceiptNo(),
                            "tuitionFeeId", tx == null ? null : tx.getTuitionFeeId());
                }));
        m.put(PAYMENT + "#refundTransaction", simple(AuditActions.PAYMENT_REFUNDED,
                AuditActions.RESOURCE_PAYMENT_TRANSACTION, 0,
                (args, result) -> {
                    RefundTransactionRequest request = arg(args, 1, RefundTransactionRequest.class);
                    PaymentTransactionDto refund = result instanceof PaymentTransactionDto dto ? dto : null;
                    return mapOf(
                            "refundTransactionId", refund == null ? null : refund.getId(),
                            "amount", request == null ? null : request.getAmount(),
                            "method", request == null ? null : request.getMethod(),
                            "reason", request == null ? null : request.getReason(),
                            "receiptNo", refund == null ? null : refund.getReceiptNo());
                }));

        // ---- TuitionFeeService --------------------------------------------------------------
        m.put(TUITION_FEE + "#cancel", simple(AuditActions.FEE_CANCELLED, AuditActions.RESOURCE_TUITION_FEE, 0,
                (args, result) -> {
                    TuitionFeeCancelRequest request = arg(args, 1, TuitionFeeCancelRequest.class);
                    TuitionFeeDetailResponse fee = result instanceof TuitionFeeDetailResponse dto ? dto : null;
                    return mapOf(
                            "reason", request == null ? null : request.getReason(),
                            "feeCode", fee == null ? null : fee.getFeeCode(),
                            "studentId", fee == null ? null : fee.getStudentId());
                }));
        m.put(TUITION_FEE + "#delete", simple(AuditActions.FEE_DELETED, AuditActions.RESOURCE_TUITION_FEE, 0, null));
        return Map.copyOf(m);
    }

    // ---------------------------------------------------------------------------------------------
    // Tiện ích
    // ---------------------------------------------------------------------------------------------

    /** Thành công: sự kiện {@code action} trên {@code args[idArg]}; thất bại: cùng action, {@code FAILURE}. */
    private static Spec simple(String action, String resourceType, int idArg, DetailMapper details) {
        return new Spec(
                (a, args, result) -> () -> AuditEvent.builder()
                        .action(action)
                        .resource(resourceType, idArg < 0 ? null : arg(args, idArg, Object.class))
                        .details(details == null ? Map.of() : details.map(args, result))
                        .build(),
                failure(action, resourceType, idArg));
    }

    private static FailureMapper failure(String action, String resourceType, int idArg) {
        return (a, args, error) -> () -> AuditEvent.builder()
                .action(action)
                .result(AuditResult.FAILURE)
                .resource(resourceType, idArg < 0 ? null : arg(args, idArg, Object.class))
                .details(errorDetails(error))
                .build();
    }

    private static String statusAction(Object[] args) {
        Boolean active = arg(args, 1, Boolean.class);
        return Boolean.TRUE.equals(active) ? AuditActions.ACCOUNT_UNLOCKED : AuditActions.ACCOUNT_LOCKED;
    }

    static Map<String, Object> errorDetails(Throwable error) {
        String code;
        if (error instanceof UnauthorizedException ex) {
            code = ex.getErrorCode();
        } else if (error instanceof ForbiddenException ex) {
            code = ex.getErrorCode();
        } else if (error instanceof OracleBusinessException ex) {
            code = ex.getErrorCode();
        } else {
            code = error == null ? null : error.getClass().getSimpleName();
        }
        String message = error == null ? null : error.getMessage();
        if (message != null && message.length() > MAX_ERROR_MESSAGE_CHARS) {
            message = message.substring(0, MAX_ERROR_MESSAGE_CHARS) + "...";
        }
        return mapOf("errorCode", code, "message", message);
    }

    private static List<String> roleCodes(List<RoleSummaryDto> roles) {
        return roles == null ? List.of() : roles.stream().map(RoleSummaryDto::getRoleCode).toList();
    }

    /** {@code ["12:VIEW|CREATE", ...]} - gọn để vừa cột DETAIL. */
    private static List<String> permissionSummary(RolePermissionUpdateRequest request) {
        List<String> out = new ArrayList<>();
        if (request == null || request.getPermissions() == null) {
            return out;
        }
        for (RolePermissionUpdateRequest.MenuPermission permission : request.getPermissions()) {
            if (permission != null) {
                out.add(permission.getMenuId() + ":" + String.join("|",
                        permission.getFunctions() == null ? List.of() : permission.getFunctions()));
            }
        }
        return out;
    }

    /** Map giữ thứ tự, bỏ giá trị {@code null}. */
    static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                out.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return out;
    }

    private static <T> T arg(Object[] args, int index, Class<T> type) {
        if (args == null || index < 0 || index >= args.length || !type.isInstance(args[index])) {
            return null;
        }
        return type.cast(args[index]);
    }

    /** Claim của refresh token có chữ ký hợp lệ (chỉ để biết người dùng; token không bao giờ được ghi). */
    private JwtClaims refreshClaims(String refreshToken) {
        JwtTokenService service = jwtTokenService.getIfAvailable();
        if (service == null || refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        try {
            return service.parse(refreshToken, TokenType.REFRESH);
        } catch (RuntimeException ex) {
            // InvalidTokenException (chữ ký sai / hết hạn...) hoặc lỗi khác: không xác định được người dùng.
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Kiểu nội bộ
    // ---------------------------------------------------------------------------------------------

    /** Dựng sự kiện trễ (lỗi khi dựng bị nuốt trong {@link #emit}); trả {@code null} = không ghi. */
    @FunctionalInterface
    interface Builder {
        AuditEvent build();
    }

    @FunctionalInterface
    interface SuccessMapper {
        Builder map(AuditTrailAspect aspect, Object[] args, Object result);
    }

    @FunctionalInterface
    interface FailureMapper {
        Builder map(AuditTrailAspect aspect, Object[] args, Throwable error);
    }

    @FunctionalInterface
    interface DetailMapper {
        Map<String, ?> map(Object[] args, Object result);
    }

    record Spec(SuccessMapper success, FailureMapper failure) {

        Builder onSuccess(AuditTrailAspect aspect, Object[] args, Object result) {
            return success == null ? null : success.map(aspect, args, result);
        }

        Builder onFailure(AuditTrailAspect aspect, Object[] args, Throwable error) {
            return failure == null ? null : failure.map(aspect, args, error);
        }
    }
}
