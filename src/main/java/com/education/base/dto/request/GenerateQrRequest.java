package com.education.base.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request sinh mã VietQR thanh toán học phí.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateQrRequest {

    /** Bỏ trống: dùng STK đang sử dụng trong {@code FIN_BANK_ACCOUNTS}. */
    @Size(max = 20, message = "Mã ngân hàng (BIN) không được vượt quá 20 ký tự")
    private String bankBin;

    /** Bỏ trống: dùng STK đang sử dụng trong {@code FIN_BANK_ACCOUNTS}. */
    @Size(max = 30, message = "Số tài khoản không được vượt quá 30 ký tự")
    private String accountNo;

    /**
     * Tên chủ tài khoản, chỉ dùng để hiển thị trên ảnh QR của link nhanh.
     */
    @Size(max = 100, message = "Tên chủ tài khoản không được vượt quá 100 ký tự")
    private String accountName;

    /**
     * Số tiền học phí (VND). Để trống sẽ sinh QR tĩnh, người chuyển tự nhập số tiền.
     */
    @Positive(message = "Số tiền phải lớn hơn 0")
    private Long amount;

    @Size(max = 99, message = "Nội dung chuyển khoản không được vượt quá 99 ký tự")
    private String description;
}
