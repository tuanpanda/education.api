package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.DecimalMax;
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
 * Request nhập hoặc sửa một đầu điểm của học sinh trong lớp.
 * <p>
 * Miền giá trị {@code score} khớp ràng buộc {@code CK_GRADES_SCORE (SCORE BETWEEN 0 AND 10)}
 * theo thang điểm 10 của Việt Nam.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradeUpsertRequest {

    @NotNull(message = "ID lớp học không được để trống")
    private Long classId;

    @NotNull(message = "ID học sinh không được để trống")
    private Long studentId;

    @NotBlank(message = "Loại điểm không được để trống")
    @Pattern(regexp = DomainConstants.GRADE_TYPE_PATTERN,
            message = "Loại điểm chỉ nhận: ASSIGNMENT, QUIZ, MIDTERM, FINAL")
    private String gradeType;

    @NotNull(message = "Điểm không được để trống")
    @DecimalMin(value = "0", message = "Điểm không được nhỏ hơn 0")
    @DecimalMax(value = "10", message = "Điểm không được lớn hơn 10")
    @Digits(integer = 3, fraction = 2, message = "Điểm tối đa 2 chữ số thập phân")
    private BigDecimal score;

    /** Hệ số của đầu điểm, phải lớn hơn 0 ({@code CK_GRADES_WEIGHT}). */
    @DecimalMin(value = "0", inclusive = false, message = "Hệ số phải lớn hơn 0")
    @Digits(integer = 3, fraction = 2, message = "Hệ số tối đa 2 chữ số thập phân")
    private BigDecimal weight;

    private LocalDate examDate;

    @Size(max = 255, message = "Ghi chú không được vượt quá 255 ký tự")
    private String note;
}
