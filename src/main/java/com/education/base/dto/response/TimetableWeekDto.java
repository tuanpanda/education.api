package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Một tuần trong view thời khóa biểu. {@code weekStart} luôn là Thứ Hai ISO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimetableWeekDto {

    private LocalDate weekStart;

    private LocalDate weekEnd;

    @Builder.Default
    private List<TimetableDayDto> days = new ArrayList<>();
}
