package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.AttendanceFilterRequest;
import com.education.base.dto.request.AttendanceMarkRequest;
import com.education.base.dto.response.AttendanceResponseDto;
import com.education.base.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@Validated
@Tag(name = "Điểm danh", description = "Điểm danh hàng loạt và tra cứu lịch sử")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @Operation(summary = "Điểm danh hàng loạt",
            description = "Ghi nhận điểm danh cả lớp trong một transaction, upsert theo (lớp, học sinh, ngày).")
    @PostMapping("/batch")
    public ApiResponse<List<AttendanceResponseDto>> markBatch(@Valid @RequestBody AttendanceMarkRequest request) {
        return ApiResponse.success("Lưu điểm danh thành công.", attendanceService.markBatch(request));
    }

    @Operation(summary = "Tra cứu điểm danh")
    @GetMapping
    public ApiResponse<List<AttendanceResponseDto>> search(@Valid @ModelAttribute AttendanceFilterRequest filter) {
        return ApiResponse.success(attendanceService.search(filter));
    }
}
