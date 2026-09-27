package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Một buổi học trên thời khóa biểu (dòng procedure + grouping tuần/tháng).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimetableItemDto {

    private Long id;
    private Long classId;
    private String classCode;
    private String className;
    private Long scheduleId;
    private LocalDate sessionDate;
    private Integer dayOfWeek;
    private String startTime;
    private String endTime;
    private String roomName;
    private Long teacherId;
    private String teacherName;
    private String topic;
    private String status;
    private String note;
    private String calendarColor;
}
