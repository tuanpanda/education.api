package com.education.base.repository;

import com.education.base.entity.SubmissionHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * Repository JPA của bài tập về nhà (GĐ2). Hợp đồng Luồng 0 chỉ khai báo truy vấn tối thiểu;
 * Bot 1 (backend) thêm truy vấn nghiệp vụ.
 */
public interface SubmissionHistoryRepository extends JpaRepository<SubmissionHistoryEntity, Long>, JpaSpecificationExecutor<SubmissionHistoryEntity> {

    List<SubmissionHistoryEntity> findBySubmissionIdOrderByAttemptNoDesc(Long submissionId);
}
