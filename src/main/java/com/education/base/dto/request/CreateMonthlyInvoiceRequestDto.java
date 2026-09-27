package com.education.base.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Sinh hàng loạt phiếu học phí tháng từ điểm danh {@code PRESENT} của lớp.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMonthlyInvoiceRequestDto {

    @NotNull(message = "ID lớp học không được để trống")
    private Long classId;

    @NotNull(message = "Tháng thu không được để trống")
    @Min(value = 1, message = "Tháng thu phải từ 1 đến 12")
    @Max(value = 12, message = "Tháng thu phải từ 1 đến 12")
    private Integer month;

    @NotNull(message = "Năm thu không được để trống")
    @Min(value = 2000, message = "Năm thu không hợp lệ")
    @Max(value = 2100, message = "Năm thu không hợp lệ")
    private Integer year;

    @NotNull(message = "Đơn giá mỗi buổi không được để trống")
    @DecimalMin(value = "0", inclusive = false, message = "Đơn giá mỗi buổi phải lớn hơn 0")
    @Digits(integer = 13, fraction = 2, message = "Đơn giá tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
    private BigDecimal pricePerSession;

    @Size(max = 1000, message = "Nhận xét giáo viên không được vượt quá 1000 ký tự")
    private String teacherComment;

    @Size(max = 500, message = "Lời chúc không được vượt quá 500 ký tự")
    private String footerWish;
}
