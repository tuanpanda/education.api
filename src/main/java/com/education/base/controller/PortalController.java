package com.education.base.controller;

import com.education.base.common.ApiResponse;
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
import com.education.base.security.PortalAccess;
import com.education.base.service.PortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Cổng học sinh. Chỉ tài khoản {@code STUDENT} ({@link PortalAccess}); nhân viên nhận 403 {@code STUDENT_ONLY}.
 * Không endpoint nào nhận {@code studentId}: học sinh luôn lấy từ phiên qua {@code EDU_USER_STUDENT_LINKS}.
 */
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
@PortalAccess
@Tag(name = "Cổng học sinh", description = "API đọc-only Phase 1 dành cho học sinh đang đăng nhập")
public class PortalController {

    private final PortalService portalService;

    @Operation(summary = "Hồ sơ học sinh đang đăng nhập",
            description = "Mã, họ tên, ngày sinh, phụ huynh, liên hệ và các lớp đang học (ENROLLED).")
    @GetMapping("/me")
    public ApiResponse<PortalMeResponse> me() {
        return ApiResponse.success(portalService.me());
    }

    @Operation(summary = "Dashboard cổng",
            description = "Buổi học sắp tới, tóm tắt học phí còn nợ, số thông báo chưa đọc.")
    @GetMapping("/me/dashboard")
    public ApiResponse<PortalDashboardResponse> dashboard() {
        return ApiResponse.success(portalService.dashboard());
    }

    @Operation(summary = "Lớp đang học",
            description = "Các lớp ENROLLED kèm môn, giáo viên, phòng, ngày bắt đầu/kết thúc.")
    @GetMapping("/me/classes")
    public ApiResponse<List<PortalClassDetailDto>> myClasses() {
        return ApiResponse.success(portalService.myClasses());
    }

    @Operation(summary = "Thời khóa biểu",
            description = "Gọi PRC_GET_TIMETABLE_BY_RANGE với P_STUDENT_ID từ phiên đăng nhập.")
    @GetMapping("/me/timetable")
    public ApiResponse<PortalTimetableResponse> timetable(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(portalService.timetable(from, to));
    }

    @Operation(summary = "Điểm danh theo lớp",
            description = "Bắt buộc đang ghi danh lớp. Không trả NOTE nội bộ.")
    @GetMapping("/me/attendance")
    public ApiResponse<List<PortalAttendanceDto>> attendance(
            @RequestParam("classId") Long classId,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(portalService.attendance(classId, from, to));
    }

    @Operation(summary = "Tổng hợp điểm danh",
            description = "Có classId thì kiểm tra ENROLLED; không có thì tổng hợp mọi lớp đang học.")
    @GetMapping("/me/attendance/summary")
    public ApiResponse<PortalAttendanceSummaryDto> attendanceSummary(
            @RequestParam(value = "classId", required = false) Long classId) {
        return ApiResponse.success(portalService.attendanceSummary(classId));
    }

    @Operation(summary = "Bảng điểm", description = "Điểm của học sinh đang đăng nhập (có thể rỗng).")
    @GetMapping("/me/grades")
    public ApiResponse<List<PortalGradeDto>> grades() {
        return ApiResponse.success(portalService.grades());
    }

    @Operation(summary = "Danh sách học phí")
    @GetMapping("/me/fees")
    public ApiResponse<List<PortalFeeListItemDto>> fees() {
        return ApiResponse.success(portalService.fees());
    }

    @Operation(summary = "Chi tiết học phí",
            description = "Chỉ khoản thuộc học sinh đang đăng nhập; 404 nếu không phải của mình.")
    @GetMapping("/me/fees/{id}")
    public ApiResponse<PortalFeeDetailDto> feeDetail(@PathVariable("id") Long id) {
        return ApiResponse.success(portalService.feeDetail(id));
    }

    @Operation(summary = "HTML phiếu học phí",
            description = "Trả HTML/CSS card; chỉ khoản của chính học sinh.")
    @GetMapping(value = "/me/fees/{id}/slip/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> feeSlipHtml(@PathVariable("id") Long id) {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(portalService.feeSlipHtml(id));
    }

    @Operation(summary = "Lịch sử thanh toán",
            description = "Giao dịch các khoản học phí của chính học sinh; ẩn chi tiết VOID nội bộ.")
    @GetMapping("/me/payments")
    public ApiResponse<List<PortalPaymentDto>> payments() {
        return ApiResponse.success(portalService.payments());
    }
}
