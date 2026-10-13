package com.education.base.service.lesson;

import com.education.base.dto.request.CourseFilterRequest;
import com.education.base.dto.request.CourseUpsertRequest;
import com.education.base.dto.response.CourseClassDto;
import com.education.base.dto.response.CourseCopyResultDto;
import com.education.base.dto.response.CourseDto;
import com.education.base.dto.response.PageResponse;

import java.util.List;

/**
 * Khoá học mẫu phía nhân viên ({@code /api/v1/lms/courses/**}, quyền {@code MENU_COURSE:*}). Bot 1 viết
 * {@code CourseServiceImpl}. Khoá mẫu KHÔNG giới hạn theo lớp (giáo viên chỉ có VIEW theo V21_4).
 * Không tồn tại / đã xoá -> 404 {@code COURSE_NOT_FOUND}. Quy tắc: {@code docs/portal-p3-api.md} §2.1.
 */
public interface CourseService {

    PageResponse<CourseDto> search(CourseFilterRequest filter);

    CourseDto getById(Long id);

    /** Mã trùng (không phân biệt hoa thường, dòng chưa xoá) -> 400 {@code COURSE_CODE_DUPLICATE}. */
    CourseDto create(CourseUpsertRequest request);

    CourseDto update(Long id, CourseUpsertRequest request);

    /** Xoá mềm khoá + chương/bài/tài nguyên của khoá; còn lớp gắn -> 400 {@code COURSE_IN_USE}. */
    void softDelete(Long id);

    List<CourseClassDto> listClasses(Long id);

    /**
     * Gắn lớp vào khoá ({@code EDU_CLASSES.COURSE_ID = id}) - liên kết sống: lớp thấy bài của khoá ngay khi đăng.
     * Khoá ARCHIVED / lớp đã gắn khoá khác -> 400 {@code COURSE_INVALID_STATE}. Gắn lại cùng khoá = không đổi.
     */
    CourseClassDto linkClass(Long id, Long classId);

    /** Gỡ lớp (COURSE_ID = NULL). Tiến độ học sinh giữ nguyên (bài khoá không còn hiện với lớp). */
    void unlinkClass(Long id, Long classId);

    /**
     * Chép chương/bài/tài nguyên của khoá thành bản RIÊNG của lớp (SOURCE_*_ID trỏ về bản gốc, bài về DRAFT, FILE dùng
     * chung FILE_ID). Không gắn lớp vào khoá. Giáo viên cần phạm vi lớp + MENU_LESSON:CREATE (kiểm tra trong service).
     */
    CourseCopyResultDto copyToClass(Long id, Long classId);
}
