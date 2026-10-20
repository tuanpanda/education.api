package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.GradeQuizAnswerRequest;
import com.education.base.dto.request.QuizCopyRequest;
import com.education.base.dto.request.QuizUpsertRequest;
import com.education.base.dto.request.QuizVersionRequest;
import com.education.base.dto.response.AnnouncementClassOptionDto;
import com.education.base.dto.response.ExportFileDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.QuizAttemptDetailDto;
import com.education.base.dto.response.QuizAttemptMatrixDto;
import com.education.base.dto.response.QuizDto;
import com.education.base.dto.response.QuizListItemDto;
import com.education.base.dto.response.QuizStatsDto;
import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.service.quiz.impl.QuizServiceImpl;
import com.education.base.service.quiz.impl.QuizStatsServiceImpl;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** GĐ4 - đề kiểm tra phía nhân viên. Hợp đồng: docs/contracts/quiz-api.md §2.2. */
@RestController
@RequestMapping("/api/v1/lms/quizzes")
@RequiredArgsConstructor
@Validated
@Tag(name = "Trắc nghiệm - Đề kiểm tra", description = "Tạo đề, xuất bản, chấm, thống kê")
public class QuizController {

    private final QuizServiceImpl quizService;
    private final QuizStatsServiceImpl statsService;

    @Operation(summary = "Danh sách đề")
    @GetMapping
    @RequirePermission(Permissions.QUIZ_VIEW)
    public ApiResponse<PageResponse<QuizListItemDto>> search(@RequestParam(value = "classId", required = false) Long classId,
                                                             @RequestParam(value = "status", required = false) String status,
                                                             @RequestParam(value = "keyword", required = false) String keyword,
                                                             @RequestParam(value = "pageNo", required = false) Integer pageNo,
                                                             @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return ApiResponse.success(quizService.search(classId, status, keyword, pageNo, pageSize));
    }

    @Operation(summary = "Lớp được giao đề")
    @GetMapping("/manageable-classes")
    @RequirePermission(Permissions.QUIZ_CREATE)
    public ApiResponse<List<AnnouncementClassOptionDto>> manageableClasses() {
        return ApiResponse.success(quizService.manageableClasses());
    }

    @Operation(summary = "Chi tiết lượt làm (có đáp án, sự kiện)")
    @GetMapping("/attempts/{aid}")
    @RequirePermission(Permissions.QUIZ_GRADE)
    public ApiResponse<QuizAttemptDetailDto> attempt(@PathVariable("aid") Long aid) {
        return ApiResponse.success(quizService.attempt(aid));
    }

    @Operation(summary = "Chấm / ghi đè điểm 1 câu")
    @PutMapping("/attempts/{aid}/answers/{qid}/grade")
    @RequirePermission(Permissions.QUIZ_GRADE)
    public ApiResponse<QuizAttemptDetailDto> grade(@PathVariable("aid") Long aid, @PathVariable("qid") Long qid,
                                                   @Valid @RequestBody GradeQuizAnswerRequest request) {
        return ApiResponse.success("Đã chấm.", quizService.grade(aid, qid, request));
    }

    @Operation(summary = "Chi tiết đề")
    @GetMapping("/{id}")
    @RequirePermission(Permissions.QUIZ_VIEW)
    public ApiResponse<QuizDto> get(@PathVariable("id") Long id) {
        return ApiResponse.success(quizService.get(id));
    }

    @Operation(summary = "Tạo đề (DRAFT)")
    @PostMapping
    @RequirePermission(Permissions.QUIZ_CREATE)
    public ApiResponse<QuizDto> create(@Valid @RequestBody QuizUpsertRequest request) {
        return ApiResponse.success("Đã tạo bài kiểm tra.", quizService.create(request));
    }

    @Operation(summary = "Sửa đề")
    @PutMapping("/{id}")
    @RequirePermission(Permissions.QUIZ_UPDATE)
    public ApiResponse<QuizDto> update(@PathVariable("id") Long id, @Valid @RequestBody QuizUpsertRequest request) {
        return ApiResponse.success("Đã cập nhật bài kiểm tra.", quizService.update(id, request));
    }

    @Operation(summary = "Xoá mềm đề (chưa có lượt làm)")
    @DeleteMapping("/{id}")
    @RequirePermission(Permissions.QUIZ_DELETE)
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        quizService.delete(id);
        return ApiResponse.success("Đã xoá bài kiểm tra.", null);
    }

    @Operation(summary = "Sao chép đề sang lớp khác")
    @PostMapping("/{id}/copy")
    @RequirePermission(Permissions.QUIZ_CREATE)
    public ApiResponse<QuizDto> copy(@PathVariable("id") Long id, @Valid @RequestBody QuizCopyRequest request) {
        return ApiResponse.success("Đã sao chép bài kiểm tra.", quizService.copy(id, request.getTargetClassId()));
    }

    @Operation(summary = "Xuất bản")
    @PostMapping("/{id}/publish")
    @RequirePermission(Permissions.QUIZ_UPDATE)
    public ApiResponse<QuizDto> publish(@PathVariable("id") Long id, @Valid @RequestBody QuizVersionRequest request) {
        return ApiResponse.success("Đã xuất bản.", quizService.publish(id, request.getExpectedVersion()));
    }

    @Operation(summary = "Huỷ xuất bản (chưa có lượt làm)")
    @PostMapping("/{id}/unpublish")
    @RequirePermission(Permissions.QUIZ_UPDATE)
    public ApiResponse<QuizDto> unpublish(@PathVariable("id") Long id, @Valid @RequestBody QuizVersionRequest request) {
        return ApiResponse.success("Đã huỷ xuất bản.", quizService.unpublish(id, request.getExpectedVersion()));
    }

    @Operation(summary = "Đóng sớm")
    @PostMapping("/{id}/close")
    @RequirePermission(Permissions.QUIZ_UPDATE)
    public ApiResponse<QuizDto> close(@PathVariable("id") Long id, @Valid @RequestBody QuizVersionRequest request) {
        return ApiResponse.success("Đã đóng bài kiểm tra.", quizService.close(id, request.getExpectedVersion()));
    }

    @Operation(summary = "Công bố kết quả (chế độ MANUAL)")
    @PostMapping("/{id}/release-results")
    @RequirePermission(Permissions.QUIZ_GRADE)
    public ApiResponse<QuizDto> releaseResults(@PathVariable("id") Long id) {
        return ApiResponse.success("Đã công bố kết quả.", quizService.releaseResults(id));
    }

    @Operation(summary = "Ma trận học sinh × lượt làm")
    @GetMapping("/{id}/attempts")
    @RequirePermission(Permissions.QUIZ_GRADE)
    public ApiResponse<QuizAttemptMatrixDto> matrix(@PathVariable("id") Long id,
                                                    @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.success(quizService.matrix(id, status));
    }

    @Operation(summary = "Thống kê đề")
    @GetMapping("/{id}/stats")
    @RequirePermission(Permissions.QUIZ_GRADE)
    public ApiResponse<QuizStatsDto> stats(@PathVariable("id") Long id) {
        return ApiResponse.success(statsService.stats(id));
    }

    @Operation(summary = "Xuất Excel điểm")
    @GetMapping("/{id}/export")
    @RequirePermission(Permissions.QUIZ_EXPORT)
    public ResponseEntity<Resource> export(@PathVariable("id") Long id) {
        return download(statsService.export(id));
    }

    static ResponseEntity<Resource> download(ExportFileDto file) {
        ContentDisposition disposition = ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore())
                .contentLength(file.content().length)
                .body(new ByteArrayResource(file.content()));
    }
}
