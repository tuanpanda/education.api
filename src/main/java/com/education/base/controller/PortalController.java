package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.response.PortalMeResponse;
import com.education.base.security.PortalAccess;
import com.education.base.service.PortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cổng học sinh. CHỈ tài khoản {@code STUDENT} ({@link PortalAccess}); nhân viên nhận 403 {@code STUDENT_ONLY}.
 * Không endpoint nào nhận {@code studentId}: học sinh luôn lấy từ phiên qua {@code EDU_USER_STUDENT_LINKS}.
 */
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
@PortalAccess
@Tag(name = "Cổng học sinh", description = "Thông tin của chính học sinh đang đăng nhập")
public class PortalController {

    private final PortalService portalService;

    @Operation(summary = "Hồ sơ học sinh đang đăng nhập",
            description = "Mã, họ tên, ngày sinh, phụ huynh, liên hệ và các lớp đang học (ENROLLED).")
    @GetMapping("/me")
    public ApiResponse<PortalMeResponse> me() {
        return ApiResponse.success(portalService.me());
    }
}
