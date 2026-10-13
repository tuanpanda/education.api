package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Đếm trạng thái bài nộp của một bài (staff).
 * Hợp đồng Luồng 0 (docs/homework-api.md). Thêm trường được (Bot 1 + Bot 2 cùng cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentStatsDto {

    private int totalStudents;
    /** Chưa nộp (gồm DRAFT), chưa quá hạn. */
    private int notSubmitted;
    /** Quá hạn mà chưa nộp. */
    private int missing;
    /** SUBMITTED chưa chấm (gồm cả nộp muộn). */
    private int submitted;
    /** Trong {@link #submitted} + {@link #graded}: số bài nộp muộn. */
    private int late;
    private int returned;
    private int graded;
}
