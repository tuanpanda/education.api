package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một giao dịch thanh toán.
 * <p>
 * Các trường khớp cột {@code O_TRANSACTION_CURSOR} của {@code PRC_GET_TUITION_FEE_DETAIL}.
 * Các trường bổ sung từ V14_2 (phiếu thu, loại giao dịch, hủy / hoàn tiền) và các trường hiển thị
 * (khoản phí, học sinh, lớp) chỉ được điền bởi API {@code /api/v1/payments/transactions}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransactionDto {

    private Long id;
    private String transactionCode;
    private Long tuitionFeeId;
    private BigDecimal amount;

    /** {@code CASH}, {@code BANK_TRANSFER}, {@code VIETQR}, {@code CARD}, {@code EWALLET}. */
    private String paymentMethod;

    private LocalDateTime paymentDate;
    private String bankBin;
    private String accountNo;
    private String bankReferenceNo;

    /** {@code PENDING}, {@code SUCCESS}, {@code FAILED}, {@code REFUNDED}, {@code VOIDED} (V14_2). */
    private String status;

    private String note;

    // ---- V14_2: phiếu thu, loại giao dịch, hủy / hoàn tiền -----------------------------------

    /** Số phiếu thu / phiếu chi, ví dụ {@code PT20261000001}. */
    private String receiptNo;

    /** {@code PAYMENT} (thu) hoặc {@code REFUND} (hoàn tiền, số dương). */
    private String transactionType;

    /** Người nộp tiền (thu) / người nhận tiền (hoàn). */
    private String payerName;

    private LocalDateTime voidedAt;
    private String voidedBy;
    private String voidReason;

    /** Giao dịch thu gốc (chỉ có ở dòng {@code REFUND}). */
    private Long refTransactionId;

    /** Thu ngân / người ghi nhận giao dịch. */
    private String createdBy;

    private LocalDateTime createdAt;

    /** Tổng các lần hoàn tiền chưa hủy của giao dịch thu này (dòng {@code PAYMENT}). */
    private BigDecimal refundedAmount;

    /** Số tiền còn có thể hoàn = {@code amount - refundedAmount} (dòng {@code PAYMENT} đang {@code SUCCESS}). */
    private BigDecimal refundableAmount;

    // ---- Thông tin hiển thị (tra cứu giao dịch) ----------------------------------------------

    private String feeCode;
    private Long studentId;
    private String studentCode;
    private String studentName;
    private Long classId;
    private String classCode;
    private String className;
}
