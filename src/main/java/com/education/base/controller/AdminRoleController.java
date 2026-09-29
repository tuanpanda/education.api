package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.RoleCreateRequest;
import com.education.base.dto.request.RoleFilterRequest;
import com.education.base.dto.request.RolePermissionUpdateRequest;
import com.education.base.dto.request.RoleUpdateRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.RolePermissionMatrixResponse;
import com.education.base.dto.response.RoleResponseDto;
import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.service.RoleAdminService;
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

/**
 * Quản trị vai trò và ma trận phân quyền menu x chức năng.
 */
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
@Validated
@Tag(name = "Quản trị vai trò & phân quyền", description = "Vai trò và quyền menu x chức năng")
public class AdminRoleController {

    private final RoleAdminService roleAdminService;

    @Operation(summary = "Tìm kiếm vai trò (phân trang)",
            description = "Màn hình người dùng cũng dùng API này để chọn vai trò khi gán.")
    @GetMapping
    @RequirePermission({Permissions.ROLE_VIEW, Permissions.USER_VIEW})
    public ApiResponse<PageResponse<RoleResponseDto>> search(@Valid @ModelAttribute RoleFilterRequest filter) {
        return ApiResponse.success(roleAdminService.search(filter));
    }

    @Operation(summary = "Chi tiết vai trò")
    @GetMapping("/{id}")
    @RequirePermission(Permissions.ROLE_VIEW)
    public ApiResponse<RoleResponseDto> getById(@PathVariable("id") Long id) {
        return ApiResponse.success(roleAdminService.getById(id));
    }

    @Operation(summary = "Tạo vai trò")
    @PostMapping
    @RequirePermission(Permissions.ROLE_CREATE)
    public ApiResponse<RoleResponseDto> create(@Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.success("Đã tạo vai trò.", roleAdminService.create(request));
    }

    @Operation(summary = "Cập nhật vai trò")
    @PutMapping("/{id}")
    @RequirePermission(Permissions.ROLE_UPDATE)
    public ApiResponse<RoleResponseDto> update(@PathVariable("id") Long id,
                                               @Valid @RequestBody RoleUpdateRequest request) {
        return ApiResponse.success("Đã cập nhật vai trò.", roleAdminService.update(id, request));
    }

    @Operation(summary = "Xóa mềm vai trò (không xóa được vai trò đang gán cho người dùng)")
    @DeleteMapping("/{id}")
    @RequirePermission(Permissions.ROLE_DELETE)
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        roleAdminService.delete(id);
        return ApiResponse.success("Đã xóa vai trò.", null);
    }

    @Operation(summary = "Ma trận phân quyền của vai trò (cây menu x chức năng)")
    @GetMapping("/{id}/permissions")
    @RequirePermission(Permissions.ROLE_VIEW)
    public ApiResponse<RolePermissionMatrixResponse> getPermissions(@PathVariable("id") Long id) {
        return ApiResponse.success(roleAdminService.getPermissions(id));
    }

    @Operation(summary = "Lưu ma trận phân quyền của vai trò (thay thế toàn bộ)",
            description = "Cấp quyền trên menu con sẽ tự cấp VIEW cho các menu cha.")
    @PutMapping("/{id}/permissions")
    @RequirePermission(Permissions.ROLE_UPDATE)
    public ApiResponse<RolePermissionMatrixResponse> updatePermissions(
            @PathVariable("id") Long id,
            @Valid @RequestBody RolePermissionUpdateRequest request) {
        return ApiResponse.success("Đã lưu phân quyền.", roleAdminService.updatePermissions(id, request));
    }
}
