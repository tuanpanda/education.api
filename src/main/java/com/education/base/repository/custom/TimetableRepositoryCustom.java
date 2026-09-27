package com.education.base.repository.custom;

import com.education.base.dto.request.TimetableFilterRequest;
import com.education.base.dto.response.TimetableItemDto;

import java.util.List;

/**
 * Tra cứu thời khóa biểu qua Standalone Procedure {@code PRC_GET_TIMETABLE_BY_RANGE}.
 */
public interface TimetableRepositoryCustom {

    List<TimetableItemDto> findTimetableByRange(TimetableFilterRequest filter);
}
