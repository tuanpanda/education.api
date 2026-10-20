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

/** GĐ4 - {@code POST/PUT /api/v1/lms/questions}. HTML được sanitize phía server. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionUpsertRequest {

    @NotBlank(message = "Vui lòng chọn loại câu hỏi")
    @Pattern(regexp = "SINGLE|MULTI|TRUE_FALSE|SHORT_TEXT|ESSAY", message = "Loại câu hỏi không hợp lệ")
    private String type;
    @NotBlank(message = "Môn học không được để trống")
    @Size(max = 100, message = "Môn học tối đa 100 ký tự")
    private String subjectName;
    @NotBlank(message = "Khối lớp không được để trống")
    @Size(max = 20, message = "Khối lớp tối đa 20 ký tự")
    private String gradeLevel;
    @NotBlank(message = "Chủ đề không được để trống")
    @Size(max = 200, message = "Chủ đề tối đa 200 ký tự")
    private String topic;
    @NotNull(message = "Vui lòng chọn độ khó")
    @Min(value = 1, message = "Độ khó 1-3")
    @Max(value = 3, message = "Độ khó 1-3")
    private Integer difficulty;
    @Pattern(regexp = "PRIVATE|SHARED", message = "Phạm vi chia sẻ không hợp lệ")
    private String visibility;
    private Long courseId;
    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    @Size(max = 20000, message = "Nội dung tối đa 20000 ký tự")
    private String content;
    @Size(max = 20000, message = "Giải thích tối đa 20000 ký tự")
    private String explanation;
    @Valid
    private List<Option> options;
    private List<String> acceptedAnswers;
    /** Bắt buộc khi PUT. */
    private Long expectedVersion;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        @NotBlank(message = "Nội dung lựa chọn không được để trống")
        @Size(max = 5000, message = "Lựa chọn tối đa 5000 ký tự")
        private String content;
        private Boolean isCorrect;
        private Integer sortOrder;
    }
}
