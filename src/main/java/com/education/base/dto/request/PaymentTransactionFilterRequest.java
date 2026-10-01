package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Điều kiện tra cứu giao dịch thanh toán ({@code FIN_PAYMENT_TRANSACTIONS}), có phân trang.
 * Mặc định sắp xếp theo {@code PAYMENT_DATE} giảm dần; luôn loại bỏ dòng {@code IS_DELETED = 1}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransactionFilterRequest {

    public static final int DEFAULT_PAGE_NO = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 200;

    /** Từ ngày thanh toán (bao gồm). */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    /** Đến ngày thanh toán (bao gồm cả ngày này). */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    @Pattern(regexp = DomainConstants.PAYMENT_METHOD_PATTERN,
            message = "Hình thức thanh toán chỉ nhận: CASH, BANK_TRANSFER, VIETQR, CARD, EWALLET")
    private String paymentMethod;

    @Pattern(regexp = DomainConstants.TRANSACTION_STATUS_FILTER_PATTERN,
            message = "Trạng thái giao dịch chỉ nhận: PENDING, SUCCESS, FAILED, REFUNDED, VOIDED")
    private String status;

    @Pattern(regexp = DomainConstants.TRANSACTION_TYPE_PATTERN,
            message = "Loại giao dịch chỉ nhận: PAYMENT, REFUND")
    private String transactionType;

    private Long studentId;

    private Long classId;

    /** Tìm gần đúng theo mã khoản học phí. */
    @Size(max = 50, message = "Mã khoản học phí không được vượt quá 50 ký tự")
    private String feeCode;

    /** Tìm gần đúng theo số phiếu thu. */
    @Size(max = 30, message = "Số phiếu thu không được vượt quá 30 ký tự")
    private String receiptNo;

    /** Tìm gần đúng theo mã giao dịch, mã / họ tên học sinh hoặc người nộp. */
    @Size(max = 100, message = "Từ khóa tìm kiếm không được vượt quá 100 ký tự")
    private String keyword;

    @Min(value = 1, message = "Số trang phải lớn hơn hoặc bằng 1")
    @Builder.Default
    private Integer pageNo = DEFAULT_PAGE_NO;

    @Min(value = 1, message = "Số dòng mỗi trang phải lớn hơn hoặc bằng 1")
    @Max(value = MAX_PAGE_SIZE, message = "Số dòng mỗi trang không được vượt quá 200")
    @Builder.Default
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    public int resolvePageNo() {
        return (pageNo == null || pageNo < 1) ? DEFAULT_PAGE_NO : pageNo;
    }

    public int resolvePageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
