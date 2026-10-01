package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một miễn giảm - học bổng của học sinh ({@code FIN_STUDENT_DISCOUNTS}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDiscountDto {

    private Long id;

    private Long studentId;
    private String studentCode;
    private String studentName;

    /** {@code null} = áp dụng cho mọi lớp. */
    private Long classId;
    private String classCode;
    private String className;

    /** {@code PERCENT} hoặc {@code AMOUNT}. */
    private String discountType;
    private BigDecimal discountValue;

    private LocalDate validFrom;
    private LocalDate validTo;
    private String reason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
