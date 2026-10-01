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
 * Chi tiết một giao dịch: thông tin giao dịch, tóm tắt khoản học phí, các lần hoàn tiền liên quan
 * và (với dòng hoàn tiền) giao dịch thu gốc.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransactionDetailResponse {

    private PaymentTransactionDto transaction;

    private FeeSummary fee;

    /** Các dòng {@code REFUND} của giao dịch thu này (kể cả đã hủy), mới nhất trước. */
    @Builder.Default
    private List<PaymentTransactionDto> refunds = new ArrayList<>();

    /** Giao dịch thu gốc, chỉ có khi {@link #transaction} là dòng {@code REFUND}. */
    private PaymentTransactionDto originalTransaction;

    /** Được phép hủy: {@code SUCCESS} + {@code PAYMENT}, chưa hủy, không có lần hoàn tiền chưa hủy. */
    private boolean voidable;

    /** Được phép hoàn tiền: {@code SUCCESS} + {@code PAYMENT} và còn số tiền có thể hoàn. */
    private boolean refundable;

    /** Tóm tắt khoản học phí của giao dịch. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FeeSummary {
        private Long id;
        private String feeCode;
        private Long studentId;
        private String studentCode;
        private String studentName;
        private Long classId;
        private String classCode;
        private String className;
        private Integer feeMonth;
        private Integer feeYear;
        private BigDecimal totalAmount;
        private BigDecimal discountAmount;
        private BigDecimal paidAmount;
        private BigDecimal remainingAmount;
        private LocalDate dueDate;
        private String status;
    }
}
