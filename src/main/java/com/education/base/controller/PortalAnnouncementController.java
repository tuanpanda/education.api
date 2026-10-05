package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.response.PortalAnnouncementDto;
import com.education.base.security.PortalAccess;
import com.education.base.service.AnnouncementQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Thông báo trên cổng học sinh. Chỉ tài khoản {@code STUDENT}; không nhận {@code studentId} từ client.
 */
@RestController
@RequestMapping("/api/v1/portal/me/announcements")
@RequiredArgsConstructor
@PortalAccess
@Tag(name = "Cổng học sinh - Thông báo", description = "Danh sách và đánh dấu đã đọc thông báo của học sinh")
public class PortalAnnouncementController {

    private final AnnouncementQueryService announcementQueryService;

    @Operation(summary = "Danh sách thông báo dành cho học sinh đang đăng nhập",
            description = "Chỉ PUBLISHED còn hiệu lực, audience STUDENT/ALL, phạm vi ALL hoặc lớp đang ghi danh.")
    @GetMapping
    public ApiResponse<List<PortalAnnouncementDto>> list() {
        return ApiResponse.success(announcementQueryService.listForCurrentStudent());
    }

    @Operation(summary = "Đánh dấu đã đọc thông báo",
            description = "Chỉ thành công nếu thông báo nằm trong phạm vi nhìn thấy của học sinh (chống IDOR).")
    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable("id") Long id) {
        announcementQueryService.markRead(id);
        return ApiResponse.success("Đã đánh dấu đã đọc.", null);
    }
}
