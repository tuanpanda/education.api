package com.education.base.repository;

import com.education.base.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository JPA của {@code SYS_ROLES}.
 */
public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    Optional<RoleEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    Optional<RoleEntity> findByRoleCodeAndIsDeleted(String roleCode, Integer isDeleted);

    boolean existsByRoleCode(String roleCode);
}
