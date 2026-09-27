package com.education.base.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountUpsertRequest {

    @NotBlank(message = "Mã tài khoản không được để trống")
    @Size(max = 30, message = "Mã tài khoản không được vượt quá 30 ký tự")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$",
            message = "Mã tài khoản chỉ được chứa chữ, số, gạch ngang và gạch dưới")
    private String accountCode;

    @NotBlank(message = "Mã BIN ngân hàng không được để trống")
    @Size(max = 20, message = "Mã BIN không được vượt quá 20 ký tự")
    @Pattern(regexp = "^[0-9]{6,20}$", message = "Mã BIN phải gồm 6–20 chữ số")
    private String bankBin;

    @NotBlank(message = "Tên ngân hàng không được để trống")
    @Size(max = 100, message = "Tên ngân hàng không được vượt quá 100 ký tự")
    private String bankName;

    @NotBlank(message = "Số tài khoản không được để trống")
    @Size(max = 30, message = "Số tài khoản không được vượt quá 30 ký tự")
    @Pattern(regexp = "^[0-9A-Za-z]{6,30}$", message = "Số tài khoản không hợp lệ")
    private String accountNo;

    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    @Size(max = 150, message = "Tên chủ tài khoản không được vượt quá 150 ký tự")
    private String accountName;

    /** true = đặt làm STK đang sử dụng (các STK khác sẽ tắt). */
    private Boolean active;

    @Size(max = 255, message = "Ghi chú không được vượt quá 255 ký tự")
    private String note;
}
