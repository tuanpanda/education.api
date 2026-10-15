package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GĐ4 - chi tiết câu hỏi (có đáp án; chỉ nhân viên). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDto {

    private Long id;
    private String type;
    private String subjectName;
    private String gradeLevel;
    private String topic;
    private Integer difficulty;
    private String visibility;
    private Long ownerId;
    private String ownerName;
    private String contentPreview;
    private Long usedInQuizCount;
    private LocalDateTime updatedAt;
    private Long courseId;
    private String content;
    private String explanation;
    private List<Option> options;
    private List<String> acceptedAnswers;
    private Boolean canEdit;
    private Long version;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        private Long id;
        private String content;
        private Boolean isCorrect;
        private Integer sortOrder;
    }
}
