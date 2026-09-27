package com.education.base.service;

import com.education.base.dto.request.AttendanceFilterRequest;
import com.education.base.dto.request.AttendanceMarkRequest;
import com.education.base.dto.response.AttendanceResponseDto;

import java.util.List;

public interface AttendanceService {

    /**
     * Điểm danh hàng loạt trong một transaction: upsert theo (lớp, học sinh, ngày).
     */
    List<AttendanceResponseDto> markBatch(AttendanceMarkRequest request);

    List<AttendanceResponseDto> search(AttendanceFilterRequest filter);
}
