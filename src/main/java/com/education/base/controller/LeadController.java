package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.LeadConvertRequest;
import com.education.base.dto.request.LeadCreateRequest;
import com.education.base.dto.request.LeadFilterRequest;
import com.education.base.dto.request.LeadUpdateRequest;
import com.education.base.dto.response.LeadDetailResponse;
import com.education.base.dto.response.LeadReportDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentDetailResponse;
import com.education.base.service.LeadService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
@Validated
@Tag(name = "Tuyển sinh", description = "Quản lý hồ sơ lead và chuyển đổi thành học sinh")
public class LeadController {

    private final LeadService leadService;

    @Operation(summary = "Tìm kiếm lead có phân trang")
    @GetMapping("/search")
    public ApiResponse<PageResponse<LeadReportDto>> search(@Valid @ModelAttribute LeadFilterRequest filter) {
        return ApiResponse.success(leadService.search(filter));
    }

    @Operation(summary = "Chi tiết lead")
    @GetMapping("/{id}")
    public ApiResponse<LeadDetailResponse> getDetail(@PathVariable("id") Long id) {
        return ApiResponse.success(leadService.getDetail(id));
    }

    @Operation(summary = "Thêm mới lead")
    @PostMapping
    public ApiResponse<LeadDetailResponse> create(@Valid @RequestBody LeadCreateRequest request) {
        return ApiResponse.success("Thêm mới lead thành công.", leadService.create(request));
    }

    @Operation(summary = "Cập nhật lead")
    @PutMapping("/{id}")
    public ApiResponse<LeadDetailResponse> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody LeadUpdateRequest request) {
        return ApiResponse.success("Cập nhật lead thành công.", leadService.update(id, request));
    }

    @Operation(summary = "Xóa mềm lead")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        leadService.softDelete(id);
        return ApiResponse.success("Xóa lead thành công.", null);
    }

    @Operation(summary = "Chuyển lead thành học sinh",
            description = "Tạo EDU_STUDENTS, gán CONVERTED_STUDENT_ID và đặt trạng thái CONVERTED trong cùng transaction.")
    @PostMapping("/{id}/convert")
    public ApiResponse<StudentDetailResponse> convert(
            @PathVariable("id") Long id,
            @Valid @RequestBody LeadConvertRequest request) {
        return ApiResponse.success("Chuyển lead thành học sinh thành công.", leadService.convert(id, request));
    }
}
