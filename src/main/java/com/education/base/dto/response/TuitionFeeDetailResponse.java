package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Chi tiết một khoản học phí kèm toàn bộ lịch sử giao dịch.
 * <p>
 * Map trực tiếp từ hai Ref Cursor của {@code PRC_GET_TUITION_FEE_DETAIL}:
 * {@code O_FEE_CURSOR} cho phần thông tin chung và {@code O_TRANSACTION_CURSOR}
 * cho danh sách {@link #transactions}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TuitionFeeDetailResponse {

    private Long id;
    private String feeCode;

    private Long studentId;
    private String studentCode;
    private String studentName;

    private Long classId;
    private String classCode;
    private String className;

    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;

    private LocalDate dueDate;
    private String status;
    private String note;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Lịch sử giao dịch, sắp xếp mới nhất trước. */
    @Builder.Default
    private List<PaymentTransactionDto> transactions = new ArrayList<>();

    /** Biên lai, ủy nhiệm chi đính kèm - module {@code TUITION}. */
    @Builder.Default
    private List<FileResponseDto> attachments = new ArrayList<>();
}
