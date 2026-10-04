package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng {@code SYS_AUDIT_LOGS} (V17_3) - nhật ký hệ thống, chỉ ghi thêm (ứng dụng không sửa / xóa).
 * Ghi qua {@code AuditService}; đọc qua {@code AuditLogQueryService}.
 */
@Entity
@Table(name = "SYS_AUDIT_LOGS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntity {

    public static final int USERNAME_MAX_BYTES = 100;
    public static final int USER_TYPE_MAX_BYTES = 20;
    public static final int ACTION_MAX_BYTES = 50;
    public static final int RESOURCE_TYPE_MAX_BYTES = 50;
    public static final int RESOURCE_ID_MAX_BYTES = 100;
    public static final int IP_MAX_BYTES = 64;
    public static final int USER_AGENT_MAX_BYTES = 500;
    public static final int DETAIL_MAX_BYTES = 4000;

    @Id
    @SequenceGenerator(name = "seq_audit_log", sequenceName = "SEQ_SYS_AUDIT_LOGS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_audit_log")
    @Column(name = "ID")
    private Long id;

    @Column(name = "EVENT_TIME", nullable = false, updatable = false)
    private LocalDateTime eventTime;

    @Column(name = "USER_ID", updatable = false)
    private Long userId;

    @Column(name = "USERNAME", length = USERNAME_MAX_BYTES, updatable = false)
    private String username;

    /** Loại tài khoản (chuỗi tự do, có thể {@code null}) - không ràng buộc với {@code SYS_USERS.USER_TYPE}. */
    @Column(name = "USER_TYPE", length = USER_TYPE_MAX_BYTES, updatable = false)
    private String userType;

    @Column(name = "ACTION", nullable = false, length = ACTION_MAX_BYTES, updatable = false)
    private String action;

    @Column(name = "RESOURCE_TYPE", length = RESOURCE_TYPE_MAX_BYTES, updatable = false)
    private String resourceType;

    @Column(name = "RESOURCE_ID", length = RESOURCE_ID_MAX_BYTES, updatable = false)
    private String resourceId;

    @Column(name = "IP", length = IP_MAX_BYTES, updatable = false)
    private String ip;

    @Column(name = "USER_AGENT", length = USER_AGENT_MAX_BYTES, updatable = false)
    private String userAgent;

    /** {@code SUCCESS} / {@code FAILURE} / {@code DENIED}. */
    @Column(name = "RESULT", nullable = false, length = 20, updatable = false)
    private String result;

    /** JSON rút gọn, tối đa {@value #DETAIL_MAX_BYTES} byte; không chứa mật khẩu / token / cookie. */
    @Column(name = "DETAIL", length = DETAIL_MAX_BYTES, updatable = false)
    private String detail;

    @PrePersist
    void onCreate() {
        if (eventTime == null) {
            eventTime = LocalDateTime.now();
        }
    }
}
