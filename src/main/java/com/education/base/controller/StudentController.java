package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.StudentCreateRequest;
import com.education.base.dto.request.StudentFilterRequest;
import com.education.base.dto.request.StudentUpdateRequest;
import com.education.base.dto.response.FileResponseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentDetailResponse;
import com.education.base.dto.response.StudentReportDto;
import com.education.base.service.StudentService;
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

/**
 * Module Quản lý Học sinh (menu {@code MENU_STUDENT_LIST}).
 * <p>
 * Tìm kiếm/phân trang đi qua Standalone Procedure, CRUD đi qua Spring Data JPA.
 */
@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
@Validated
@Tag(name = "Quản lý Học sinh", description = "Tìm kiếm, CRUD và đính kèm tài liệu hồ sơ học sinh")
public class StudentController {

    private final StudentService studentService;

    @Operation(summary = "Tìm kiếm học sinh có phân trang",
            description = "Gọi Standalone Procedure PRC_SEARCH_STUDENTS_PAGING, lọc theo từ khóa "
                    + "(mã học sinh/họ tên/email) và trạng thái. Chỉ trả về học sinh chưa bị xóa mềm.")
    @GetMapping("/search")
    public ApiResponse<PageResponse<StudentReportDto>> search(
            @Valid @ModelAttribute StudentFilterRequest filter) {

        return ApiResponse.success(studentService.search(filter));
    }

    @Operation(summary = "Chi tiết học sinh",
            description = "Trả về thông tin học sinh kèm danh sách tài liệu đính kèm "
                    + "(hồ sơ, bảng điểm) lấy qua Procedure PRC_GET_FILES_BY_REF.")
    @GetMapping("/{id:\\d+}")
    public ApiResponse<StudentDetailResponse> getDetail(@PathVariable("id") Long id) {
        return ApiResponse.success(studentService.getDetail(id));
    }

    @Operation(summary = "Thêm mới học sinh (kèm ghi danh hoặc tạo nhanh lớp)",
            description = "Ba kịch bản trong một giao dịch (@Transactional, rollback khi bất kỳ bước nào lỗi): "
                    + "(1) Chỉ tạo học sinh — không gửi classId và newClass. "
                    + "(2) Chọn lớp có sẵn — gửi classId, không gửi newClass. "
                    + "(3) Tạo nhanh lớp rồi ghi danh — gửi newClass (className, gradeLevel 1–12, courseName, teacherId, roomName), "
                    + "không gửi classId. Mã lớp do FN_NEXT_BIZ_CODE (PREFIX + khối + năm + STT). "
                    + "Không được gửi đồng thời classId và newClass. "
                    + "ID do sequence/trigger. Mã học sinh do FN_NEXT_BIZ_CODE theo SYS_CODE_RULES.")
    @PostMapping
    public ApiResponse<StudentDetailResponse> create(@Valid @RequestBody StudentCreateRequest request) {
        return ApiResponse.success("Thêm mới học sinh thành công.", studentService.create(request));
    }

    @Operation(summary = "Cập nhật học sinh",
            description = "Cập nhật hồ sơ. Gửi classId để ghi danh vào lớp đang mở (bỏ qua nếu đã thuộc lớp đó). "
                    + "Mã học sinh là bất biến sau khi tạo.")
    @PutMapping("/{id:\\d+}")
    public ApiResponse<StudentDetailResponse> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody StudentUpdateRequest request) {

        return ApiResponse.success("Cập nhật học sinh thành công.", studentService.update(id, request));
    }

    @Operation(summary = "Xóa mềm học sinh",
            description = "Đặt IS_DELETED = 1, dữ liệu vẫn được giữ lại trong Database để đối soát.")
    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        studentService.softDelete(id);
        return ApiResponse.success("Xóa học sinh thành công.", null);
    }

    @Operation(summary = "Tải tài liệu hồ sơ đính kèm",
            description = "Lưu file vật lý vào outputs/STUDENT/{YYYY}/{MM}/, Database chỉ lưu đường dẫn tương đối.")
    @PostMapping(value = "/{id:\\d+}/upload-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileResponseDto> uploadDocument(
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file) {

        return ApiResponse.success("Tải tài liệu thành công.", studentService.uploadDocument(id, file));
    }
}
