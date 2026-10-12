package com.education.base.service.homework;

import com.education.base.dto.request.PortalSubmissionDraftRequest;
import com.education.base.dto.response.PortalAssignmentDetailDto;
import com.education.base.dto.response.PortalAssignmentListItemDto;
import com.education.base.dto.response.PortalFileDto;
import com.education.base.dto.response.PortalHomeworkSummaryDto;
import com.education.base.dto.response.PortalSubmissionDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Bài tập trên cổng học sinh ({@code /api/v1/portal/me/assignments/**}). Học sinh lấy từ
 * {@code PortalStudentContext.requireCurrentStudentId()} - KHÔNG nhận studentId từ client.
 * Bài phải PUBLISHED/CLOSED, chưa xoá, của lớp học sinh đang ENROLLED ({@code StudentScopeGuard.requireEnrolled});
 * ngoài phạm vi -> 404 {@code ASSIGNMENT_NOT_FOUND} (không 403, không lộ tồn tại).
 */
public interface PortalHomeworkService {

    /** {@code tab}: todo|submitted|graded|overdue (NULL = todo). Không phân trang. */
    List<PortalAssignmentListItemDto> list(String tab);

    PortalHomeworkSummaryDto summary();

    PortalAssignmentDetailDto get(Long assignmentId);

    /**
     * Tạo (lần đầu, STATUS = DRAFT) / cập nhật nội dung. Chỉ khi STATUS DRAFT hoặc RETURNED và còn hạn nộp (kể cả hạn
     * muộn); SUBMITTED/GRADED -> 400 {@code SUBMISSION_LOCKED} (muốn sửa bài đã nộp phải {@link #unsubmit} trước).
     */
    PortalSubmissionDto saveDraft(Long assignmentId, PortalSubmissionDraftRequest request);

    PortalFileDto uploadFile(Long assignmentId, MultipartFile file);

    void deleteFile(Long assignmentId, Long fileId);

    /** DRAFT/RETURNED -> SUBMITTED, ATTEMPT_COUNT+1, ghi LMS_SUBMISSION_HISTORY, IS_LATE theo giờ server. */
    PortalSubmissionDto submit(Long assignmentId);

    /**
     * Rút bài để sửa: SUBMITTED -> DRAFT, chỉ khi ALLOW_RESUBMIT = 1, chưa GRADED và còn hạn nộp. Lịch sử lần nộp cũ giữ.
     */
    PortalSubmissionDto unsubmit(Long assignmentId);
}
