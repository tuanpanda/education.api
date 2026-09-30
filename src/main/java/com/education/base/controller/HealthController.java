package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.security.PublicEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check công khai (không cần đăng nhập) cho Docker/IIS/load balancer.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Kiểm tra dịch vụ đang chạy")
public class HealthController {

    @Operation(summary = "Health check")
    @GetMapping
    @PublicEndpoint
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of("status", "UP"));
    }
}
