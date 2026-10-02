package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request tạo / cập nhật một miễn giảm - học bổng ({@code FIN_STUDENT_DISCOUNTS}).
 * <p>
 * Ràng buộc liên trường do tầng Service kiểm tra (khớp CHECK của V14_1):
 * {@code PERCENT} thì {@code 0 < discountValue <= 100}; {@code validTo >= validFrom}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDiscountUpsertRequest {

    @NotNull(message = "ID học sinh không được để trống")
    private Long studentId;

    /** Bỏ trống = áp dụng cho mọi lớp của học sinh. */
    private Long classId;

    @NotBlank(message = "Loại miễn giảm không được để trống")
    @Pattern(regexp = DomainConstants.DISCOUNT_TYPE_PATTERN, message = "Loại miễn giảm chỉ nhận: PERCENT, AMOUNT")
    private String discountType;

    @NotNull(message = "Giá trị miễn giảm không được để trống")
    @DecimalMin(value = "0", inclusive = false, message = "Giá trị miễn giảm phải lớn hơn 0")
    @Digits(integer = 13, fraction = 2, message = "Giá trị miễn giảm tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
    private BigDecimal discountValue;

    @NotNull(message = "Ngày bắt đầu hiệu lực không được để trống")
    private LocalDate validFrom;

    /** Bỏ trống = không thời hạn. */
    private LocalDate validTo;

    @Size(max = 255, message = "Lý do không được vượt quá 255 ký tự")
    private String reason;
}
