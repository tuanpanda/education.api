package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Sổ công nợ của một học sinh, map từ {@code PRC_RPT_STUDENT_LEDGER}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentLedgerDto {

    private Long studentId;

    private String studentCode;

    private String studentName;

    /** Trạng thái học sinh; {@code DELETED} nếu đã xóa mềm. */
    private String studentStatus;

    /** Tổng ghi nợ (phải thu sau miễn giảm). */
    private BigDecimal totalDebit;

    /** Tổng ghi có (đã thu). */
    private BigDecimal totalCredit;

    /** Số dư cuối: dương là còn nợ, âm là học sinh trả dư. */
    private BigDecimal balance;

    /** Các dòng theo thời gian, kèm số dư lũy kế. */
    @Builder.Default
    private List<StudentLedgerEntryDto> entries = new ArrayList<>();
}
