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
 * Chỉ dùng các cột có sẵn của {@code FIN_PAYMENT_TRANSACTIONS} (trước V14_2): khoảng ngày thanh toán, hình thức,
 * trạng thái, học sinh, lớp, mã khoản phí và từ khóa. Bộ lọc loại giao dịch / số phiếu thu được bổ sung sau khi
 * Stream B merge.
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

    @Pattern(regexp = DomainConstants.TRANSACTION_STATUS_PATTERN,
            message = "Trạng thái chỉ nhận: PENDING, SUCCESS, FAILED, REFUNDED")
    private String status;

    private Long studentId;

    private Long classId;

    /** Mã khoản học phí (tìm gần đúng, không phân biệt hoa thường). */
    @Size(max = 30, message = "Mã khoản phí không được vượt quá 30 ký tự")
    private String feeCode;

    /** Tìm theo mã giao dịch, mã tham chiếu ngân hàng, mã hoặc họ tên học sinh. */
    @Size(max = 100, message = "Từ khóa tìm kiếm không được vượt quá 100 ký tự")
    private String keyword;
}
