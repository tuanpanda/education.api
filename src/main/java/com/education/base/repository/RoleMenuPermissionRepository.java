package com.education.base.repository;

import com.education.base.entity.RoleMenuPermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository JPA của {@code SYS_ROLE_MENU_PERMISSIONS}.
 */
public interface RoleMenuPermissionRepository extends JpaRepository<RoleMenuPermissionEntity, Long> {

    Optional<RoleMenuPermissionEntity> findByRoleIdAndMenuId(Long roleId, Long menuId);

    List<RoleMenuPermissionEntity> findByRoleId(Long roleId);

    List<RoleMenuPermissionEntity> findByRoleIdIn(Collection<Long> roleIds);

    List<RoleMenuPermissionEntity> findByMenuId(Long menuId);
}
