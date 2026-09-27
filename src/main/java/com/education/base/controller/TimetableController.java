package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.CancelSessionRequest;
import com.education.base.dto.request.GenerateSessionsRequest;
import com.education.base.dto.request.SaveClassScheduleRequest;
import com.education.base.dto.request.TimetableFilterRequest;
import com.education.base.dto.request.UpdateSessionRequest;
import com.education.base.dto.response.ClassScheduleResponse;
import com.education.base.dto.response.GenerateSessionsResponse;
import com.education.base.dto.response.TimetableItemDto;
import com.education.base.dto.response.TimetableResponse;
import com.education.base.service.TimetableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
@Tag(name = "Lịch học & Thời khóa biểu",
        description = "Khung lịch tuần, sinh buổi học, tra cứu TKB và hủy/sửa buổi")
public class TimetableController {

    private final TimetableService timetableService;

    @Operation(summary = "Xem khung lịch tuần của lớp",
            description = "Trả về các slot ACTIVE (thứ 2–8, giờ HH:mm).")
    @GetMapping("/classes/{classId}/schedules")
    public ApiResponse<List<ClassScheduleResponse>> getSchedules(@PathVariable("classId") Long classId) {
        return ApiResponse.success(timetableService.getClassSchedules(classId));
    }

    @Operation(summary = "Cấu hình lịch tuần của lớp",
            description = "Xóa mềm lịch ACTIVE cũ rồi ghi slot mới trong một transaction. "
                    + "Chặn chồng giờ trong cùng lớp, trùng phòng hoặc trùng giáo viên với lớp khác.")
    @PutMapping("/classes/{classId}/schedules")
    public ApiResponse<List<ClassScheduleResponse>> saveSchedules(
            @PathVariable("classId") Long classId,
            @Valid @RequestBody SaveClassScheduleRequest request) {
        return ApiResponse.success("Cấu hình lịch tuần thành công.",
                timetableService.saveClassSchedules(classId, request));
    }

    @Operation(summary = "Sinh buổi học từ lịch tuần",
            description = "Duyệt từng ngày trong khoảng, khớp DAY_OF_WEEK (2–8 ↔ Java DayOfWeek). "
                    + "Bỏ qua buổi đã tồn tại (class + schedule + ngày). Rollback nếu trùng phòng/GV.")
    @PostMapping("/classes/{classId}/sessions/generate")
    public ApiResponse<GenerateSessionsResponse> generateSessions(
            @PathVariable("classId") Long classId,
            @Valid @RequestBody GenerateSessionsRequest request) {
        return ApiResponse.success("Sinh buổi học thành công.",
                timetableService.generateSessions(classId, request));
    }

    @Operation(summary = "Tra cứu thời khóa biểu",
            description = "Gọi PRC_GET_TIMETABLE_BY_RANGE. Trả danh sách phẳng và nhóm theo tuần.")
    @GetMapping("/timetable")
    public ApiResponse<TimetableResponse> getTimetable(@Valid @ModelAttribute TimetableFilterRequest filter) {
        return ApiResponse.success(timetableService.getTimetable(filter));
    }

    @Operation(summary = "Cập nhật buổi học SCHEDULED",
            description = "Đổi ngày, giờ, phòng, giáo viên hoặc chủ đề. Kiểm tra xung đột trước khi lưu.")
    @PutMapping("/sessions/{id}")
    public ApiResponse<TimetableItemDto> updateSession(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateSessionRequest request) {
        return ApiResponse.success("Cập nhật buổi học thành công.",
                timetableService.updateSession(id, request));
    }

    @Operation(summary = "Hủy buổi học",
            description = "Chỉ hủy trạng thái SCHEDULED. Lý do bắt buộc, lưu vào NOTE.")
    @PostMapping("/sessions/{id}/cancel")
    public ApiResponse<TimetableItemDto> cancelSession(
            @PathVariable("id") Long id,
            @Valid @RequestBody CancelSessionRequest request) {
        return ApiResponse.success("Hủy buổi học thành công.",
                timetableService.cancelSession(id, request));
    }
}
