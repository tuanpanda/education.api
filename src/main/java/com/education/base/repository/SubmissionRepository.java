package com.education.base.repository;

import com.education.base.entity.SubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository JPA của bài tập về nhà (GĐ2). Hợp đồng Luồng 0 chỉ khai báo truy vấn tối thiểu;
 * Bot 1 (backend) thêm truy vấn nghiệp vụ.
 */
public interface SubmissionRepository extends JpaRepository<SubmissionEntity, Long>, JpaSpecificationExecutor<SubmissionEntity> {

    Optional<SubmissionEntity> findByAssignmentIdAndStudentIdAndIsDeleted(Long assignmentId, Long studentId, Integer isDeleted);

    List<SubmissionEntity> findByAssignmentIdAndIsDeleted(Long assignmentId, Integer isDeleted);

    List<SubmissionEntity> findByStudentIdAndAssignmentIdInAndIsDeleted(Long studentId, Collection<Long> assignmentIds,
                                                                       Integer isDeleted);

    boolean existsByAssignmentIdAndIsDeleted(Long assignmentId, Integer isDeleted);
}
