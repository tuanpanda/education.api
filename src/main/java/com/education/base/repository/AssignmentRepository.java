package com.education.base.repository;

import com.education.base.entity.AssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Repository JPA của bài tập về nhà (GĐ2). Hợp đồng Luồng 0 chỉ khai báo truy vấn tối thiểu;
 * Bot 1 (backend) thêm truy vấn nghiệp vụ.
 */
public interface AssignmentRepository extends JpaRepository<AssignmentEntity, Long>, JpaSpecificationExecutor<AssignmentEntity> {

    Optional<AssignmentEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);
}
