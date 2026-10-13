package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả {@code POST /api/v1/lms/courses/{id}/copy-to-class/{classId}}: chép chương/bài/tài nguyên thành bản của lớp
 * (bài chép về DRAFT, file dùng chung FILE_ID, không nhân bản blob).
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseCopyResultDto {

    private Long courseId;

    private Long classId;

    private Integer sectionCount;

    private Integer lessonCount;

    private Integer resourceCount;
}
