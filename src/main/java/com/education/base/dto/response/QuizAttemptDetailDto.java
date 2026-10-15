package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GĐ4 - chi tiết 1 lượt làm (nhân viên, có đáp án + sự kiện). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttemptDetailDto {

    private Long attemptId;
    private Long quizId;
    private Long studentId;
    private String fullName;
    private Integer attemptNo;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime submittedAt;
    private BigDecimal score;
    private BigDecimal score10;
    private String ipAddress;
    private String userAgent;
    private Integer focusLostCount;
    private List<String> flags;
    private List<Answer> answers;
    private List<Event> events;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Answer {
        private Long answerId;
        private Long questionId;
        private String type;
        private String content;
        private List<Option> options;
        private List<String> acceptedAnswers;
        private List<Long> selectedOptionIds;
        private String textAnswer;
        private Boolean isCorrect;
        private BigDecimal points;
        private BigDecimal pointsAwarded;
        private String graderComment;
        private String overrideReason;
        private String gradedBy;
        private Long version;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        private Long id;
        private String content;
        private Boolean isCorrect;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Event {
        private String type;
        private LocalDateTime occurredAt;
        private String ipAddress;
    }
}
