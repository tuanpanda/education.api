package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Một dòng nhật ký hệ thống ({@code SYS_AUDIT_LOGS}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponseDto {

    private Long id;

    private LocalDateTime eventTime;

    private Long userId;

    private String username;

    private String userType;

    private String action;

    private String resourceType;

    private String resourceId;

    private String ip;

    private String userAgent;

    /** {@code SUCCESS} / {@code FAILURE} / {@code DENIED}. */
    private String result;

    /** JSON rút gọn. */
    private String detail;
}
