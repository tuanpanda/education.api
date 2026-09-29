package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Một buổi điểm danh của học sinh, dùng trên màn hình chi tiết.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAttendanceSessionDto {

    private Long id;

    private Long classId;

    private String classCode;

    private String className;

    private LocalDate attendanceDate;

    /** {@code PRESENT}, {@code ABSENT}, {@code LATE} hoặc {@code EXCUSED}. */
    private String status;

    private String note;
}
