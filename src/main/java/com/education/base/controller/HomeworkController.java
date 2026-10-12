package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.AssignmentFilterRequest;
import com.education.base.dto.request.AssignmentUpsertRequest;
import com.education.base.dto.request.GradeSubmissionRequest;
import com.education.base.dto.request.ReturnSubmissionRequest;
import com.education.base.dto.response.AnnouncementClassOptionDto;
import com.education.base.dto.response.AssignmentDto;
import com.education.base.dto.response.ExportFileDto;
import com.education.base.dto.response.FileResponseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.SubmissionDetailDto;
import com.education.base.dto.response.SubmissionMatrixDto;
import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.service.homework.HomeworkExportService;
import com.education.base.service.homework.HomeworkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** GĐ2 - bài tập về nhà phía nhân viên. Hợp đồng: docs/homework-api.md §2. */
@RestController
@RequestMapping("/api/v1/lms/assignments")
@RequiredArgsConstructor
@Validated
@Tag(name = "Bài tập về nhà", description = "Giao bài, xem bài nộp, chấm và trả bài")
public class HomeworkController {

    private final HomeworkService homeworkService;
    private final HomeworkExportService homeworkExportService;

    @Operation(summary = "Danh sách bài tập (phân trang 1-based)")
    @GetMapping
    @RequirePermission(Permissions.HOMEWORK_VIEW)
    public ApiResponse<PageResponse<AssignmentDto>> search(@Valid @ModelAttribute AssignmentFilterRequest filter) {
        return ApiResponse.success(homeworkService.search(filter));
    }

    @Operation(summary = "Lớp được phép giao bài")
    @GetMapping("/manageable-classes")
    @RequirePermission(Permissions.HOMEWORK_CREATE)
    public ApiResponse<List<AnnouncementClassOptionDto>> manageableClasses() {
        return ApiResponse.success(homeworkService.listManageableClasses());
    }

    @Operation(summary = "Chi tiết bài tập")
    @GetMapping("/{id}")
    @RequirePermission(Permissions.HOMEWORK_VIEW)
    public ApiResponse<AssignmentDto> getById(@PathVariable("id") Long id) {
        return ApiResponse.success(homeworkService.getById(id));
    }

    @Operation(summary = "Tạo bài tập (nháp)")
    @PostMapping
    @RequirePermission(Permissions.HOMEWORK_CREATE)
    public ApiResponse<AssignmentDto> create(@Valid @RequestBody AssignmentUpsertRequest request) {
        return ApiResponse.success("Đã tạo bài tập.", homeworkService.create(request));
    }

    @Operation(summary = "Cập nhật bài tập")
    @PutMapping("/{id}")
    @RequirePermission(Permissions.HOMEWORK_UPDATE)
    public ApiResponse<AssignmentDto> update(@PathVariable("id") Long id,
                                             @Valid @RequestBody AssignmentUpsertRequest request) {
        return ApiResponse.success("Đã cập nhật bài tập.", homeworkService.update(id, request));
    }

    @Operation(summary = "Đăng bài / mở lại bài đã đóng")
    @PostMapping("/{id}/publish")
    @RequirePermission(Permissions.HOMEWORK_UPDATE)
    public ApiResponse<AssignmentDto> publish(@PathVariable("id") Long id) {
        return ApiResponse.success("Đã đăng bài tập.", homeworkService.publish(id));
    }

    @Operation(summary = "Đóng bài")
    @PostMapping("/{id}/close")
    @RequirePermission(Permissions.HOMEWORK_UPDATE)
    public ApiResponse<AssignmentDto> close(@PathVariable("id") Long id) {
        return ApiResponse.success("Đã đóng bài tập.", homeworkService.close(id));
    }

