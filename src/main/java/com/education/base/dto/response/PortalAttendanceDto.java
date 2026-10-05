package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Điểm danh trên cổng. Cố ý không có {@code NOTE} nội bộ từ {@code EDU_ATTENDANCE}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalAttendanceDto {

    private Long id;

    private Long classId;

    private String classCode;

    private String className;

    private LocalDate attendanceDate;

    /** {@code PRESENT}, {@code ABSENT}, {@code LATE} hoặc {@code EXCUSED}. */
    private String status;
}
