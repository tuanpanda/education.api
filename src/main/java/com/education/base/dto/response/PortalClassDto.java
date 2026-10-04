package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lớp đang học của học sinh trên cổng ({@code GET /api/v1/portal/me}). DTO riêng cho cổng, không chứa
 * thông tin nội bộ (học phí, sĩ số, giáo viên...).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalClassDto {

    private Long classId;

    private String classCode;

    private String className;

    /** Trạng thái LỚP ({@code EDU_CLASSES.STATUS}): PLANNED / OPEN / ONGOING / CLOSED / CANCELLED. */
    private String status;
}
