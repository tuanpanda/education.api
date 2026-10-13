package com.education.base.controller;

import com.education.base.dto.request.CourseFilterRequest;
import com.education.base.dto.request.CourseUpsertRequest;
import com.education.base.dto.response.CourseClassDto;
import com.education.base.dto.response.CourseCopyResultDto;
import com.education.base.dto.response.CourseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.service.lesson.CourseService;
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

/** Khoá học mẫu (§2.1 docs/portal-p3-api.md). */
@RestController
@RequestMapping("/api/v1/lms/courses")
@RequiredArgsConstructor
@Validated
@Tag(name = "Khoá học", description = "Khoá học mẫu, gắn lớp, chép sang lớp")
public class CourseController {

    private final CourseService courseService;

    @Operation(summary = "Danh sách khoá học (phân trang 1-based)")
    @GetMapping
    @RequirePermission(Permissions.COURSE_VIEW)
    public ApiResponse<PageResponse<CourseDto>> search(@Valid @ModelAttribute CourseFilterRequest filter) {
        return ApiResponse.success(courseService.search(filter));
    }

    @Operation(summary = "Chi tiết khoá học")
    @GetMapping("/{id}")
    @RequirePermission(Permissions.COURSE_VIEW)
    public ApiResponse<CourseDto> getById(@PathVariable("id") Long id) {
        return ApiResponse.success(courseService.getById(id));
    }

    @Operation(summary = "Tạo khoá học")
    @PostMapping
    @RequirePermission(Permissions.COURSE_CREATE)
    public ApiResponse<CourseDto> create(@Valid @RequestBody CourseUpsertRequest request) {
        return ApiResponse.success("Đã tạo khoá học.", courseService.create(request));
    }

    @Operation(summary = "Cập nhật khoá học")
    @PutMapping("/{id}")
    @RequirePermission(Permissions.COURSE_UPDATE)
    public ApiResponse<CourseDto> update(@PathVariable("id") Long id, @Valid @RequestBody CourseUpsertRequest request) {
        return ApiResponse.success("Đã cập nhật khoá học.", courseService.update(id, request));
    }

    @Operation(summary = "Xoá mềm khoá học")
    @DeleteMapping("/{id}")
    @RequirePermission(Permissions.COURSE_DELETE)
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        courseService.softDelete(id);
        return ApiResponse.success("Đã xoá khoá học.", null);
    }

    @Operation(summary = "Lớp đang gắn khoá")
    @GetMapping("/{id}/classes")
    @RequirePermission(Permissions.COURSE_VIEW)
    public ApiResponse<List<CourseClassDto>> classes(@PathVariable("id") Long id) {
        return ApiResponse.success(courseService.listClasses(id));
    }

    @Operation(summary = "Gắn lớp vào khoá (liên kết sống)")
    @PutMapping("/{id}/classes/{classId}")
    @RequirePermission(Permissions.COURSE_UPDATE)
    public ApiResponse<CourseClassDto> link(@PathVariable("id") Long id, @PathVariable("classId") Long classId) {
        return ApiResponse.success("Đã gắn lớp vào khoá học.", courseService.linkClass(id, classId));
    }

    @Operation(summary = "Gỡ lớp khỏi khoá")
    @DeleteMapping("/{id}/classes/{classId}")
    @RequirePermission(Permissions.COURSE_UPDATE)
    public ApiResponse<Void> unlink(@PathVariable("id") Long id, @PathVariable("classId") Long classId) {
        courseService.unlinkClass(id, classId);
        return ApiResponse.success("Đã gỡ lớp khỏi khoá học.", null);
    }

    @Operation(summary = "Chép chương/bài của khoá thành bản riêng của lớp")
    @PostMapping("/{id}/copy-to-class/{classId}")
    @RequirePermission(Permissions.LESSON_CREATE)
    public ApiResponse<CourseCopyResultDto> copyToClass(@PathVariable("id") Long id,
                                                        @PathVariable("classId") Long classId) {
        return ApiResponse.success("Đã chép khoá học sang lớp.", courseService.copyToClass(id, classId));
    }
}
