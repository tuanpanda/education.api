package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.PortalAttemptEventRequest;
import com.education.base.dto.request.PortalSaveAnswerRequest;
import com.education.base.dto.response.PortalAttemptDto;
import com.education.base.dto.response.PortalAttemptResultDto;
import com.education.base.dto.response.PortalQuizDetailDto;
import com.education.base.dto.response.PortalQuizListItemDto;
import com.education.base.dto.response.PortalSaveAnswerDto;
import com.education.base.dto.response.PortalSubmitResultDto;
import com.education.base.security.PortalAccess;
import com.education.base.service.quiz.impl.PortalQuizServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GĐ4 - kiểm tra online trên cổng học sinh. Hợp đồng: docs/contracts/quiz-api.md §3. */
@RestController
@RequestMapping("/api/v1/portal/me")
@RequiredArgsConstructor
@Validated
@PortalAccess
@Tag(name = "Cổng học sinh - Kiểm tra", description = "Làm bài trắc nghiệm, nộp, xem kết quả")
public class PortalQuizController {

    private final PortalQuizServiceImpl portalQuizService;

    @Operation(summary = "Danh sách đề theo tab (UPCOMING|OPEN|DONE)")
    @GetMapping("/quizzes")
    public ApiResponse<List<PortalQuizListItemDto>> list(@RequestParam(value = "tab", required = false) String tab,
                                                         @RequestParam(value = "status", required = false) String status) {
        // "status" là bí danh của "tab" (ca kiểm thử QZ-K12).
        return ApiResponse.success(portalQuizService.list(tab != null ? tab : status));
    }

    @Operation(summary = "Chi tiết đề")
    @GetMapping("/quizzes/{id}")
    public ApiResponse<PortalQuizDetailDto> get(@PathVariable("id") Long id) {
        return ApiResponse.success(portalQuizService.get(id));
    }

    @Operation(summary = "Bắt đầu / tiếp tục làm bài")
    @PostMapping("/quizzes/{id}/start")
    public ApiResponse<PortalAttemptDto> start(@PathVariable("id") Long id, HttpServletRequest http) {
        return ApiResponse.success(portalQuizService.start(id, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT)));
    }

    @Operation(summary = "Lượt đang làm (khôi phục sau tải lại)")
    @GetMapping("/attempts/{aid}")
    public ApiResponse<PortalAttemptDto> attempt(@PathVariable("aid") Long aid) {
        return ApiResponse.success(portalQuizService.getAttempt(aid));
    }

    @Operation(summary = "Lưu đáp án 1 câu")
    @PutMapping("/attempts/{aid}/answers/{qid}")
    public ApiResponse<PortalSaveAnswerDto> save(@PathVariable("aid") Long aid, @PathVariable("qid") Long qid,
                                                 @Valid @RequestBody PortalSaveAnswerRequest request) {
        return ApiResponse.success(portalQuizService.saveAnswer(aid, qid, request));
    }

    @Operation(summary = "Nộp bài")
    @PostMapping("/attempts/{aid}/submit")
    public ApiResponse<PortalSubmitResultDto> submit(@PathVariable("aid") Long aid) {
        return ApiResponse.success("Đã nộp bài.", portalQuizService.submit(aid));
    }

    @Operation(summary = "Kết quả lượt làm")
    @GetMapping("/attempts/{aid}/result")
    public ApiResponse<PortalAttemptResultDto> result(@PathVariable("aid") Long aid) {
        return ApiResponse.success(portalQuizService.result(aid));
    }

    @Operation(summary = "Ghi sự kiện rời màn hình / tải lại")
    @PostMapping("/attempts/{aid}/events")
    public ApiResponse<Void> event(@PathVariable("aid") Long aid, @Valid @RequestBody PortalAttemptEventRequest request,
                                   HttpServletRequest http) {
        portalQuizService.event(aid, request, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
        return ApiResponse.success(null);
    }
}
