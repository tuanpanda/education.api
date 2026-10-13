package com.education.base.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Lớp gắn với khoá học mẫu ({@code GET /api/v1/lms/courses/{id}/classes}).
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseClassDto {

    private Long classId;

    private String classCode;

    private String className;

    private String status;

    private LocalDate startDate;

    private LocalDate endDate;

    /** Học sinh ENROLLED. */
    private Integer studentCount;
}
