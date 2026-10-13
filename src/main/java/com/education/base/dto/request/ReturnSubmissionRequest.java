package com.education.base.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code POST /api/v1/lms/assignments/submissions/{sid}/return}: trả bài yêu cầu làm lại. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnSubmissionRequest {

    @NotBlank(message = "Vui lòng nhập lý do trả bài")
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;

    @NotNull(message = "Thiếu phiên bản bài nộp")
    private Long expectedVersion;
}
