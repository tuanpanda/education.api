package com.education.base.service.homework;

import com.education.base.dto.request.AssignmentFilterRequest;
import com.education.base.dto.request.AssignmentUpsertRequest;
import com.education.base.dto.request.GradeSubmissionRequest;
import com.education.base.dto.request.ReturnSubmissionRequest;
import com.education.base.dto.response.AnnouncementClassOptionDto;
import com.education.base.dto.response.AssignmentDto;
import com.education.base.dto.response.FileResponseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.SubmissionDetailDto;
import com.education.base.dto.response.SubmissionMatrixDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Nghiệp vụ bài tập phía nhân viên ({@code /api/v1/lms/assignments/**}). Bot 1 viết {@code HomeworkServiceImpl}.
 * <p>
 * Mọi hàm nhận/đọc theo lớp phải gọi {@code TeachingAssignmentGuard}: giáo viên bị giới hạn chỉ thao tác lớp
 * mình chủ nhiệm/dạy -> 403 {@code NOT_CLASS_TEACHER}; danh sách lọc theo
 * {@code taughtClassIdsIfRestricted()}. Bản ghi xoá mềm / không tồn tại -> 404 {@code ASSIGNMENT_NOT_FOUND}.
 * Quy tắc chi tiết: {@code docs/homework-api.md}.
 */
public interface HomeworkService {

    PageResponse<AssignmentDto> search(AssignmentFilterRequest filter);

    AssignmentDto getById(Long id);

    /** Tạo DRAFT. */
    AssignmentDto create(AssignmentUpsertRequest request);

    AssignmentDto update(Long id, AssignmentUpsertRequest request);

    /** DRAFT/CLOSED -> PUBLISHED (CLOSED -> PUBLISHED = mở lại). */
    AssignmentDto publish(Long id);

    /** PUBLISHED -> CLOSED: chặn nộp mới (kể cả nộp muộn). */
    AssignmentDto close(Long id);

    /** Xoá mềm; 400 {@code ASSIGNMENT_HAS_SUBMISSIONS} nếu đã có bài nộp khác DRAFT. */
    void softDelete(Long id);

    /** Lớp được chọn khi tạo bài (giáo viên: lớp mình; nhân viên khác: mọi lớp đang hoạt động). */
    List<AnnouncementClassOptionDto> listManageableClasses();

    FileResponseDto uploadAttachment(Long id, MultipartFile file);

    void deleteAttachment(Long id, Long fileId);

    /** Ma trận trạng thái MỌI học sinh ENROLLED của lớp. */
    SubmissionMatrixDto getSubmissionMatrix(Long id);

    SubmissionDetailDto getSubmission(Long submissionId);

    /** Optimistic lock theo {@code expectedVersion}; lệch -> 409 {@code GRADE_CONFLICT}. Đồng bộ sổ điểm nếu bật. */
    SubmissionDetailDto grade(Long submissionId, GradeSubmissionRequest request);

    /** SUBMITTED/GRADED -> RETURNED (cho học sinh làm lại kể cả quá hạn tới khi CLOSED). */
    SubmissionDetailDto returnSubmission(Long submissionId, ReturnSubmissionRequest request);

    FileResponseDto uploadFeedbackFile(Long submissionId, MultipartFile file);

    void deleteFeedbackFile(Long submissionId, Long fileId);
}
