package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Xác nhận một khoản thanh toán đã nhận được cho học phí.
 * Bỏ trống {@code amount} thì thu toàn bộ số tiền còn lại.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmPaymentRequest {

    @DecimalMin(value = "0", inclusive = false, message = "Số tiền phải lớn hơn 0")
    @Digits(integer = 13, fraction = 2, message = "Số tiền tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
    private BigDecimal amount;

    @Pattern(regexp = DomainConstants.PAYMENT_METHOD_PATTERN,
            message = "Hình thức thanh toán chỉ nhận: CASH, BANK_TRANSFER, VIETQR, CARD, EWALLET")
    private String paymentMethod;

    @Size(max = 50, message = "Mã giao dịch không được vượt quá 50 ký tự")
    private String transactionCode;

    @Size(max = 100, message = "Mã tham chiếu ngân hàng không được vượt quá 100 ký tự")
    private String bankReferenceNo;

    @Size(max = 255, message = "Ghi chú không được vượt quá 255 ký tự")
    private String note;

    /** Người nộp tiền (in trên phiếu thu). Bỏ trống thì lấy tên phụ huynh, nếu không có thì tên học sinh. */
    @Size(max = 150, message = "Tên người nộp không được vượt quá 150 ký tự")
    private String payerName;
}
