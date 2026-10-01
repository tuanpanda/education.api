package com.education.base.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request cập nhật một khoản học phí ({@code PUT /api/v1/tuition-fees/{id}}).
 * <p>
 * Thay thế TOÀN BỘ các trường được phép sửa: tổng tiền, tiền giảm, hạn thu, ghi chú.
 * {@code discountAmount = null} nghĩa là 0; {@code dueDate} / {@code note} = null nghĩa là xóa giá trị cũ.
 * Tầng Service kiểm tra {@code discountAmount <= totalAmount} ({@code CK_FEES_DISCOUNT}) và
 * {@code totalAmount - discountAmount >= paidAmount} (không tạo tiền thừa).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TuitionFeeUpdateRequest {

    @NotNull(message = "Tổng số tiền không được để trống")
    @DecimalMin(value = "0", message = "Tổng số tiền không được là số âm")
    @Digits(integer = 13, fraction = 2, message = "Tổng số tiền tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
    private BigDecimal totalAmount;

    @DecimalMin(value = "0", message = "Số tiền giảm không được là số âm")
    @Digits(integer = 13, fraction = 2, message = "Số tiền giảm tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
    private BigDecimal discountAmount;

    private LocalDate dueDate;

    @Size(max = 255, message = "Ghi chú không được vượt quá 255 ký tự")
    private String note;
}
