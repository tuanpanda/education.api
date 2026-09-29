package com.education.base.service;

import com.education.base.dto.request.UserCreateRequest;
import com.education.base.dto.request.UserFilterRequest;
import com.education.base.dto.request.UserUpdateRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.UserResponseDto;

import java.util.List;

/**
 * Quản trị tài khoản người dùng ({@code SYS_USERS}, {@code SYS_USER_ROLES}).
 * <p>
 * Chặn: tự khóa / tự xóa / tự đặt lại mật khẩu, và khóa / xóa / gỡ vai trò của quản trị viên cuối cùng.
 */
public interface UserAdminService {

    PageResponse<UserResponseDto> search(UserFilterRequest filter);

    UserResponseDto getById(Long id);

    UserResponseDto create(UserCreateRequest request);

    UserResponseDto update(Long id, UserUpdateRequest request);

    void delete(Long id, Long currentUserId);

    UserResponseDto changeStatus(Long id, boolean active, Long currentUserId);

    void resetPassword(Long id, String newPassword, Long currentUserId);

    UserResponseDto assignRoles(Long id, List<Long> roleIds);
}
