package com.education.base.service;

import com.education.base.dto.request.RoleCreateRequest;
import com.education.base.dto.request.RoleFilterRequest;
import com.education.base.dto.request.RolePermissionUpdateRequest;
import com.education.base.dto.request.RoleUpdateRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.RolePermissionMatrixResponse;
import com.education.base.dto.response.RoleResponseDto;

/**
 * Quản trị vai trò ({@code SYS_ROLES}) và ma trận quyền menu x chức năng ({@code SYS_ROLE_MENU_PERMISSIONS}).
 */
public interface RoleAdminService {

    PageResponse<RoleResponseDto> search(RoleFilterRequest filter);

    RoleResponseDto getById(Long id);

    RoleResponseDto create(RoleCreateRequest request);

    RoleResponseDto update(Long id, RoleUpdateRequest request);

    void delete(Long id);

    RolePermissionMatrixResponse getPermissions(Long id);

    RolePermissionMatrixResponse updatePermissions(Long id, RolePermissionUpdateRequest request);
}
