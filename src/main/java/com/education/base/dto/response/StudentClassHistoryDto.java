package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mot dong lich su lop / khoa hoc cua hoc sinh (moi ghi danh, ke ca da hoan thanh, da nghi, da rut khoi lop).
 * {@code enrollmentStatus}: ENROLLED / COMPLETED / DROPPED, hoac REMOVED khi ghi danh da bi xoa mem.
 * {@code statusChangedAt}: lan cap nhat cuoi cua ghi danh (xap xi ngay hoan thanh / nghi) - null neu chua doi.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentClassHistoryDto {
    private Long enrollmentId;
    private Long classId;
    private String classCode;
    private String className;
    private String subjectName;
    private String classStatus;
    private LocalDate classStartDate;
    private LocalDate classEndDate;
    private Long courseId;
    private String courseCode;
    private String courseName;
    private String enrollmentStatus;
    private LocalDateTime enrolledAt;
    private LocalDateTime statusChangedAt;
    /** Dang hoc: ghi danh ENROLLED con hieu luc va lop khong CLOSED/CANCELLED. */
    private boolean current;
}