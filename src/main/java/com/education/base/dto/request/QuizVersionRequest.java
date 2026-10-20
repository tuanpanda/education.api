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

/** GĐ4 - body {@code {expectedVersion}} cho publish/unpublish/close. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizVersionRequest {

    @NotNull(message = "Thiếu expectedVersion")
    private Long expectedVersion;
}
