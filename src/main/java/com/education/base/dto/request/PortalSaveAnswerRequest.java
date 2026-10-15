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

/** GĐ4 - cổng: lưu đáp án 1 câu. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalSaveAnswerRequest {

    @Size(max = 10, message = "Tối đa 10 lựa chọn")
    private List<Long> selectedOptionIds;
    @Size(max = 10000, message = "Câu trả lời tối đa 10000 ký tự")
    private String textAnswer;
}
