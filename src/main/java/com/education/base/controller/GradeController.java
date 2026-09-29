package com.education.base.controller;

import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.common.ApiResponse;
import com.education.base.dto.request.GradeBatchRequest;
import com.education.base.dto.response.GradeResponseDto;
import com.education.base.dto.response.StudentGradeSummaryDto;
import com.education.base.service.GradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/grades")
@RequiredArgsConstructor
@Validated
@Tag(name = "Nhập điểm", description = "Nhập điểm hàng loạt và bảng điểm tổng hợp")
public class GradeController {

    private final GradeService gradeService;

    @Operation(summary = "Nhập điểm hàng loạt",
            description = "Upsert theo (lớp, học sinh, loại điểm) trong một transaction.")
    @PostMapping("/batch")
    @RequirePermission({Permissions.GRADE_CREATE, Permissions.GRADE_UPDATE})
    public ApiResponse<List<GradeResponseDto>> upsertBatch(@Valid @RequestBody GradeBatchRequest request) {
        return ApiResponse.success("Nhập điểm thành công.", gradeService.upsertBatch(request));
    }

    @Operation(summary = "Danh sách đầu điểm")
    @GetMapping
    @RequirePermission(Permissions.GRADE_VIEW)
    public ApiResponse<List<GradeResponseDto>> list(
            @RequestParam(value = "classId", required = false) Long classId,
            @RequestParam(value = "studentId", required = false) Long studentId) {
        return ApiResponse.success(gradeService.listByClass(classId, studentId));
    }

    @Operation(summary = "Bảng điểm tổng hợp của học sinh trong lớp")
    @GetMapping("/summary")
    @RequirePermission({Permissions.GRADE_VIEW, Permissions.STUDENT_VIEW})
    public ApiResponse<StudentGradeSummaryDto> summarize(
            @RequestParam("classId") @NotNull(message = "ID lớp học không được để trống") Long classId,
            @RequestParam("studentId") @NotNull(message = "ID học sinh không được để trống") Long studentId) {
        return ApiResponse.success(gradeService.summarize(classId, studentId));
    }
}
