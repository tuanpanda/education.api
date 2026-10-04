package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.AuditLogFilterRequest;
import com.education.base.dto.response.AuditLogResponseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.service.AuditLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nhật ký hệ thống (chỉ đọc): đăng nhập, mật khẩu, khóa tài khoản, phân quyền, thanh toán, khoản phí.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Validated
@Tag(name = "Nhật ký hệ thống", description = "Tra cứu SYS_AUDIT_LOGS (chỉ đọc)")
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    @Operation(summary = "Tra cứu nhật ký hệ thống (phân trang, mới nhất trước)",
            description = "Lọc theo khoảng ngày, người dùng, loại tài khoản, hành động, đối tượng, kết quả, IP, "
                    + "từ khóa trong chi tiết.")
    @GetMapping("/search")
    @RequirePermission(Permissions.AUDIT_LOG_VIEW)
    public ApiResponse<PageResponse<AuditLogResponseDto>> search(@Valid @ModelAttribute AuditLogFilterRequest filter) {
        return ApiResponse.success(auditLogQueryService.search(filter));
    }
}
