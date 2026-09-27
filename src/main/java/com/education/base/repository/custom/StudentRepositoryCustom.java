package com.education.base.repository.custom;

import com.education.base.dto.request.StudentFilterRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentReportDto;

import java.util.List;

/**
 * Các nghiệp vụ tra cứu học sinh phải xử lý bằng Oracle Standalone Procedure
 * (tìm kiếm động nhiều điều kiện, phân trang phía Database).
 */
public interface StudentRepositoryCustom {

    /**
     * Tìm kiếm học sinh theo từ khóa, gọi {@code PRC_SEARCH_STUDENTS}.
     *
     * @param keyword từ khóa theo mã học sinh hoặc họ tên; {@code null} để lấy tất cả.
     * @return danh sách học sinh chưa bị xóa mềm.
     */
    List<StudentReportDto> searchStudents(String keyword);

    /**
     * Tìm kiếm học sinh theo từ khóa và trạng thái, có phân trang,
     * gọi {@code PRC_SEARCH_STUDENTS_PAGING}.
     *
     * @param filter điều kiện tìm kiếm và thông tin phân trang.
     * @return dữ liệu trang hiện tại kèm tổng số dòng khớp điều kiện.
     */
    PageResponse<StudentReportDto> searchWithPaging(StudentFilterRequest filter);
}
