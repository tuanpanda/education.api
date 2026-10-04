package com.education.base.audit;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Map;

/**
 * Một sự kiện cần ghi vào {@code SYS_AUDIT_LOGS}.
 * <p>
 * Người thao tác, IP và User-Agent mặc định lấy từ request / {@code SecurityContext} hiện tại tại thời điểm
 * {@code AuditService#record} được gọi; {@code actor*} chỉ cần khi chưa có người dùng đăng nhập (ví dụ đăng nhập
 * thất bại). {@code details} được ghi thành JSON rút gọn; khóa nhạy cảm (password, token, cookie, secret...) luôn
 * bị che - dù vậy KHÔNG truyền các giá trị đó vào đây.
 * <pre>{@code
 * auditService.record(AuditEvent.builder()
 *         .action(AuditActions.STUDENT_ACCOUNT_PROVISIONED)
 *         .resource(AuditActions.RESOURCE_USER, userId)
 *         .detail("studentId", studentId)
 *         .build());
 * }</pre>
 */
@Value
@Builder(toBuilder = true)
public class AuditEvent {

    /** Mã hành động, xem {@link AuditActions}. Bắt buộc. */
    String action;

    @Builder.Default
    AuditResult result = AuditResult.SUCCESS;

    String resourceType;

    String resourceId;

    /** Chi tiết bổ sung (giá trị vô hướng / danh sách ngắn). */
    @Singular
    Map<String, Object> details;

    /** Ghi đè người thao tác (mặc định: người dùng đăng nhập hiện tại). */
    Long actorUserId;

    String actorUsername;

    /** Ghi đè loại tài khoản (mặc định: {@link AuditUserTypeResolver} nếu có, không thì {@code null}). */
    String actorUserType;

    public static class AuditEventBuilder {

        /** Loại + khóa đối tượng; {@code id} được chuyển thành chuỗi ({@code null} giữ nguyên). */
        public AuditEventBuilder resource(String type, Object id) {
            this.resourceType = type;
            this.resourceId = id == null ? null : String.valueOf(id);
            return this;
        }

        public AuditEventBuilder actor(Long userId, String username) {
            this.actorUserId = userId;
            this.actorUsername = username;
            return this;
        }
    }
}
