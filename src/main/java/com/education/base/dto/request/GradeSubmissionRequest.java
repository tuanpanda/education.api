package com.education.base.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** {@code PUT /api/v1/lms/assignments/submissions/{sid}/grade}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeSubmissionRequest {

    @NotNull(message = "Vui lòng nhập điểm")
    @DecimalMin(value = "0", message = "Điểm không được âm")
    @Digits(integer = 3, fraction = 2, message = "Điểm tối đa 2 chữ số thập phân")
    private BigDecimal score;

    /** HTML nhận xét; backend sanitize. */
    private String feedback;

    /** {@code SubmissionDetailDto.version} lúc mở bài; khác hiện tại -> 409 GRADE_CONFLICT. */
    @NotNull(message = "Thiếu phiên bản bài nộp")
    private Long expectedVersion;
}
