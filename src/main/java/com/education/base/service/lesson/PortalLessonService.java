package com.education.base.service.lesson;

import com.education.base.dto.request.PortalLessonProgressRequest;
import com.education.base.dto.response.PortalLessonClassDto;
import com.education.base.dto.response.PortalLessonDetailDto;
import com.education.base.dto.response.PortalLessonProgressDto;
import com.education.base.dto.response.PortalLessonTreeDto;

import java.util.List;

/**
 * Học liệu trên cổng học sinh ({@code /api/v1/portal/me/lessons/**}, {@code /portal/me/classes/{classId}/lessons}).
 * Học sinh lấy từ {@code PortalStudentContext.requireCurrentStudentId()} - KHÔNG nhận studentId từ client.
 * <p>
 * Bài nhìn thấy = chưa xoá, PUBLISHED, {@code effectiveAvailableFrom <= now} (giờ server) và thuộc lớp ENROLLED
 * ({@code StudentScopeGuard.requireEnrolled}) hoặc khoá mẫu đang gắn với lớp đó. Ngoài phạm vi / chưa mở -> 404
 * {@code LESSON_NOT_FOUND} (không 403, không lộ tồn tại).
 */
public interface PortalLessonService {

    List<PortalLessonClassDto> listClasses();

    PortalLessonTreeDto getTree(Long classId);

    /** {@code classId} NULL = lớp ENROLLED đầu tiên (theo classId tăng) thấy bài. Không ghi tiến độ. */
    PortalLessonDetailDto get(Long lessonId, Long classId);

    /** Upsert LMS_LESSON_PROGRESS; COMPLETED không lùi. Audit LESSON_COMPLETED lần đầu hoàn thành. */
    PortalLessonProgressDto recordProgress(Long lessonId, Long classId, PortalLessonProgressRequest request);
}
