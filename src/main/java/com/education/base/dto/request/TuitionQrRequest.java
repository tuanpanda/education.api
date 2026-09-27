package com.education.base.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tham số tùy chọn khi sinh VietQR cho một khoản học phí.
 * Bỏ trống thì dùng tài khoản mặc định trong {@code app.payment}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TuitionQrRequest {

    @Size(max = 20, message = "Mã BIN ngân hàng không được vượt quá 20 ký tự")
    private String bankBin;

    @Size(max = 30, message = "Số tài khoản không được vượt quá 30 ký tự")
    private String accountNo;

    @Size(max = 100, message = "Tên chủ tài khoản không được vượt quá 100 ký tự")
    private String accountName;

    @Size(max = 99, message = "Nội dung chuyển khoản không được vượt quá 99 ký tự")
    private String description;
}
