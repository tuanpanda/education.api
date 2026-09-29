package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.ResetPasswordRequest;
import com.education.base.dto.request.UserCreateRequest;
import com.education.base.dto.request.UserFilterRequest;
import com.education.base.dto.request.UserRolesRequest;
import com.education.base.dto.request.UserStatusRequest;
import com.education.base.dto.request.UserUpdateRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.UserResponseDto;
import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.security.SecurityUtils;
import com.education.base.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản trị người dùng: CRUD, khóa / mở khóa, đặt lại mật khẩu, gán vai trò.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "Quản trị người dùng", description = "Tài khoản đăng nhập, trạng thái và vai trò")
public class AdminUserController {

    private final UserAdminService userAdminService;

    @Operation(summary = "Tìm kiếm người dùng (phân trang)")
    @GetMapping
    @RequirePermission(Permissions.USER_VIEW)
    public ApiResponse<PageResponse<UserResponseDto>> search(@Valid @ModelAttribute UserFilterRequest filter) {
        return ApiResponse.success(userAdminService.search(filter));
    }

    @Operation(summary = "Chi tiết người dùng")
    @GetMapping("/{id}")
    @RequirePermission(Permissions.USER_VIEW)
    public ApiResponse<UserResponseDto> getById(@PathVariable("id") Long id) {
        return ApiResponse.success(userAdminService.getById(id));
    }

    @Operation(summary = "Tạo người dùng")
    @PostMapping
    @RequirePermission(Permissions.USER_CREATE)
    public ApiResponse<UserResponseDto> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.success("Đã tạo người dùng.", userAdminService.create(request));
    }

    @Operation(summary = "Cập nhật thông tin người dùng")
    @PutMapping("/{id}")
    @RequirePermission(Permissions.USER_UPDATE)
    public ApiResponse<UserResponseDto> update(@PathVariable("id") Long id,
                                               @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success("Đã cập nhật người dùng.", userAdminService.update(id, request));
    }

    @Operation(summary = "Xóa mềm người dùng")
    @DeleteMapping("/{id}")
    @RequirePermission(Permissions.USER_DELETE)
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        userAdminService.delete(id, SecurityUtils.requireCurrentUser().getId());
        return ApiResponse.success("Đã xóa người dùng.", null);
    }

    @Operation(summary = "Khóa / mở khóa người dùng")
    @PatchMapping("/{id}/status")
    @RequirePermission(Permissions.USER_UPDATE)
    public ApiResponse<UserResponseDto> changeStatus(@PathVariable("id") Long id,
                                                     @Valid @RequestBody UserStatusRequest request) {
        boolean active = Boolean.TRUE.equals(request.getActive());
        UserResponseDto updated = userAdminService.changeStatus(id, active, SecurityUtils.requireCurrentUser().getId());
        return ApiResponse.success(active ? "Đã mở khóa người dùng." : "Đã khóa người dùng.", updated);
    }

    @Operation(summary = "Đặt lại mật khẩu", description = "Người dùng sẽ phải đổi mật khẩu ở lần đăng nhập kế tiếp.")
    @PostMapping("/{id}/reset-password")
    @RequirePermission(Permissions.USER_UPDATE)
    public ApiResponse<Void> resetPassword(@PathVariable("id") Long id,
                                           @Valid @RequestBody ResetPasswordRequest request) {
        userAdminService.resetPassword(id, request.getNewPassword(), SecurityUtils.requireCurrentUser().getId());
        return ApiResponse.success("Đã đặt lại mật khẩu.", null);
    }

    @Operation(summary = "Gán vai trò cho người dùng (thay thế toàn bộ)")
    @PutMapping("/{id}/roles")
    @RequirePermission(Permissions.USER_UPDATE)
    public ApiResponse<UserResponseDto> assignRoles(@PathVariable("id") Long id,
                                                    @Valid @RequestBody UserRolesRequest request) {
        return ApiResponse.success("Đã cập nhật vai trò.", userAdminService.assignRoles(id, request.getRoleIds()));
    }
}
