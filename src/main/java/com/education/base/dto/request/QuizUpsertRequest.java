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

/** GĐ4 - {@code POST/PUT /api/v1/lms/quizzes}. Giờ ISO không múi giờ = giờ server. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizUpsertRequest {

    @NotNull(message = "Vui lòng chọn lớp")
    private Long classId;
    private Long lessonId;
    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(max = 200, message = "Tiêu đề không được vượt quá 200 ký tự")
    private String title;
    @Size(max = 20000, message = "Mô tả tối đa 20000 ký tự")
    private String description;
    @NotNull(message = "Vui lòng nhập giờ mở")
    private LocalDateTime openAt;
    @NotNull(message = "Vui lòng nhập giờ đóng")
    private LocalDateTime closeAt;
    private Integer durationMin;
    @NotNull(message = "Vui lòng nhập số lượt làm")
    private Integer maxAttempts;
    private Boolean shuffleQuestions;
    private Boolean shuffleOptions;
    @NotBlank(message = "Vui lòng chọn chế độ xem kết quả")
    @Pattern(regexp = "IMMEDIATE|AFTER_CLOSE|MANUAL", message = "Chế độ xem kết quả không hợp lệ")
    private String resultMode;
    @Pattern(regexp = "HIGHEST|LAST|AVERAGE", message = "Cách tính điểm không hợp lệ")
    private String scorePolicy;
    @DecimalMin(value = "0", message = "Điểm đạt không âm")
    private BigDecimal passScore;
    private Boolean syncToGradebook;
    @Valid
    private List<QuestionItem> questions;
    @Valid
    private List<RuleItem> rules;
    private Long expectedVersion;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionItem {
        @NotNull(message = "Thiếu câu hỏi")
        private Long questionId;
        @NotNull(message = "Thiếu điểm câu")
        private BigDecimal points;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleItem {
        @NotBlank(message = "Chủ đề không được để trống")
        @Size(max = 200, message = "Chủ đề tối đa 200 ký tự")
        private String topic;
        @NotNull(message = "Vui lòng chọn độ khó")
        @Min(value = 1, message = "Độ khó 1-3")
        @Max(value = 3, message = "Độ khó 1-3")
        private Integer difficulty;
        @NotNull(message = "Thiếu số câu")
        @Min(value = 1, message = "Số câu tối thiểu 1")
        @Max(value = 200, message = "Số câu tối đa 200")
        private Integer questionCount;
        @NotNull(message = "Thiếu điểm mỗi câu")
        private BigDecimal pointsEach;
    }
}
