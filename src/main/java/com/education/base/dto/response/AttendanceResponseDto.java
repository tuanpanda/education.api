package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một bản ghi điểm danh ({@code EDU_ATTENDANCE}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceResponseDto {

    private Long id;

    private Long classId;
    private String classCode;
    private String className;

    private Long studentId;
    private String studentCode;
    private String fullName;

    private LocalDate attendanceDate;

    /** {@code PRESENT}, {@code ABSENT}, {@code LATE} hoặc {@code EXCUSED}. */
    private String status;

    private String note;

    /** ID người thực hiện điểm danh trong {@code SYS_USERS}. */
    private Long recordedById;

    private String recordedByName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
