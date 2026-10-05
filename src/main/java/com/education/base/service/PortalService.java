package com.education.base.service;

import com.education.base.dto.response.PortalAttendanceDto;
import com.education.base.dto.response.PortalAttendanceSummaryDto;
import com.education.base.dto.response.PortalClassDetailDto;
import com.education.base.dto.response.PortalDashboardResponse;
import com.education.base.dto.response.PortalFeeDetailDto;
import com.education.base.dto.response.PortalFeeListItemDto;
import com.education.base.dto.response.PortalGradeDto;
import com.education.base.dto.response.PortalMeResponse;
import com.education.base.dto.response.PortalPaymentDto;
import com.education.base.dto.response.PortalTimetableResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Nghiệp vụ cổng học sinh ({@code /api/v1/portal/**}). Học sinh luôn lấy từ phiên đăng nhập
 * ({@code PortalStudentContext}), không nhận {@code studentId} từ client.
 */
public interface PortalService {

    /** Hồ sơ + lớp đang học của học sinh đang đăng nhập. */
    PortalMeResponse me();

    /** Dashboard: buổi sắp tới, học phí còn nợ, số thông báo chưa đọc. */
    PortalDashboardResponse dashboard();

    /** Các lớp đang ghi danh ({@code ENROLLED}) kèm môn / GV / phòng / ngày. */
    List<PortalClassDetailDto> myClasses();

    /**
     * Thời khóa biểu theo khoảng ngày qua {@code PRC_GET_TIMETABLE_BY_RANGE}
     * với {@code P_STUDENT_ID} từ phiên (không lấy từ client).
     */
    PortalTimetableResponse timetable(LocalDate from, LocalDate to);

    /**
     * Điểm danh của học sinh trong lớp đang ghi danh.
     * Bắt buộc {@code classId}; {@code from}/{@code to} tùy chọn.
     */
    List<PortalAttendanceDto> attendance(Long classId, LocalDate from, LocalDate to);

    /**
     * Tổng hợp điểm danh. Nếu {@code classId} khác null thì bắt buộc đang ghi danh lớp đó;
     * nếu null thì tổng hợp mọi lớp đang ghi danh.
     */
    PortalAttendanceSummaryDto attendanceSummary(Long classId);

    /** Điểm của học sinh đang đăng nhập (có thể rỗng). */
    List<PortalGradeDto> grades();

    /** Danh sách học phí của học sinh đang đăng nhập. */
    List<PortalFeeListItemDto> fees();

    /** Chi tiết một khoản học phí thuộc về học sinh đang đăng nhập. */
    PortalFeeDetailDto feeDetail(Long feeId);

    /** HTML phiếu học phí (in / gửi Zalo) — chỉ khoản của chính học sinh. */
    String feeSlipHtml(Long feeId);

    /** Lịch sử thanh toán các khoản học phí của chính học sinh. */
    List<PortalPaymentDto> payments();
}
