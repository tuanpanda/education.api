package com.education.base.service;

import com.education.base.dto.request.AuditLogFilterRequest;
import com.education.base.dto.response.AuditLogResponseDto;
import com.education.base.dto.response.PageResponse;

/**
 * Tra cứu nhật ký hệ thống (màn hình "Nhật ký hệ thống", quyền {@code MENU_AUDIT_LOG:VIEW}).
 */
public interface AuditLogQueryService {

    PageResponse<AuditLogResponseDto> search(AuditLogFilterRequest filter);
}
