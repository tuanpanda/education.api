package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Báo cáo tuổi nợ tại ngày chốt, map từ {@code PRC_RPT_DEBT_AGING}.
 * <p>
 * Gồm cả học sinh không còn {@code ACTIVE} hoặc đã xóa mềm (B8 - phía báo cáo). Nhóm tuổi nợ xem
 * {@code DomainConstants.DEBT_AGING_BUCKETS}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtAgingDto {

    /** Ngày chốt thực tế đã áp dụng. */
    private LocalDate asOfDate;

    /** Chưa tới hạn (hoặc không có hạn thu). */
    private BigDecimal notDueAmount;

    /** Quá hạn 1-30 ngày. */
    private BigDecimal due0To30Amount;

    /** Quá hạn 31-60 ngày. */
    private BigDecimal due31To60Amount;

    /** Quá hạn 61-90 ngày. */
    private BigDecimal due61To90Amount;

    /** Quá hạn trên 90 ngày. */
    private BigDecimal dueOver90Amount;

    /** Tổng còn phải thu. */
    private BigDecimal totalOutstanding;

    private Long feeCount;

    private Long studentCount;

    /** Mỗi học sinh còn nợ một dòng, nợ lâu nhất trước. */
    @Builder.Default
    private List<DebtAgingStudentDto> students = new ArrayList<>();

    /** Mỗi khoản phí còn nợ một dòng. */
    @Builder.Default
    private List<DebtAgingFeeDto> fees = new ArrayList<>();
}
