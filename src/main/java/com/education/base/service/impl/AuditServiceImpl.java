package com.education.base.service.impl;

import com.education.base.audit.AuditDetails;
import com.education.base.audit.AuditEvent;
import com.education.base.audit.AuditResult;
import com.education.base.audit.AuditUserTypeResolver;
import com.education.base.entity.AuditLogEntity;
import com.education.base.repository.AuditLogRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ghi {@code SYS_AUDIT_LOGS} trong transaction riêng ({@code REQUIRES_NEW}), không bao giờ ném lỗi ra ngoài.
 * <p>
 * Ngữ cảnh (người dùng, IP, User-Agent, thời điểm) được chụp ngay khi gọi {@link #record}; nếu đang trong một
 * transaction thì sự kiện {@code SUCCESS} được hoãn tới {@code afterCommit}. IP là {@code getRemoteAddr()} - sau
 * reverse proxy, Tomcat ({@code server.forward-headers-strategy=native}) đã thay bằng IP thật từ
 * {@code X-Forwarded-For}. Không đọc header Cookie / Authorization.
 */
@Slf4j
@Service
public class AuditServiceImpl implements AuditService {

    /** Không log WARN lỗi ghi nhật ký quá 1 lần / khoảng này (ví dụ chưa chạy V17_3). */
    static final long WARN_INTERVAL_MILLIS = 60_000L;

    private final AuditLogRepository repository;
    private final TransactionTemplate requiresNew;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ObjectProvider<AuditUserTypeResolver> userTypeResolver;
    private final boolean enabled;
    private final AtomicLong lastWarnAt = new AtomicLong(Long.MIN_VALUE / 2);

    public AuditServiceImpl(AuditLogRepository repository,
                            PlatformTransactionManager transactionManager,
                            ObjectMapper objectMapper,
                            Clock clock,
                            ObjectProvider<AuditUserTypeResolver> userTypeResolver,
                            @Value("${app.audit.enabled:true}") boolean enabled) {
        this.repository = repository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.userTypeResolver = userTypeResolver;
        this.enabled = enabled;
    }

    @Override
    public void record(AuditEvent event) {
        if (!enabled || event == null) {
            return;
        }
        try {
            AuditLogEntity entity = toEntity(event);
            if (entity.getAction() == null) {
                log.warn("Bỏ qua sự kiện nhật ký không có action: {}", event.getResourceType());
                return;
            }
            boolean deferUntilCommit = event.getResult() == AuditResult.SUCCESS
                    && TransactionSynchronizationManager.isSynchronizationActive()
                    && TransactionSynchronizationManager.isActualTransactionActive();
            if (deferUntilCommit) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        write(entity);
                    }
                });
            } else {
                write(entity);
            }
        } catch (RuntimeException ex) {
            warn(event, ex);
        }
    }

    void write(AuditLogEntity entity) {
        try {
            requiresNew.executeWithoutResult(status -> repository.save(entity));
        } catch (RuntimeException ex) {
            warn(entity.getAction(), ex);
        }
    }

    AuditLogEntity toEntity(AuditEvent event) {
        Optional<AuthUserPrincipal> principal = SecurityUtils.currentUser();
        // Người thao tác ghi đè (ví dụ đăng nhập thất bại): dùng nguyên cặp id/username của sự kiện, KHÔNG trộn
        // với người dùng đang đăng nhập (tránh gán lần đăng nhập sai của tài khoản khác cho phiên hiện tại).
        boolean overridden = event.getActorUserId() != null || event.getActorUsername() != null;
        Long userId = overridden ? event.getActorUserId() : principal.map(AuthUserPrincipal::getId).orElse(null);
        String username = overridden ? event.getActorUsername()
                : principal.map(AuthUserPrincipal::getUsername).orElse(null);
        String userType = event.getActorUserType() != null ? event.getActorUserType()
                : overridden ? null : principal.map(this::resolveUserType).orElse(null);
        HttpServletRequest request = currentRequest();
        AuditResult result = event.getResult() == null ? AuditResult.SUCCESS : event.getResult();
        return AuditLogEntity.builder()
                .eventTime(LocalDateTime.now(clock))
                .userId(userId)
                .username(AuditDetails.truncateUtf8(trimToNull(username), AuditLogEntity.USERNAME_MAX_BYTES))
                .userType(AuditDetails.code(userType, AuditLogEntity.USER_TYPE_MAX_BYTES))
                .action(AuditDetails.code(event.getAction(), AuditLogEntity.ACTION_MAX_BYTES))
                .resourceType(AuditDetails.code(event.getResourceType(), AuditLogEntity.RESOURCE_TYPE_MAX_BYTES))
                .resourceId(AuditDetails.truncateUtf8(trimToNull(event.getResourceId()),
                        AuditLogEntity.RESOURCE_ID_MAX_BYTES))
                .ip(request == null ? null
                        : AuditDetails.truncateUtf8(request.getRemoteAddr(), AuditLogEntity.IP_MAX_BYTES))
                .userAgent(request == null ? null
                        : AuditDetails.truncateUtf8(trimToNull(request.getHeader(HttpHeaders.USER_AGENT)),
                        AuditLogEntity.USER_AGENT_MAX_BYTES))
                .result(result.name())
                .detail(AuditDetails.toJson(objectMapper, event.getDetails(), AuditLogEntity.DETAIL_MAX_BYTES))
                .build();
    }

    private String resolveUserType(AuthUserPrincipal principal) {
        AuditUserTypeResolver resolver = userTypeResolver.getIfAvailable();
        if (resolver == null) {
            return null;
        }
        try {
            return resolver.userTypeOf(principal);
        } catch (RuntimeException ex) {
            log.debug("AuditUserTypeResolver lỗi: {}", ex.toString());
            return null;
        }
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void warn(Object action, RuntimeException ex) {
        long now = clock.millis();
        long last = lastWarnAt.get();
        if (now - last >= WARN_INTERVAL_MILLIS && lastWarnAt.compareAndSet(last, now)) {
            log.warn("Không ghi được nhật ký hệ thống (action={}): {}. Kiểm tra bảng SYS_AUDIT_LOGS (V17_3).",
                    action instanceof AuditEvent event ? event.getAction() : action, ex.toString());
        } else {
            log.debug("Không ghi được nhật ký hệ thống (action={}): {}", action, ex.toString());
        }
    }
}
