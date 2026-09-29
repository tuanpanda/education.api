package com.education.base.controller;

import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.common.ApiResponse;
import com.education.base.dto.request.ClassCreateRequest;
import com.education.base.dto.request.ClassFilterRequest;
import com.education.base.dto.request.ClassUpdateRequest;
import com.education.base.dto.request.EnrollStudentsRequest;
import com.education.base.dto.response.ClassDetailResponse;
import com.education.base.dto.response.ClassOptionResponse;
import com.education.base.dto.response.ClassReportDto;
import com.education.base.dto.response.EnrolledStudentDto;
import com.education.base.dto.response.NextClassCodeResponse;
import com.education.base.dto.response.PageResponse;
import com.education.base.service.ClassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@RestController
@RequestMapping("/api/v1/classes")
@RequiredArgsConstructor
@Validated
@Tag(name = "Quản lý Lớp học", description = "CRUD lớp học, ghi danh và tìm kiếm phân trang qua procedure")
public class ClassController {

    private final ClassService classService;

    @Operation(summary = "Tìm kiếm lớp học có phân trang",
            description = "Gọi Standalone Procedure PRC_SEARCH_CLASSES_PAGING.")
    @GetMapping("/search")
    @RequirePermission({Permissions.CLASS_VIEW, Permissions.ATTENDANCE_VIEW, Permissions.GRADE_VIEW, Permissions.TIMETABLE_VIEW, Permissions.TUITION_FEE_VIEW})
    public ApiResponse<PageResponse<ClassReportDto>> search(@Valid @ModelAttribute ClassFilterRequest filter) {
        return ApiResponse.success(classService.search(filter));
    }

    @Operation(summary = "Danh sách lớp đang mở cho dropdown",
            description = "Trả về các lớp đang mở (OPEN, chưa xóa mềm), sắp xếp mới nhất trước. "
                    + "Dùng cho Select trên form thêm học sinh.")
    @GetMapping("/options")
    public ApiResponse<List<ClassOptionResponse>> listOpenOptions() {
        return ApiResponse.success(classService.listOpenOptions());
    }

    @Operation(summary = "Xem trước mã lớp tự động",
            description = "Đọc SYS_CODE_RULES (RULE_CODE=CLASS) và ghép {PREFIX}{GRADE}{YYYY}{SEQ} "
                    + "giống FN_NEXT_BIZ_CODE nhưng không tăng LAST_SEQ. "
                    + "Mã thật được trigger cấp lúc thêm lớp. Ví dụ khối 9 → LH920260001.")
    @GetMapping("/next-code")
    @RequirePermission({Permissions.CLASS_VIEW, Permissions.CLASS_CREATE, Permissions.STUDENT_CREATE})
    public ApiResponse<NextClassCodeResponse> peekNextCode(
            @RequestParam("gradeLevel")
            @NotNull(message = "Khối lớp không được để trống")
            @Min(value = 1, message = "Khối lớp tối thiểu là 1")
            @Max(value = 12, message = "Khối lớp tối đa là 12")
            Integer gradeLevel) {
        return ApiResponse.success(classService.peekNextClassCode(gradeLevel));
    }

    @Operation(summary = "Chi tiết lớp học")
    @GetMapping("/{id}")
    @RequirePermission({Permissions.CLASS_VIEW, Permissions.ATTENDANCE_VIEW, Permissions.GRADE_VIEW, Permissions.TIMETABLE_VIEW})
    public ApiResponse<ClassDetailResponse> getDetail(@PathVariable("id") Long id) {
        return ApiResponse.success(classService.getDetail(id));
    }

    @Operation(summary = "Thêm mới lớp học",
            description = "ID do sequence/trigger. Mã lớp do FN_NEXT_BIZ_CODE sinh theo "
                    + "SYS_CODE_RULES: {PREFIX}{GRADE}{YYYY}{SEQ} (ví dụ khối 9 → LH920260001). "
                    + "gradeLevel (1–12) bắt buộc khi tạo.")
    @PostMapping
    @RequirePermission(Permissions.CLASS_CREATE)
    public ApiResponse<ClassDetailResponse> create(@Valid @RequestBody ClassCreateRequest request) {
        return ApiResponse.success("Thêm mới lớp học thành công.", classService.create(request));
    }

    @Operation(summary = "Cập nhật lớp học")
    @PutMapping("/{id}")
    @RequirePermission(Permissions.CLASS_UPDATE)
    public ApiResponse<ClassDetailResponse> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody ClassUpdateRequest request) {
        return ApiResponse.success("Cập nhật lớp học thành công.", classService.update(id, request));
    }

    @Operation(summary = "Xóa mềm lớp học")
    @DeleteMapping("/{id}")
    @RequirePermission(Permissions.CLASS_DELETE)
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        classService.softDelete(id);
        return ApiResponse.success("Xóa lớp học thành công.", null);
    }

    @Operation(summary = "Ghi danh học sinh vào lớp")
    @PostMapping("/{id}/enroll")
    @RequirePermission(Permissions.CLASS_UPDATE)
    public ApiResponse<List<EnrolledStudentDto>> enroll(
            @PathVariable("id") Long id,
            @Valid @RequestBody EnrollStudentsRequest request) {
        return ApiResponse.success("Ghi danh học sinh thành công.", classService.enroll(id, request));
    }

    @Operation(summary = "Xóa học sinh khỏi lớp",
            description = "Xóa mềm ghi danh. Học sinh vẫn còn trong hệ thống, chỉ rời lớp này.")
    @DeleteMapping("/{id}/students/{studentId}")
    @RequirePermission(Permissions.CLASS_UPDATE)
    public ApiResponse<Void> unenroll(
            @PathVariable("id") Long id,
            @PathVariable("studentId") Long studentId) {
        classService.unenroll(id, studentId);
        return ApiResponse.success("Đã xóa học sinh khỏi lớp.", null);
    }
}
