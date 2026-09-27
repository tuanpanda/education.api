package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một đầu điểm của học sinh ({@code EDU_GRADES}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradeResponseDto {

    private Long id;

    private Long classId;
    private String classCode;
    private String className;

    private Long studentId;
    private String studentCode;
    private String fullName;

    /** {@code ASSIGNMENT}, {@code QUIZ}, {@code MIDTERM} hoặc {@code FINAL}. */
    private String gradeType;

    private BigDecimal score;
    private BigDecimal weight;
    private LocalDate examDate;
    private String note;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
