package com.education.base.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code GET /api/v1/lms/assignments/{id}/submissions}. Thứ tự rows = tên học sinh (dùng cho phím 'học sinh kế tiếp').
 * Hợp đồng Luồng 0 (docs/homework-api.md). Thêm trường được (Bot 1 + Bot 2 cùng cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionMatrixDto {

    private AssignmentDto assignment;
    private AssignmentStatsDto stats;
    private List<SubmissionRowDto> rows;
}
