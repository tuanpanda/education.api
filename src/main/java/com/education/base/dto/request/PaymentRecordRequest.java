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
import java.time.LocalDateTime;

/**
 * Request ghi nhận một giao dịch thanh toán học phí.
 * <p>
 * Khi giao dịch chuyển sang trạng thái {@code SUCCESS}, tầng Service phải cộng dồn
 * {@code PAID_AMOUNT} và tính lại {@code STATUS} của khoản học phí trong cùng transaction,
 * nếu không hai bảng sẽ lệch số liệu.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRecordRequest {

    @NotNull(message = "ID khoản học phí không được để trống")
    private Long tuitionFeeId;

    /**
     * Mã giao dịch. Bỏ trống thì Service tự sinh; nếu truyền lên phải là duy nhất
     * ({@code UQ_FIN_TRANS_CODE}) và nên dùng mã đối soát của ngân hàng để chống ghi trùng.
     */
    @Size(max = 50, message = "Mã giao dịch không được vượt quá 50 ký tự")
    private String transactionCode;

    @NotNull(message = "Số tiền không được để trống")
    @DecimalMin(value = "0", inclusive = false, message = "Số tiền phải lớn hơn 0")
    @Digits(integer = 13, fraction = 2, message = "Số tiền tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
    private BigDecimal amount;

    @NotBlank(message = "Hình thức thanh toán không được để trống")
    @Pattern(regexp = DomainConstants.PAYMENT_METHOD_PATTERN,
            message = "Hình thức thanh toán chỉ nhận: CASH, BANK_TRANSFER, VIETQR, CARD, EWALLET")
    private String paymentMethod;

    /** Thời điểm thanh toán; bỏ trống thì lấy thời điểm hiện tại. */
    private LocalDateTime paymentDate;

    @Size(max = 20, message = "Mã BIN ngân hàng không được vượt quá 20 ký tự")
    private String bankBin;

    @Size(max = 30, message = "Số tài khoản không được vượt quá 30 ký tự")
    private String accountNo;

    @Size(max = 100, message = "Mã tham chiếu ngân hàng không được vượt quá 100 ký tự")
    private String bankReferenceNo;

    @Pattern(regexp = DomainConstants.TRANSACTION_STATUS_PATTERN,
            message = "Trạng thái giao dịch chỉ nhận: PENDING, SUCCESS, FAILED, REFUNDED")
    private String status;

    @Size(max = 255, message = "Ghi chú không được vượt quá 255 ký tự")
    private String note;
}
