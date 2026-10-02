package com.education.base.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request hủy một khoản học phí ({@code POST /api/v1/tuition-fees/{id}/cancel}).
 * Chỉ hủy được khi {@code PAID_AMOUNT = 0}; đã thu tiền thì phải hoàn tiền (Stream B) trước.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TuitionFeeCancelRequest {

    @NotBlank(message = "Lý do hủy không được để trống")
    @Size(max = 255, message = "Lý do hủy không được vượt quá 255 ký tự")
    private String reason;
}
