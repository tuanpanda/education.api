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
 * Sinh phiếu học phí theo khoảng ngày cho cả lớp ({@code POST /api/v1/tuition-fees/generate-range}).
 * <p>
 * {@code fromDate <= toDate} và khoảng tối đa {@code DomainConstants.FEE_RANGE_MAX_DAYS} ngày được kiểm ở Service
 * (400 {@code VALIDATION_ERROR}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateRangeInvoicesRequestDto {

    @NotNull(message = "ID lớp học không được để trống")
    private Long classId;

    @NotNull(message = "Từ ngày không được để trống")
    private LocalDate fromDate;

    @NotNull(message = "Đến ngày không được để trống")
    private LocalDate toDate;

    @NotNull(message = "Đơn giá mỗi buổi không được để trống")
    @DecimalMin(value = "0", inclusive = false, message = "Đơn giá mỗi buổi phải lớn hơn 0")
    @Digits(integer = 13, fraction = 2, message = "Đơn giá tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
    private BigDecimal pricePerSession;

    @Size(max = 1000, message = "Nhận xét giáo viên không được vượt quá 1000 ký tự")
    private String teacherComment;

    @Size(max = 500, message = "Lời chúc không được vượt quá 500 ký tự")
    private String footerWish;
}
