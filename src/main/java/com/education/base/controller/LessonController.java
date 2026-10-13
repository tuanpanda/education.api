package com.education.base.controller;

import com.education.base.dto.request.LessonReorderRequest;
import com.education.base.dto.request.LessonResourceReorderRequest;
import com.education.base.dto.request.LessonResourceRequest;
import com.education.base.dto.request.LessonSectionUpsertRequest;
import com.education.base.dto.request.LessonUpsertRequest;
import com.education.base.dto.response.AnnouncementClassOptionDto;
import com.education.base.dto.response.ClassLessonProgressDto;
import com.education.base.dto.response.LessonDto;
import com.education.base.dto.response.LessonResourceDto;
import com.education.base.dto.response.LessonSectionDto;
import com.education.base.dto.response.LessonTreeDto;
import com.education.base.service.lesson.LessonService;
import com.education.base.common.ApiResponse;
import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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

import java.util.List;

/**
 * Chương / bài / tài nguyên / tiến độ lớp (§2.2 docs/portal-p3-api.md). {@code @RequirePermission} rộng ("một trong");
 * service kiểm đúng nhánh theo chủ (CLASS: MENU_LESSON + phạm vi GV; COURSE: MENU_COURSE).
 */
@RestController
@RequestMapping("/api/v1/lms")
@RequiredArgsConstructor
@Validated
@Tag(name = "Học liệu", description = "Chương, bài học, tài liệu, video, tiến độ")
public class LessonController {

    private final LessonService lessonService;

    @Operation(summary = "Cây chương/bài theo chủ (COURSE / CLASS)")
    @GetMapping("/lessons/tree")
    @RequirePermission({Permissions.LESSON_VIEW, Permissions.COURSE_VIEW})
    public ApiResponse<LessonTreeDto> tree(@RequestParam("ownerType") String ownerType,
                                           @RequestParam("ownerId") Long ownerId) {
        return ApiResponse.success(lessonService.getTree(ownerType, ownerId));
    }

    @Operation(summary = "Lớp được soạn học liệu")
    @GetMapping("/lessons/manageable-classes")
    @RequirePermission(Permissions.LESSON_VIEW)
    public ApiResponse<List<AnnouncementClassOptionDto>> manageableClasses() {
        return ApiResponse.success(lessonService.listManageableClasses());
    }

    @Operation(summary = "Tạo chương")
    @PostMapping("/lesson-sections")
    @RequirePermission({Permissions.LESSON_CREATE, Permissions.COURSE_UPDATE})
    public ApiResponse<LessonSectionDto> createSection(@Valid @RequestBody LessonSectionUpsertRequest request) {
        return ApiResponse.success("Đã tạo chương.", lessonService.createSection(request));
    }

    @Operation(summary = "Đổi tên chương")
    @PutMapping("/lesson-sections/{id}")
    @RequirePermission({Permissions.LESSON_UPDATE, Permissions.COURSE_UPDATE})
    public ApiResponse<LessonSectionDto> updateSection(@PathVariable("id") Long id,
                                                       @Valid @RequestBody LessonSectionUpsertRequest request) {
        return ApiResponse.success("Đã cập nhật chương.", lessonService.updateSection(id, request));
    }

    @Operation(summary = "Xoá chương (phải rỗng)")
    @DeleteMapping("/lesson-sections/{id}")
    @RequirePermission({Permissions.LESSON_DELETE, Permissions.COURSE_UPDATE})
    public ApiResponse<Void> deleteSection(@PathVariable("id") Long id) {
        lessonService.deleteSection(id);
        return ApiResponse.success("Đã xoá chương.", null);
    }

    @Operation(summary = "Chi tiết bài học")
    @GetMapping("/lessons/{id}")
    @RequirePermission({Permissions.LESSON_VIEW, Permissions.COURSE_VIEW})
    public ApiResponse<LessonDto> getById(@PathVariable("id") Long id) {
        return ApiResponse.success(lessonService.getById(id));
    }

    @Operation(summary = "Tạo bài học (nháp)")
    @PostMapping("/lessons")
    @RequirePermission({Permissions.LESSON_CREATE, Permissions.COURSE_UPDATE})
    public ApiResponse<LessonDto> create(@Valid @RequestBody LessonUpsertRequest request) {
        return ApiResponse.success("Đã tạo bài học.", lessonService.create(request));
    }

    @Operation(summary = "Sắp xếp chương/bài của một chủ")
    @PutMapping("/lessons/reorder")
    @RequirePermission({Permissions.LESSON_UPDATE, Permissions.COURSE_UPDATE})
    public ApiResponse<LessonTreeDto> reorder(@Valid @RequestBody LessonReorderRequest request) {
        return ApiResponse.success("Đã sắp xếp.", lessonService.reorder(request));
    }

