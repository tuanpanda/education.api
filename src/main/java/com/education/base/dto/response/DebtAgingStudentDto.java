package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Công nợ của một học sinh theo nhóm tuổi nợ ({@code O_STUDENT_CURSOR} của {@code PRC_RPT_DEBT_AGING}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtAgingStudentDto {

    private Long studentId;

    private String studentCode;

    private String studentName;

    /** Trạng thái học sinh; {@code DELETED} nếu đã xóa mềm. */
    private String studentStatus;

    private BigDecimal notDueAmount;

    private BigDecimal due0To30Amount;

    private BigDecimal due31To60Amount;

    private BigDecimal due61To90Amount;

    private BigDecimal dueOver90Amount;

    private BigDecimal totalOutstanding;

    private Long feeCount;

    private LocalDate oldestDueDate;

    private Integer maxDaysPastDue;
}
