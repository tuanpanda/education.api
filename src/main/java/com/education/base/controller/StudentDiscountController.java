package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.StudentDiscountFilterRequest;
import com.education.base.dto.request.StudentDiscountUpsertRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentDiscountDto;
import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.service.StudentDiscountService;
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
@RequestMapping("/api/v1/fee-discounts")
@RequiredArgsConstructor
@Validated
@Tag(name = "Miễn giảm học phí", description = "Miễn giảm / học bổng theo học sinh, áp dụng khi sinh phiếu học phí tháng")
public class StudentDiscountController {

    private final StudentDiscountService studentDiscountService;

    @Operation(summary = "Tìm kiếm miễn giảm có phân trang")
    @GetMapping("/search")
    @RequirePermission(Permissions.FEE_DISCOUNT_VIEW)
    public ApiResponse<PageResponse<StudentDiscountDto>> search(
            @Valid @ModelAttribute StudentDiscountFilterRequest filter) {
        return ApiResponse.success(studentDiscountService.search(filter));
    }

    @Operation(summary = "Chi tiết miễn giảm")
    @GetMapping("/{id}")
    @RequirePermission(Permissions.FEE_DISCOUNT_VIEW)
    public ApiResponse<StudentDiscountDto> getById(@PathVariable("id") Long id) {
        return ApiResponse.success(studentDiscountService.getById(id));
    }

    @Operation(summary = "Thêm miễn giảm / học bổng")
    @PostMapping
    @RequirePermission(Permissions.FEE_DISCOUNT_CREATE)
    public ApiResponse<StudentDiscountDto> create(@Valid @RequestBody StudentDiscountUpsertRequest request) {
        return ApiResponse.success("Đã lưu miễn giảm.", studentDiscountService.create(request));
    }

    @Operation(summary = "Cập nhật miễn giảm / học bổng",
            description = "Chỉ ảnh hưởng các lần sinh phiếu tháng sau; phiếu đã sinh giữ nguyên số tiền giảm.")
    @PutMapping("/{id}")
    @RequirePermission(Permissions.FEE_DISCOUNT_UPDATE)
    public ApiResponse<StudentDiscountDto> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody StudentDiscountUpsertRequest request) {
        return ApiResponse.success("Đã cập nhật miễn giảm.", studentDiscountService.update(id, request));
    }

    @Operation(summary = "Xóa mềm miễn giảm / học bổng")
    @DeleteMapping("/{id}")
    @RequirePermission(Permissions.FEE_DISCOUNT_DELETE)
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        studentDiscountService.softDelete(id);
        return ApiResponse.success("Đã xóa miễn giảm.", null);
    }
}
