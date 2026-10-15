package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GĐ4 - dòng danh sách câu hỏi. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionListItemDto {

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
}
