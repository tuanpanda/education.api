package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Buổi học sắp tới trên dashboard cổng ({@code GET /api/v1/portal/me/dashboard}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalNextSessionDto {

    private Long sessionId;

    private Long classId;

    private String classCode;

    private String className;

    private LocalDate sessionDate;

    private String startTime;

    private String endTime;

    private String roomName;

    private String teacherName;

    private String topic;

    private String status;
}
