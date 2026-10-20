package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.QuestionUpsertRequest;
import com.education.base.dto.response.ExportFileDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.QuestionDto;
import com.education.base.dto.response.QuestionImportResultDto;
import com.education.base.dto.response.QuestionListItemDto;
import com.education.base.dto.response.QuestionTopicCountDto;
import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.service.quiz.impl.QuestionImportServiceImpl;
import com.education.base.service.quiz.impl.QuestionServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
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

/** GĐ4 - ngân hàng câu hỏi. Hợp đồng: docs/contracts/quiz-api.md §2.1. */
@RestController
@RequestMapping("/api/v1/lms/questions")
@RequiredArgsConstructor
@Validated
@Tag(name = "Trắc nghiệm - Ngân hàng câu hỏi", description = "Câu hỏi, nhập Excel")
public class QuestionController {

    private final QuestionServiceImpl questionService;
    private final QuestionImportServiceImpl importService;

    @Operation(summary = "Danh sách câu hỏi")
    @GetMapping
    @RequirePermission(Permissions.QUESTION_BANK_VIEW)
    public ApiResponse<PageResponse<QuestionListItemDto>> search(
            @RequestParam(value = "subjectName", required = false) String subjectName,
            @RequestParam(value = "gradeLevel", required = false) String gradeLevel,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "difficulty", required = false) Integer difficulty,
            @RequestParam(value = "visibility", required = false) String visibility,
            @RequestParam(value = "mine", required = false) Boolean mine,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return ApiResponse.success(questionService.search(subjectName, gradeLevel, topic, type, difficulty, visibility,
                mine, keyword, pageNo, pageSize));
    }

    @Operation(summary = "Số câu theo chủ đề × độ khó")
    @GetMapping("/topics")
    @RequirePermission(Permissions.QUESTION_BANK_VIEW)
    public ApiResponse<List<QuestionTopicCountDto>> topics(
            @RequestParam(value = "subjectName", required = false) String subjectName,
            @RequestParam(value = "gradeLevel", required = false) String gradeLevel) {
        return ApiResponse.success(questionService.topics(subjectName, gradeLevel));
    }

    @Operation(summary = "Tải file mẫu nhập câu hỏi")
    @GetMapping("/import-template")
    @RequirePermission(Permissions.QUESTION_BANK_CREATE)
    public ResponseEntity<Resource> template() {
        ExportFileDto file = importService.template();
        return QuizController.download(file);
    }

    @Operation(summary = "Chi tiết câu hỏi (có đáp án)")
    @GetMapping("/{id}")
    @RequirePermission(Permissions.QUESTION_BANK_VIEW)
    public ApiResponse<QuestionDto> get(@PathVariable("id") Long id) {
        return ApiResponse.success(questionService.get(id));
    }

    @Operation(summary = "Tạo câu hỏi")
    @PostMapping
    @RequirePermission(Permissions.QUESTION_BANK_CREATE)
    public ApiResponse<QuestionDto> create(@Valid @RequestBody QuestionUpsertRequest request) {
        return ApiResponse.success("Đã tạo câu hỏi.", questionService.create(request));
    }

    @Operation(summary = "Sửa câu hỏi")
    @PutMapping("/{id}")
    @RequirePermission(Permissions.QUESTION_BANK_UPDATE)
    public ApiResponse<QuestionDto> update(@PathVariable("id") Long id, @Valid @RequestBody QuestionUpsertRequest request) {
        return ApiResponse.success("Đã cập nhật câu hỏi.", questionService.update(id, request));
    }

    @Operation(summary = "Xoá mềm câu hỏi")
    @DeleteMapping("/{id}")
    @RequirePermission(Permissions.QUESTION_BANK_DELETE)
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        questionService.delete(id);
        return ApiResponse.success("Đã xoá câu hỏi.", null);
    }

    @Operation(summary = "Nhập câu hỏi từ Excel (all-or-nothing)")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(Permissions.QUESTION_BANK_CREATE)
    public ApiResponse<QuestionImportResultDto> importFile(@RequestParam("file") MultipartFile file,
                                                           @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return ApiResponse.success(dryRun ? "File hợp lệ." : "Đã nhập câu hỏi.", importService.importFile(file, dryRun));
    }
}
