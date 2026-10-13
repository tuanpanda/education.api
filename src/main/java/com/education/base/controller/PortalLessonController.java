package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.PortalLessonProgressRequest;
import com.education.base.dto.response.PortalLessonClassDto;
import com.education.base.dto.response.PortalLessonDetailDto;
import com.education.base.dto.response.PortalLessonProgressDto;
import com.education.base.dto.response.PortalLessonTreeDto;
import com.education.base.security.PortalAccess;
import com.education.base.service.lesson.PortalLessonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Học liệu trên cổng học sinh (§3 docs/portal-p3-api.md). Ngoài phạm vi -> 404, không 403. */
@RestController
@RequestMapping("/api/v1/portal/me")
@RequiredArgsConstructor
@Validated
@PortalAccess
@Tag(name = "Cổng học sinh - Học liệu", description = "Bài học, tài liệu, video, tiến độ")
public class PortalLessonController {

    private final PortalLessonService portalLessonService;

    @Operation(summary = "Các lớp đang học kèm tiến độ học liệu")
    @GetMapping("/lessons")
    public ApiResponse<List<PortalLessonClassDto>> classes() {
        return ApiResponse.success(portalLessonService.listClasses());
    }

    @Operation(summary = "Cây bài học của lớp")
    @GetMapping("/classes/{classId}/lessons")
    public ApiResponse<PortalLessonTreeDto> tree(@PathVariable("classId") Long classId) {
        return ApiResponse.success(portalLessonService.getTree(classId));
    }

    @Operation(summary = "Chi tiết bài học (không ghi tiến độ)")
    @GetMapping("/lessons/{id}")
    public ApiResponse<PortalLessonDetailDto> get(@PathVariable("id") Long id,
                                                  @RequestParam(value = "classId", required = false) Long classId) {
        return ApiResponse.success(portalLessonService.get(id, classId));
    }

    @Operation(summary = "Ghi tiến độ (IN_PROGRESS / COMPLETED)")
    @PostMapping("/lessons/{id}/progress")
    public ApiResponse<PortalLessonProgressDto> progress(@PathVariable("id") Long id,
                                                         @RequestParam(value = "classId", required = false) Long classId,
                                                         @Valid @RequestBody PortalLessonProgressRequest request) {
        return ApiResponse.success(portalLessonService.recordProgress(id, classId, request));
    }
}
