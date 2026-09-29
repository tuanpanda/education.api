package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.MenuReorderRequest;
import com.education.base.dto.request.MenuUpsertRequest;
import com.education.base.dto.response.AdminMenuResponseDto;
import com.education.base.dto.response.FunctionOptionDto;
import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.service.MenuAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Quản trị cây menu sidebar và danh mục chức năng.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Validated
@Tag(name = "Quản trị menu", description = "Cây menu, chức năng của menu, sắp xếp")
public class AdminMenuController {

    private final MenuAdminService menuAdminService;

    @Operation(summary = "Cây menu đầy đủ (kể cả menu ẩn / ngừng hoạt động)")
    @GetMapping("/menus/tree")
    @RequirePermission({Permissions.MENU_CONFIG_VIEW, Permissions.ROLE_VIEW})
    public ApiResponse<List<AdminMenuResponseDto>> tree() {
        return ApiResponse.success(menuAdminService.getTree());
    }

    @Operation(summary = "Chi tiết menu")
    @GetMapping("/menus/{id}")
    @RequirePermission(Permissions.MENU_CONFIG_VIEW)
    public ApiResponse<AdminMenuResponseDto> getById(@PathVariable("id") Long id) {
        return ApiResponse.success(menuAdminService.getById(id));
    }

    @Operation(summary = "Tạo menu")
    @PostMapping("/menus")
    @RequirePermission(Permissions.MENU_CONFIG_CREATE)
    public ApiResponse<AdminMenuResponseDto> create(@Valid @RequestBody MenuUpsertRequest request) {
        return ApiResponse.success("Đã tạo menu.", menuAdminService.create(request));
    }

    @Operation(summary = "Sắp xếp lại menu (thứ tự và menu cha)")
    @PutMapping("/menus/reorder")
    @RequirePermission(Permissions.MENU_CONFIG_UPDATE)
    public ApiResponse<List<AdminMenuResponseDto>> reorder(@Valid @RequestBody MenuReorderRequest request) {
        return ApiResponse.success("Đã lưu thứ tự menu.", menuAdminService.reorder(request));
    }

    @Operation(summary = "Cập nhật menu (không đổi được mã menu)")
    @PutMapping("/menus/{id}")
    @RequirePermission(Permissions.MENU_CONFIG_UPDATE)
    public ApiResponse<AdminMenuResponseDto> update(@PathVariable("id") Long id,
                                                    @Valid @RequestBody MenuUpsertRequest request) {
        return ApiResponse.success("Đã cập nhật menu.", menuAdminService.update(id, request));
    }

    @Operation(summary = "Xóa mềm menu (menu không còn menu con)")
    @DeleteMapping("/menus/{id}")
    @RequirePermission(Permissions.MENU_CONFIG_DELETE)
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        menuAdminService.delete(id);
        return ApiResponse.success("Đã xóa menu.", null);
    }

    @Operation(summary = "Danh mục mã chức năng (VIEW, CREATE, UPDATE, DELETE, EXPORT...)")
    @GetMapping("/functions")
    @RequirePermission({Permissions.MENU_CONFIG_VIEW, Permissions.ROLE_VIEW})
    public ApiResponse<List<FunctionOptionDto>> functions() {
        return ApiResponse.success(menuAdminService.listFunctions());
    }
}
