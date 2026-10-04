package com.education.base.service;

import com.education.base.audit.AuditEvent;
import com.education.base.audit.AuditResult;

import java.util.Map;

/**
 * Ghi nhật ký hệ thống ({@code SYS_AUDIT_LOGS}).
 * <p>
 * Cam kết: KHÔNG BAO GIỜ ném lỗi ra ngoài và không làm hỏng giao dịch đang chạy - lỗi ghi nhật ký (bảng chưa
 * tạo, DB lỗi...) chỉ được log WARN. Bản ghi được ghi trong transaction riêng ({@code REQUIRES_NEW}); nếu gọi bên
 * trong một transaction đang mở thì sự kiện {@code SUCCESS} chỉ được ghi SAU KHI transaction đó commit (rollback =
 * không ghi), sự kiện {@code FAILURE} / {@code DENIED} được ghi ngay.
 * <p>
 * Đây là hook công khai cho các module khác (stream A: cấp tài khoản học sinh, liên kết HS, hàng rào cổng):
 * {@code auditService.record(AuditEvent.builder().action(...).resource(...).detail(k, v).build())}.
 */
public interface AuditService {

    void record(AuditEvent event);

    default void success(String action, String resourceType, Object resourceId, Map<String, ?> details) {
        record(event(action, AuditResult.SUCCESS, resourceType, resourceId, details));
    }

    default void failure(String action, String resourceType, Object resourceId, Map<String, ?> details) {
        record(event(action, AuditResult.FAILURE, resourceType, resourceId, details));
    }

    default void denied(String action, String resourceType, Object resourceId, Map<String, ?> details) {
        record(event(action, AuditResult.DENIED, resourceType, resourceId, details));
    }

    private static AuditEvent event(String action, AuditResult result, String resourceType, Object resourceId,
                                    Map<String, ?> details) {
        AuditEvent.AuditEventBuilder builder = AuditEvent.builder()
                .action(action)
                .result(result)
                .resource(resourceType, resourceId);
        if (details != null) {
            details.forEach(builder::detail);
        }
        return builder.build();
    }
}
