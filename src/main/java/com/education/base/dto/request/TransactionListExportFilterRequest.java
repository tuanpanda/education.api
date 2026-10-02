package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Bộ lọc xuất Excel danh sách giao dịch thanh toán ({@code GET /api/v1/reports/export/payment-transactions}).
 * <p>
 * Cùng tên tham số với tra cứu giao dịch ({@code /api/v1/payments/transactions/search}): khoảng ngày thanh toán,
 * hình thức, trạng thái (kể cả {@code VOIDED}), loại giao dịch, học sinh, lớp, mã khoản phí, số phiếu và từ khóa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionListExportFilterRequest {

    /** Ngày thanh toán từ (bao gồm). */
    private LocalDate fromDate;

    /** Ngày thanh toán đến (bao gồm cả ngày này). */
    private LocalDate toDate;

    @Pattern(regexp = DomainConstants.PAYMENT_METHOD_PATTERN,
            message = "Hình thức chỉ nhận: CASH, BANK_TRANSFER, VIETQR, CARD, EWALLET")
    private String paymentMethod;

    @Pattern(regexp = DomainConstants.TRANSACTION_STATUS_FILTER_PATTERN,
            message = "Trạng thái chỉ nhận: PENDING, SUCCESS, FAILED, REFUNDED, VOIDED")
    private String status;

    @Pattern(regexp = DomainConstants.TRANSACTION_TYPE_PATTERN, message = "Loại giao dịch chỉ nhận: PAYMENT, REFUND")
    private String transactionType;

    private Long studentId;

    private Long classId;

    /** Mã khoản học phí (tìm gần đúng, không phân biệt hoa thường). */
    @Size(max = 30, message = "Mã khoản phí không được vượt quá 30 ký tự")
    private String feeCode;

    /** Số phiếu thu / phiếu chi (tìm gần đúng). */
    @Size(max = 30, message = "Số phiếu thu không được vượt quá 30 ký tự")
    private String receiptNo;

    /** Tìm theo mã giao dịch, số phiếu, mã tham chiếu ngân hàng, mã / họ tên học sinh hoặc người nộp. */
    @Size(max = 100, message = "Từ khóa tìm kiếm không được vượt quá 100 ký tự")
    private String keyword;
}
