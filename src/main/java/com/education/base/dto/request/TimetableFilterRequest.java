package com.education.base.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Bộ lọc thời khóa biểu, truyền vào {@code PRC_GET_TIMETABLE_BY_RANGE}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimetableFilterRequest {

    @NotNull(message = "Từ ngày không được để trống")
    private LocalDate fromDate;

    @NotNull(message = "Đến ngày không được để trống")
    private LocalDate toDate;

    private Long classId;

    private Long teacherId;

    private Long studentId;
}
