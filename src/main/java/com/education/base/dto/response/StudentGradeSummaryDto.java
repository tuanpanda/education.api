package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Bảng điểm tổng hợp của một học sinh trong một lớp.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentGradeSummaryDto {

    private Long classId;
    private String classCode;
    private String className;

    private Long studentId;
    private String studentCode;
    private String fullName;

    /** Chi tiết từng đầu điểm. */
    @Builder.Default
    private List<GradeResponseDto> grades = new ArrayList<>();

    /** Điểm trung bình có tính hệ số: {@code SUM(score * weight) / SUM(weight)}. */
    private BigDecimal weightedAverage;

    /** Số buổi có mặt và tổng số buổi đã điểm danh, phục vụ điều kiện dự thi. */
    private Integer presentSessions;
    private Integer totalSessions;
}
