package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Điểm trên cổng. Không trả {@code NOTE} nội bộ từ {@code EDU_GRADES}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalGradeDto {

    private Long id;

    private Long classId;

    private String classCode;

    private String className;

    /** {@code ASSIGNMENT}, {@code QUIZ}, {@code MIDTERM} hoặc {@code FINAL}. */
    private String gradeType;

    private BigDecimal score;

    private BigDecimal weight;

    private LocalDate examDate;
}
