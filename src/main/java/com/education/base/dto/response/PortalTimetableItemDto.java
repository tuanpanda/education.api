package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Một buổi trên TKB cổng học sinh. Không có NOTE nội bộ / thống kê điểm danh lớp.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalTimetableItemDto {

    private Long sessionId;

    private Long classId;

    private String classCode;

    private String className;

    private LocalDate sessionDate;

    private Integer dayOfWeek;

    private String startTime;

    private String endTime;

    private String roomName;

    private Long teacherId;

    private String teacherName;

    private String topic;

    /** {@code SCHEDULED} / {@code COMPLETED} / {@code CANCELLED}. */
    private String status;

    private String calendarColor;
}
