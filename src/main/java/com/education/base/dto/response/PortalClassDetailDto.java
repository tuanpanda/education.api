package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Lớp đang học trên cổng ({@code GET /api/v1/portal/me/classes}).
 * DTO riêng cho cổng: có môn / GV / phòng / ngày, không có học phí nội bộ.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalClassDetailDto {

    private Long classId;

    private String classCode;

    private String className;

    /** {@code EDU_CLASSES.STATUS}: PLANNED / OPEN / ONGOING / CLOSED / CANCELLED. */
    private String status;

    private String subjectName;

    private Long teacherId;

    private String teacherName;

    private String roomName;

    private LocalDate startDate;

    private LocalDate endDate;
}
