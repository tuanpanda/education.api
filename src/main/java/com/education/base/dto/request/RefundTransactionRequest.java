package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Hoàn tiền (một phần hoặc toàn bộ) cho một giao dịch thu {@code SUCCESS}.
 * Tạo dòng mới {@code TRANSACTION_TYPE = REFUND} với số tiền dương.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundTransactionRequest {

    @NotNull(message = "Số tiền hoàn không được để trống")
    @DecimalMin(value = "0", inclusive = false, message = "Số tiền hoàn phải lớn hơn 0")
    @Digits(integer = 13, fraction = 2, message = "Số tiền tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
    private BigDecimal amount;

    @NotBlank(message = "Hình thức hoàn tiền không được để trống")
    @Pattern(regexp = DomainConstants.PAYMENT_METHOD_PATTERN,
            message = "Hình thức hoàn tiền chỉ nhận: CASH, BANK_TRANSFER, VIETQR, CARD, EWALLET")
    private String method;

    @NotBlank(message = "Lý do hoàn tiền không được để trống")
    @Size(max = 255, message = "Lý do hoàn tiền không được vượt quá 255 ký tự")
    private String reason;

    /** Người nhận tiền hoàn. Bỏ trống thì lấy người nộp của giao dịch gốc. */
    @Size(max = 150, message = "Tên người nhận không được vượt quá 150 ký tự")
    private String payerName;
}