    @Operation(summary = "Xoá mềm bài tập")
    @DeleteMapping("/{id}")
    @RequirePermission(Permissions.HOMEWORK_DELETE)
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        homeworkService.softDelete(id);
        return ApiResponse.success("Đã xoá bài tập.", null);
    }

    @Operation(summary = "Đính kèm file đề bài")
    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(Permissions.HOMEWORK_UPDATE)
    public ApiResponse<FileResponseDto> uploadAttachment(@PathVariable("id") Long id,
                                                         @RequestParam("file") MultipartFile file) {
        return ApiResponse.success("Đã tải file lên.", homeworkService.uploadAttachment(id, file));
    }

    @Operation(summary = "Gỡ file đề bài")
    @DeleteMapping("/{id}/attachments/{fileId}")
    @RequirePermission(Permissions.HOMEWORK_UPDATE)
    public ApiResponse<Void> deleteAttachment(@PathVariable("id") Long id, @PathVariable("fileId") Long fileId) {
        homeworkService.deleteAttachment(id, fileId);
        return ApiResponse.success("Đã gỡ file.", null);
    }

    @Operation(summary = "Ma trận bài nộp của cả lớp")
    @GetMapping("/{id}/submissions")
    @RequirePermission(Permissions.HOMEWORK_VIEW)
    public ApiResponse<SubmissionMatrixDto> submissions(@PathVariable("id") Long id) {
        return ApiResponse.success(homeworkService.getSubmissionMatrix(id));
    }

    @Operation(summary = "Chi tiết một bài nộp")
    @GetMapping("/submissions/{sid}")
    @RequirePermission(Permissions.HOMEWORK_VIEW)
    public ApiResponse<SubmissionDetailDto> submission(@PathVariable("sid") Long sid) {
        return ApiResponse.success(homeworkService.getSubmission(sid));
    }

    @Operation(summary = "Chấm bài (expectedVersion lệch -> 409 GRADE_CONFLICT)")
    @PutMapping("/submissions/{sid}/grade")
    @RequirePermission(Permissions.HOMEWORK_GRADE)
    public ApiResponse<SubmissionDetailDto> grade(@PathVariable("sid") Long sid,
                                                  @Valid @RequestBody GradeSubmissionRequest request) {
        return ApiResponse.success("Đã chấm bài.", homeworkService.grade(sid, request));
    }

    @Operation(summary = "Trả bài cho học sinh làm lại")
    @PostMapping("/submissions/{sid}/return")
    @RequirePermission(Permissions.HOMEWORK_GRADE)
    public ApiResponse<SubmissionDetailDto> returnSubmission(@PathVariable("sid") Long sid,
                                                             @Valid @RequestBody ReturnSubmissionRequest request) {
        return ApiResponse.success("Đã trả bài.", homeworkService.returnSubmission(sid, request));
    }

    @Operation(summary = "Đính kèm file nhận xét")
    @PostMapping(value = "/submissions/{sid}/feedback-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(Permissions.HOMEWORK_GRADE)
    public ApiResponse<FileResponseDto> uploadFeedback(@PathVariable("sid") Long sid,
                                                       @RequestParam("file") MultipartFile file) {
        return ApiResponse.success("Đã tải file lên.", homeworkService.uploadFeedbackFile(sid, file));
    }

    @Operation(summary = "Gỡ file nhận xét")
    @DeleteMapping("/submissions/{sid}/feedback-files/{fileId}")
    @RequirePermission(Permissions.HOMEWORK_GRADE)
    public ApiResponse<Void> deleteFeedback(@PathVariable("sid") Long sid, @PathVariable("fileId") Long fileId) {
        homeworkService.deleteFeedbackFile(sid, fileId);
        return ApiResponse.success("Đã gỡ file.", null);
    }

    @Operation(summary = "Xuất Excel ma trận bài nộp")
    @GetMapping("/{id}/export")
    @RequirePermission(Permissions.HOMEWORK_EXPORT)
    public ResponseEntity<Resource> export(@PathVariable("id") Long id) {
        ExportFileDto file = homeworkExportService.exportAssignment(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore())
                .contentLength(file.content().length)
                .body(new ByteArrayResource(file.content()));
    }
}
