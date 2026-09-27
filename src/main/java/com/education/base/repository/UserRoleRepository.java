package com.education.base.repository;

import com.education.base.entity.UserRoleEntity;
import com.education.base.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository JPA của {@code SYS_USER_ROLES} (khóa kép {@link UserRoleId}).
 */
public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleId> {

    List<UserRoleEntity> findByUserId(Long userId);

    List<UserRoleEntity> findByRoleId(Long roleId);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);
}
