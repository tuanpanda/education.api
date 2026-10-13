package com.education.base.service.file;

import com.education.base.common.PersistenceFlags;
import com.education.base.entity.AssignmentEntity;
import com.education.base.entity.SubmissionEntity;
import com.education.base.repository.AssignmentRepository;
import com.education.base.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * GĐ2 - tra cứu bản ghi tham chiếu cho 3 descriptor file bài tập (ASSIGNMENT / SUBMISSION / FEEDBACK).
 * Không kiểm quyền; chỉ đọc.
 */
@Component
@RequiredArgsConstructor
public class HomeworkFileRefs {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;

    public Optional<AssignmentEntity> assignment(Long assignmentId) {
        return assignmentId == null ? Optional.empty() : assignmentRepository.findById(assignmentId);
    }

    public Optional<SubmissionEntity> submission(Long submissionId) {
        return submissionId == null ? Optional.empty() : submissionRepository.findById(submissionId);
    }

    /** Lớp của bài tập (null nếu không tìm thấy). */
    public Long classOfAssignment(Long assignmentId) {
        return assignment(assignmentId).map(AssignmentEntity::getClassId).orElse(null);
    }

    /** Lớp của bài tập chứa bài nộp (null nếu không tìm thấy). */
    public Long classOfSubmission(Long submissionId) {
        return submission(submissionId).map(s -> classOfAssignment(s.getAssignmentId())).orElse(null);
    }

    static boolean deleted(Integer flag) {
        return flag != null && flag != PersistenceFlags.NOT_DELETED;
    }
}
