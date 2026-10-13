package com.education.base.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code GET /api/v1/lms/assignments?classId&status&keyword&pageNo&pageSize}. Phân trang 1-based như thông báo.
 * Giáo viên bị giới hạn chỉ thấy lớp mình dạy (lọc ở server, bỏ qua {@code classId} ngoài phạm vi -> trang rỗng).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentFilterRequest {

    private Long classId;

    /** DRAFT / PUBLISHED / CLOSED; rỗng = tất cả. */
    private String status;

    private String keyword;

    @Builder.Default
    private Integer pageNo = 1;

    @Builder.Default
    private Integer pageSize = 20;
}
