package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Một học sinh trong danh sách ghi danh của lớp ({@code EDU_CLASS_STUDENTS}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrolledStudentDto {

    /** ID bản ghi ghi danh, không phải ID học sinh. */
    private Long enrollmentId;

    private Long studentId;
    private String studentCode;
    private String fullName;
    private String email;

    private LocalDateTime enrolledAt;

    /** {@code ENROLLED}, {@code COMPLETED} hoặc {@code DROPPED}. */
    private String status;
}
