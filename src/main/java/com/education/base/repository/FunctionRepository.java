package com.education.base.repository;

import com.education.base.entity.FunctionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository JPA của {@code SYS_FUNCTIONS}.
 */
public interface FunctionRepository extends JpaRepository<FunctionEntity, Long> {

    List<FunctionEntity> findByMenuIdAndIsDeleted(Long menuId, Integer isDeleted);
}
