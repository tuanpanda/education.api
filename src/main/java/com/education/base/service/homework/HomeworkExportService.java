package com.education.base.service.homework;

import com.education.base.dto.response.ExportFileDto;

/**
 * {@code GET /api/v1/lms/assignments/{id}/export} (MENU_HOMEWORK:EXPORT): Excel (POI sẵn có) một sheet: STT, mã HS,
 * họ tên, trạng thái, nộp lúc, muộn, số lần nộp, điểm, điểm thang 10, nhận xét (text thuần). Áp dụng phạm vi giáo viên.
 */
public interface HomeworkExportService {

    ExportFileDto exportAssignment(Long assignmentId);
}
