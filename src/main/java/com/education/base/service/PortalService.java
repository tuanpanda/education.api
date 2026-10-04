package com.education.base.service;

import com.education.base.dto.response.PortalMeResponse;

/**
 * Nghiệp vụ cổng học sinh ({@code /api/v1/portal/**}). Học sinh luôn lấy từ phiên đăng nhập
 * ({@code PortalStudentContext}), không nhận {@code studentId} từ client.
 */
public interface PortalService {

    /** Hồ sơ + lớp đang học của học sinh đang đăng nhập. */
    PortalMeResponse me();
}
