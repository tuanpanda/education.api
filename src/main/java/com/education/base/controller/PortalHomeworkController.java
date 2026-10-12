package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.PortalSubmissionDraftRequest;
import com.education.base.dto.response.PortalAssignmentDetailDto;
import com.education.base.dto.response.PortalAssignmentListItemDto;
import com.education.base.dto.response.PortalFileDto;
import com.education.base.dto.response.PortalHomeworkSummaryDto;
import com.education.base.dto.response.PortalSubmissionDto;
import com.education.base.security.PortalAccess;
import com.education.base.service.homework.PortalHomeworkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** GĐ2 - bài tập về nhà trên cổng học sinh. Hợp đồng: docs/homework-api.md §3. */
@RestController
@RequestMapping("/api/v1/portal/me/assignments")
@RequiredArgsConstructor
@Validated
@PortalAccess
@Tag(name = "Cổng học sinh - Bài tập", description = "Xem bài, làm bài, nộp / rút bài")
public class PortalHomeworkController {

    private final PortalHomeworkService portalHomeworkService;

    @Operation(summary = "Danh sách bài theo tab (todo|submitted|graded|overdue)")
    @GetMapping
    public ApiResponse<List<PortalAssignmentListItemDto>> list(
            @RequestParam(value = "tab", required = false) String tab) {
        return ApiResponse.success(portalHomeworkService.list(tab));
    }

    @Operation(summary = "Huy hiệu trang chủ")
    @GetMapping("/summary")
    public ApiResponse<PortalHomeworkSummaryDto> summary() {
        return ApiResponse.success(portalHomeworkService.summary());
    }

    @Operation(summary = "Chi tiết bài")
    @GetMapping("/{id}")
    public ApiResponse<PortalAssignmentDetailDto> get(@PathVariable("id") Long id) {
        return ApiResponse.success(portalHomeworkService.get(id));
    }

    @Operation(summary = "Lưu nháp bài làm")
    @PutMapping("/{id}/submission")
    public ApiResponse<PortalSubmissionDto> saveDraft(@PathVariable("id") Long id,
                                                      @Valid @RequestBody PortalSubmissionDraftRequest request) {
        return ApiResponse.success("Đã lưu nháp.", portalHomeworkService.saveDraft(id, request));
    }

    @Operation(summary = "Đính kèm file bài làm")
    @PostMapping(value = "/{id}/submission/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PortalFileDto> uploadFile(@PathVariable("id") Long id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.success("Đã tải file lên.", portalHomeworkService.uploadFile(id, file));
    }

    @Operation(summary = "Gỡ file bài làm")
    @DeleteMapping("/{id}/submission/files/{fileId}")
    public ApiResponse<Void> deleteFile(@PathVariable("id") Long id, @PathVariable("fileId") Long fileId) {
        portalHomeworkService.deleteFile(id, fileId);
        return ApiResponse.success("Đã gỡ file.", null);
    }

    @Operation(summary = "Nộp bài")
    @PostMapping("/{id}/submit")
    public ApiResponse<PortalSubmissionDto> submit(@PathVariable("id") Long id) {
        return ApiResponse.success("Đã nộp bài.", portalHomeworkService.submit(id));
    }

    @Operation(summary = "Rút bài để sửa")
    @PostMapping("/{id}/unsubmit")
    public ApiResponse<PortalSubmissionDto> unsubmit(@PathVariable("id") Long id) {
        return ApiResponse.success("Đã rút bài.", portalHomeworkService.unsubmit(id));
    }
}
