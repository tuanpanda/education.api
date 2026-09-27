package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một khung lịch tuần đã lưu của lớp.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassScheduleResponse {

    private Long id;
    private Long classId;
    private Integer dayOfWeek;
    private String startTime;
    private String endTime;
    private String roomName;
    private Long teacherId;
    private String teacherName;
    private String status;
}
