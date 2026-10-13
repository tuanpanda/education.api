package com.education.base.service.lesson;

import com.education.base.dto.request.LessonReorderRequest;
import com.education.base.dto.request.LessonResourceReorderRequest;
import com.education.base.dto.request.LessonResourceRequest;
import com.education.base.dto.request.LessonSectionUpsertRequest;
import com.education.base.dto.request.LessonUpsertRequest;
import com.education.base.dto.response.AnnouncementClassOptionDto;
import com.education.base.dto.response.ClassLessonProgressDto;
import com.education.base.dto.response.LessonDto;
import com.education.base.dto.response.LessonResourceDto;
import com.education.base.dto.response.LessonSectionDto;
import com.education.base.dto.response.LessonTreeDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Chương / bài học / tài nguyên phía nhân viên ({@code /api/v1/lms/lessons/**}, {@code /lms/lesson-sections/**},
 * {@code /lms/lesson-resources/**}, {@code /lms/classes/{id}/lesson-progress}). Bot 1 viết {@code LessonServiceImpl}.
 * <p>
 * Quyền theo CHỦ của chương/bài (§2.2 hợp đồng):
 * <ul>
 *   <li>Chủ CLASS: {@code MENU_LESSON:*} + {@code TeachingAssignmentGuard} (giáo viên ngoài lớp -> 403 NOT_CLASS_TEACHER).</li>
 *   <li>Chủ COURSE: ghi cần {@code MENU_COURSE:UPDATE}; đọc cần {@code MENU_COURSE:VIEW} hoặc {@code MENU_LESSON:VIEW}.</li>
 * </ul>
 * Controller gắn {@code @RequirePermission} rộng (VIEW/CREATE/UPDATE/DELETE của MENU_LESSON hoặc MENU_COURSE); service
 * kiểm tra đúng nhánh theo chủ. Không tồn tại / đã xoá -> 404 {@code LESSON_*_NOT_FOUND}.
 */
public interface LessonService {

    /** {@code ownerType} COURSE / CLASS. CLASS kèm {@code inheritedSections} của khoá mẫu đang gắn. */
    LessonTreeDto getTree(String ownerType, Long ownerId);

    /** Lớp người gọi soạn được bài (giáo viên: lớp mình; nhân viên khác: lớp đang hoạt động). */
    List<AnnouncementClassOptionDto> listManageableClasses();

    LessonSectionDto createSection(LessonSectionUpsertRequest request);

    LessonSectionDto updateSection(Long sectionId, LessonSectionUpsertRequest request);

    /** Còn bài chưa xoá -> 400 {@code LESSON_SECTION_NOT_EMPTY}. */
    void deleteSection(Long sectionId);

    LessonDto getById(Long id);

    /** Tạo DRAFT ở cuối chương. */
    LessonDto create(LessonUpsertRequest request);

    LessonDto update(Long id, LessonUpsertRequest request);

    /** Xoá mềm bài + tài nguyên; file LESSON chỉ xoá mềm khi không còn tài nguyên nào khác dùng FILE_ID. Tiến độ giữ. */
    void softDelete(Long id);

    /** DRAFT -> PUBLISHED, đặt publishedAt lần đầu. */
    LessonDto publish(Long id);

    /** PUBLISHED -> DRAFT (ẩn khỏi cổng; tiến độ giữ). */
    LessonDto unpublish(Long id);

    LessonTreeDto reorder(LessonReorderRequest request);

    /** VIDEO / LINK / ASSIGNMENT. VIDEO: URL qua {@link VideoEmbedResolver}. */
    LessonResourceDto addResource(Long lessonId, LessonResourceRequest request);

    /** FILE: lưu qua FileLifecycleService (module LESSON, ref = lessonId) rồi tạo dòng tài nguyên. */
    LessonResourceDto uploadResourceFile(Long lessonId, MultipartFile file, String title);

    LessonResourceDto updateResource(Long resourceId, LessonResourceRequest request);

    void deleteResource(Long resourceId);

    List<LessonResourceDto> reorderResources(Long lessonId, LessonResourceReorderRequest request);

    /** Ma trận tiến độ: quyền MENU_LESSON:VIEW + phạm vi lớp. */
    ClassLessonProgressDto getClassProgress(Long classId);
}
