package com.education.base.dto.request;

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
import java.time.LocalDate;

/**
 * Request tạo một khoản học phí cho học sinh.
 * <p>
 * Ràng buộc {@code CK_FEES_DISCOUNT} yêu cầu {@code discountAmount <= totalAmount};
 * tầng Service phải kiểm tra điều kiện liên trường này vì Jakarta Validation ở mức field
 * không so sánh được hai trường với nhau.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TuitionFeeCreateRequest {

    @NotBlank(message = "Mã khoản học phí không được để trống")
    @Size(max = 30, message = "Mã khoản học phí không được vượt quá 30 ký tự")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$",
            message = "Mã khoản học phí chỉ được chứa chữ, số, dấu gạch ngang và gạch dưới")
    private String feeCode;

    @NotNull(message = "ID học sinh không được để trống")
    private Long studentId;

    /** Lớp học phát sinh khoản phí; có thể để trống với các khoản phí chung. */
    private Long classId;

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
