package com.education.base.service;

import com.education.base.dto.response.UserNavigationResponseDto;
import com.education.base.entity.RoleEntity;
import com.education.base.entity.UserEntity;
import com.education.base.security.AuthUserPrincipal;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Tính vai trò, quyền phẳng và menu điều hướng của người dùng từ
 * {@code SYS_USER_ROLES}, {@code SYS_ROLE_MENU_PERMISSIONS}, {@code SYS_MENUS}, {@code SYS_FUNCTIONS}.
 */
public interface AccessControlService {

    /**
     * Người dùng còn hoạt động ({@code ACTIVE}, chưa xóa) kèm vai trò và quyền; rỗng nếu không hợp lệ.
     */
    Optional<AuthUserPrincipal> loadActivePrincipal(Long userId);

    /** Dựng principal (vai trò + quyền) từ entity người dùng. */
    AuthUserPrincipal buildPrincipal(UserEntity user);

    /**
     * Quyền phẳng {@code MENU_CODE:FUNCTION_CODE} của tập vai trò. {@code ROLE_ADMIN} nhận mọi chức năng
     * của mọi menu đang hoạt động.
     */
    Set<String> resolvePermissions(Collection<RoleEntity> roles);

    /** Cây menu sidebar (chỉ menu có quyền VIEW) và quyền phẳng của người dùng. */
    UserNavigationResponseDto getNavigation(AuthUserPrincipal principal);
}
