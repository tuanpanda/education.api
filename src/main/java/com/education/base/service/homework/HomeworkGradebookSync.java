package com.education.base.service.homework;

import com.education.base.entity.AssignmentEntity;
import com.education.base.entity.SubmissionEntity;

/**
 * Đẩy điểm bài tập vào {@code EDU_GRADES} (V20_4) khi {@code LMS_ASSIGNMENTS.SYNC_TO_GRADEBOOK = 1}.
 * Upsert 1 dòng / (ASSIGNMENT_ID, STUDENT_ID): GRADE_TYPE = 'ASSIGNMENT', SCORE = round(score / maxScore * 10, 2),
 * WEIGHT = GRADE_WEIGHT, EXAM_DATE = ngày DUE_AT, NOTE = "BT: " + tiêu đề (cắt 255).
 * Trả bài (RETURNED) / xoá bài -> xoá mềm dòng điểm đã đẩy. Lỗi -> audit HOMEWORK_GRADEBOOK_SYNC_FAILED, không ném.
 */
public interface HomeworkGradebookSync {

    void onGraded(AssignmentEntity assignment, SubmissionEntity submission);

    void onUngraded(AssignmentEntity assignment, SubmissionEntity submission);

    /** Bật/tắt cờ hoặc đổi MAX_SCORE/GRADE_WEIGHT -> đồng bộ lại toàn bộ bài đã chấm. */
    void resyncAssignment(AssignmentEntity assignment);
}
