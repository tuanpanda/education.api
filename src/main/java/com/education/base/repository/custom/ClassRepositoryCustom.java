package com.education.base.repository.custom;

import com.education.base.dto.request.ClassFilterRequest;
import com.education.base.dto.response.ClassReportDto;
import com.education.base.dto.response.PageResponse;

/**
 * Tra cứu lớp học động qua Standalone Procedure {@code PRC_SEARCH_CLASSES_PAGING}.
 */
public interface ClassRepositoryCustom {

    /**
     * Tìm lớp học theo từ khóa, trạng thái và giảng viên, có phân trang.
     *
     * @param filter điều kiện tìm kiếm; {@code null} thì dùng giá trị mặc định.
     * @return trang dữ liệu kèm tổng số dòng khớp điều kiện.
     */
    PageResponse<ClassReportDto> searchWithPaging(ClassFilterRequest filter);
}
