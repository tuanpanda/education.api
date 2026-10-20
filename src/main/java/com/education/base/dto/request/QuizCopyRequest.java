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

/** GĐ4 - {@code POST /api/v1/lms/quizzes/{id}/copy}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizCopyRequest {

    @NotNull(message = "Vui lòng chọn lớp đích")
    private Long targetClassId;
}
