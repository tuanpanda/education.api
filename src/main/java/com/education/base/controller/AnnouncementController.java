package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.AnnouncementFilterRequest;
import com.education.base.dto.request.AnnouncementUpsertRequest;
import com.education.base.dto.response.AnnouncementClassOptionDto;
import com.education.base.dto.response.AnnouncementDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Quản trị thông báo (menu {@code MENU_ANNOUNCEMENT}, V18_2).
 * <p>
 * Giáo viên ({@code ROLE_TEACHER}, không phải admin) chỉ tạo/sửa/đăng/lưu trữ/xóa thông báo
 * phạm vi CLASS cho lớp mình dạy; xem thêm thông báo ALL (chỉ đọc).
 */
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
@Validated
@Tag(name = "Thông báo", description = "CRUD và xuất bản thông báo cho cổng học sinh")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @Operation(summary = "Tìm kiếm thông báo (phân trang)")
    @GetMapping
    @RequirePermission(Permissions.ANNOUNCEMENT_VIEW)
    public ApiResponse<PageResponse<AnnouncementDto>> search(@Valid @ModelAttribute AnnouncementFilterRequest filter) {
        return ApiResponse.success(announcementService.search(filter));
    }

    @Operation(summary = "Lớp được phép gắn thông báo CLASS (giáo viên bị giới hạn phân công)")
    @GetMapping("/manageable-classes")
    @RequirePermission(Permissions.ANNOUNCEMENT_VIEW)
    public ApiResponse<List<AnnouncementClassOptionDto>> manageableClasses() {
        return ApiResponse.success(announcementService.listManageableClasses());
    }

    @Operation(summary = "Chi tiết thông báo")
    @GetMapping("/{id}")
    @RequirePermission(Permissions.ANNOUNCEMENT_VIEW)
    public ApiResponse<AnnouncementDto> getById(@PathVariable("id") Long id) {
        return ApiResponse.success(announcementService.getById(id));
    }

    @Operation(summary = "Tạo thông báo (nháp)")
    @PostMapping
    @RequirePermission(Permissions.ANNOUNCEMENT_CREATE)
    public ApiResponse<AnnouncementDto> create(@Valid @RequestBody AnnouncementUpsertRequest request) {
        return ApiResponse.success("Đã tạo thông báo.", announcementService.create(request));
    }

    @Operation(summary = "Cập nhật thông báo")
    @PutMapping("/{id}")
    @RequirePermission(Permissions.ANNOUNCEMENT_UPDATE)
    public ApiResponse<AnnouncementDto> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody AnnouncementUpsertRequest request) {
        return ApiResponse.success("Đã cập nhật thông báo.", announcementService.update(id, request));
    }

    @Operation(summary = "Xuất bản thông báo")
    @PostMapping("/{id}/publish")
    @RequirePermission(Permissions.ANNOUNCEMENT_PUBLISH)
    public ApiResponse<AnnouncementDto> publish(@PathVariable("id") Long id) {
        return ApiResponse.success("Đã xuất bản thông báo.", announcementService.publish(id));
    }

    @Operation(summary = "Lưu trữ thông báo")
    @PostMapping("/{id}/archive")
    @RequirePermission(Permissions.ANNOUNCEMENT_UPDATE)
    public ApiResponse<AnnouncementDto> archive(@PathVariable("id") Long id) {
        return ApiResponse.success("Đã lưu trữ thông báo.", announcementService.archive(id));
    }

    @Operation(summary = "Xóa mềm thông báo")
    @DeleteMapping("/{id}")
    @RequirePermission(Permissions.ANNOUNCEMENT_DELETE)
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        announcementService.softDelete(id);
        return ApiResponse.success("Đã xóa thông báo.", null);
    }
}