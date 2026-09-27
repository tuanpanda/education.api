package com.education.base.repository;

import com.education.base.entity.LeadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Repository JPA của {@code EDU_LEADS}.
 */
public interface LeadRepository extends JpaRepository<LeadEntity, Long>, JpaSpecificationExecutor<LeadEntity> {

    Optional<LeadEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    boolean existsByLeadCode(String leadCode);

    Optional<LeadEntity> findByLeadCodeAndIsDeleted(String leadCode, Integer isDeleted);
}
