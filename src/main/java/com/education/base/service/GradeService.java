package com.education.base.service;

import com.education.base.dto.request.GradeBatchRequest;
import com.education.base.dto.response.GradeResponseDto;
import com.education.base.dto.response.StudentGradeSummaryDto;

import java.util.List;

public interface GradeService {

    /**
     * Nhập điểm hàng loạt trong một transaction: upsert theo (lớp, học sinh, loại điểm).
     */
    List<GradeResponseDto> upsertBatch(GradeBatchRequest request);

    List<GradeResponseDto> listByClass(Long classId, Long studentId);

    StudentGradeSummaryDto summarize(Long classId, Long studentId);
}
