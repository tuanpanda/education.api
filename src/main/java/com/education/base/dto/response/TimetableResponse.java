package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Thời khóa biểu theo khoảng ngày: danh sách phẳng + nhóm theo tuần (Thứ Hai–Chủ Nhật).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimetableResponse {

    private LocalDate fromDate;

    private LocalDate toDate;

    @Builder.Default
    private List<TimetableItemDto> items = new ArrayList<>();

    @Builder.Default
    private List<TimetableWeekDto> weeks = new ArrayList<>();
}
