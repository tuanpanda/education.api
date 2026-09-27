package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.DashboardFilterRequest;
import com.education.base.dto.response.DashboardMetricsResponse;
import com.education.base.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Validated
@Tag(name = "Báo cáo", description = "Chỉ số bảng điều khiển từ procedure PRC_RPT_DASHBOARD_METRICS")
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "Chỉ số dashboard",
            description = "Bỏ trống khoảng ngày thì procedure lấy 12 tháng gần nhất.")
    @GetMapping("/dashboard")
    public ApiResponse<DashboardMetricsResponse> dashboard(@Valid @ModelAttribute DashboardFilterRequest filter) {
        return ApiResponse.success(reportService.getDashboardMetrics(filter));
    }
}