    @Operation(summary = "Cập nhật bài học")
    @PutMapping("/lessons/{id}")
    @RequirePermission({Permissions.LESSON_UPDATE, Permissions.COURSE_UPDATE})
    public ApiResponse<LessonDto> update(@PathVariable("id") Long id, @Valid @RequestBody LessonUpsertRequest request) {
        return ApiResponse.success("Đã cập nhật bài học.", lessonService.update(id, request));
    }

    @Operation(summary = "Xoá mềm bài học")
    @DeleteMapping("/lessons/{id}")
    @RequirePermission({Permissions.LESSON_DELETE, Permissions.COURSE_UPDATE})
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        lessonService.softDelete(id);
        return ApiResponse.success("Đã xoá bài học.", null);
    }

    @Operation(summary = "Đăng bài")
    @PostMapping("/lessons/{id}/publish")
    @RequirePermission({Permissions.LESSON_UPDATE, Permissions.COURSE_UPDATE})
    public ApiResponse<LessonDto> publish(@PathVariable("id") Long id) {
        return ApiResponse.success("Đã đăng bài học.", lessonService.publish(id));
    }

    @Operation(summary = "Gỡ đăng (về nháp)")
    @PostMapping("/lessons/{id}/unpublish")
    @RequirePermission({Permissions.LESSON_UPDATE, Permissions.COURSE_UPDATE})
    public ApiResponse<LessonDto> unpublish(@PathVariable("id") Long id) {
        return ApiResponse.success("Đã gỡ đăng bài học.", lessonService.unpublish(id));
    }

    @Operation(summary = "Thêm video / link / bài tập")
    @PostMapping("/lessons/{id}/resources")
    @RequirePermission({Permissions.LESSON_UPDATE, Permissions.COURSE_UPDATE})
    public ApiResponse<LessonResourceDto> addResource(@PathVariable("id") Long id,
                                                      @Valid @RequestBody LessonResourceRequest request) {
        return ApiResponse.success("Đã thêm tài liệu.", lessonService.addResource(id, request));
    }

    @Operation(summary = "Tải file tài liệu bài học")
    @PostMapping(value = "/lessons/{id}/resources/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission({Permissions.LESSON_UPDATE, Permissions.COURSE_UPDATE})
    public ApiResponse<LessonResourceDto> uploadFile(@PathVariable("id") Long id,
                                                     @RequestParam("file") MultipartFile file,
                                                     @RequestParam(value = "title", required = false) String title) {
        return ApiResponse.success("Đã tải file lên.", lessonService.uploadResourceFile(id, file, title));
    }

    @Operation(summary = "Sắp xếp tài liệu của bài")
    @PutMapping("/lessons/{id}/resources/reorder")
    @RequirePermission({Permissions.LESSON_UPDATE, Permissions.COURSE_UPDATE})
    public ApiResponse<List<LessonResourceDto>> reorderResources(@PathVariable("id") Long id,
                                                                 @Valid @RequestBody LessonResourceReorderRequest request) {
        return ApiResponse.success("Đã sắp xếp tài liệu.", lessonService.reorderResources(id, request));
    }

    @Operation(summary = "Cập nhật tài liệu")
    @PutMapping("/lesson-resources/{rid}")
    @RequirePermission({Permissions.LESSON_UPDATE, Permissions.COURSE_UPDATE})
    public ApiResponse<LessonResourceDto> updateResource(@PathVariable("rid") Long rid,
                                                         @Valid @RequestBody LessonResourceRequest request) {
        return ApiResponse.success("Đã cập nhật tài liệu.", lessonService.updateResource(rid, request));
    }

    @Operation(summary = "Gỡ tài liệu")
    @DeleteMapping("/lesson-resources/{rid}")
    @RequirePermission({Permissions.LESSON_UPDATE, Permissions.COURSE_UPDATE})
    public ApiResponse<Void> deleteResource(@PathVariable("rid") Long rid) {
        lessonService.deleteResource(rid);
        return ApiResponse.success("Đã gỡ tài liệu.", null);
    }

    @Operation(summary = "Ma trận tiến độ học liệu của lớp")
    @GetMapping("/classes/{classId}/lesson-progress")
    @RequirePermission(Permissions.LESSON_VIEW)
    public ApiResponse<ClassLessonProgressDto> classProgress(@PathVariable("classId") Long classId) {
        return ApiResponse.success(lessonService.getClassProgress(classId));
    }
}
