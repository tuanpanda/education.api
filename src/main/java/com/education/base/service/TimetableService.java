package com.education.base.service;

import com.education.base.dto.request.CancelSessionRequest;
import com.education.base.dto.request.GenerateSessionsRequest;
import com.education.base.dto.request.SaveClassScheduleRequest;
import com.education.base.dto.request.TimetableFilterRequest;
import com.education.base.dto.request.UpdateSessionRequest;
import com.education.base.dto.response.ClassScheduleResponse;
import com.education.base.dto.response.GenerateSessionsResponse;
import com.education.base.dto.response.TimetableItemDto;
import com.education.base.dto.response.TimetableResponse;

import java.util.List;

/**
 * Quản lý khung lịch tuần, sinh buổi học, xung đột phòng/GV và thời khóa biểu.
 */
public interface TimetableService {

    List<ClassScheduleResponse> getClassSchedules(Long classId);

    List<ClassScheduleResponse> saveClassSchedules(Long classId, SaveClassScheduleRequest request);

    GenerateSessionsResponse generateSessions(Long classId, GenerateSessionsRequest request);

    TimetableResponse getTimetable(TimetableFilterRequest filter);

    TimetableItemDto updateSession(Long sessionId, UpdateSessionRequest request);

    TimetableItemDto cancelSession(Long sessionId, CancelSessionRequest request);
}
