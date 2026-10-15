package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GĐ4 - số câu theo chủ đề × độ khó. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionTopicCountDto {

    private String topic;
    private Integer difficulty;
    private Long count;
}
