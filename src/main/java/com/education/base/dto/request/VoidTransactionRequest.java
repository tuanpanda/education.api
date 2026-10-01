package com.education.base.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hủy (void) một giao dịch thu: bắt buộc nêu lý do.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoidTransactionRequest {

    @NotBlank(message = "Lý do hủy giao dịch không được để trống")
    @Size(max = 255, message = "Lý do hủy không được vượt quá 255 ký tự")
    private String reason;
}
