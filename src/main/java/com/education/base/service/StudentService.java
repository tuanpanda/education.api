package com.education.base.service;

import com.education.base.dto.request.StudentCreateRequest;
import com.education.base.dto.request.StudentFilterRequest;
import com.education.base.dto.request.StudentUpdateRequest;
import com.education.base.dto.response.FileResponseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentDetailResponse;
import com.education.base.dto.response.StudentReportDto;
import org.springframework.web.multipart.MultipartFile;

/**
 * Nghiệp vụ Module Quản lý Học sinh (menu {@code MENU_STUDENT_LIST}).
 */
public interface StudentService {

    /**
     * Tên module dùng khi lưu tài liệu đính kèm của học sinh.
     */
    String MODULE_NAME = "STUDENT";

    /**
     * Tìm kiếm học sinh có phân trang qua Standalone Procedure
     * {@code PRC_SEARCH_STUDENTS_PAGING}.
     *
     * @param filter điều kiện tìm kiếm và phân trang.
     * @return trang dữ liệu kèm tổng số dòng.
     */
    PageResponse<StudentReportDto> search(StudentFilterRequest filter);

    /**
     * Lấy chi tiết học sinh kèm danh sách tài liệu đính kèm.
     *
     * @param id ID học sinh.
     * @return chi tiết học sinh.
     */
    StudentDetailResponse getDetail(Long id);

    /**
     * Thêm mới học sinh. {@code studentCode} do {@code FN_NEXT_BIZ_CODE} sinh theo
     * quy luật bảng {@code SYS_CODE_RULES}; {@code ID} do sequence/trigger.
     *
     * @param request dữ liệu học sinh mới.
     * @return chi tiết học sinh vừa tạo.
     */
    StudentDetailResponse create(StudentCreateRequest request);

    /**
     * Cập nhật thông tin học sinh còn hiệu lực.
     *
     * @param id      ID học sinh.
     * @param request dữ liệu cập nhật.
     * @return chi tiết học sinh sau cập nhật.
     */
    StudentDetailResponse update(Long id, StudentUpdateRequest request);

    /**
     * Xóa mềm học sinh bằng cách đặt {@code IS_DELETED = 1}.
     *
     * @param id ID học sinh.
     */
    void softDelete(Long id);

    /**
     * Tải tài liệu hồ sơ đính kèm cho học sinh, lưu vật lý dưới module {@code STUDENT}.
     *
     * @param id   ID học sinh.
     * @param file file tài liệu.
     * @return metadata file đã lưu kèm link xem/tải.
     */
    FileResponseDto uploadDocument(Long id, MultipartFile file);
}
