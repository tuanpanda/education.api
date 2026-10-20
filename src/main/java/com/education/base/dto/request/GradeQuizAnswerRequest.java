package com.education.base.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GĐ4 - chấm / ghi đè điểm 1 câu. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeQuizAnswerRequest {

    @NotNull(message = "Vui lòng nhập điểm")
    private BigDecimal pointsAwarded;
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
    @Size(max = 2000, message = "Nhận xét tối đa 2000 ký tự")
    private String comment;
    @NotNull(message = "Thiếu expectedVersion")
    private Long expectedVersion;
}
