package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Thời khóa biểu cổng ({@code GET /api/v1/portal/me/timetable}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalTimetableResponse {

    private LocalDate fromDate;

    private LocalDate toDate;

    @Builder.Default
    private List<PortalTimetableItemDto> items = new ArrayList<>();
}
