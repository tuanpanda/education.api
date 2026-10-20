package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GĐ4 - cổng: kết quả lưu 1 câu. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalSaveAnswerDto {

    private Long questionId;
    private LocalDateTime savedAt;
    /** Giờ server ISO-8601 kèm offset (+07:00). */
    private LocalDateTime serverNow;
    private Long remainingSeconds;
}
