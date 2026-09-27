package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Một ngày trong view tuần/tháng, chứa các buổi học sắp xếp theo giờ bắt đầu.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimetableDayDto {

    private LocalDate date;

    /** 2=Thứ Hai … 8=Chủ Nhật. */
    private Integer dayOfWeek;

    @Builder.Default
    private List<TimetableItemDto> sessions = new ArrayList<>();
}
