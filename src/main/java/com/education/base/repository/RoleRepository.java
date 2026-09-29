package com.education.base.repository;

import com.education.base.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository JPA của {@code SYS_ROLES}.
 */
public interface RoleRepository extends JpaRepository<RoleEntity, Long>, JpaSpecificationExecutor<RoleEntity> {

    Optional<RoleEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    Optional<RoleEntity> findByRoleCodeAndIsDeleted(String roleCode, Integer isDeleted);

    boolean existsByRoleCode(String roleCode);

    List<RoleEntity> findByIdInAndIsDeleted(Collection<Long> ids, Integer isDeleted);
}
