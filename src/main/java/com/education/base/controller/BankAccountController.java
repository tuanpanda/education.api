package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.BankAccountUpsertRequest;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.service.BankAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bank-accounts")
@RequiredArgsConstructor
@Validated
@Tag(name = "Tài khoản thụ hưởng", description = "Cấu hình số tài khoản VietQR; chỉ một STK đang sử dụng")
public class BankAccountController {

    private final BankAccountService bankAccountService;

    @Operation(summary = "Danh sách số tài khoản")
    @GetMapping
    public ApiResponse<List<BankAccountResponseDto>> list() {
        return ApiResponse.success(bankAccountService.list());
    }

    @Operation(summary = "STK đang sử dụng")
    @GetMapping("/active")
    public ApiResponse<BankAccountResponseDto> getActive() {
        return ApiResponse.success(bankAccountService.requireActive());
    }

    @Operation(summary = "Chi tiết số tài khoản")
    @GetMapping("/{id}")
    public ApiResponse<BankAccountResponseDto> getById(@PathVariable("id") Long id) {
        return ApiResponse.success(bankAccountService.getById(id));
    }

    @Operation(summary = "Thêm số tài khoản")
    @PostMapping
    public ApiResponse<BankAccountResponseDto> create(@Valid @RequestBody BankAccountUpsertRequest request) {
        return ApiResponse.success("Đã lưu số tài khoản.", bankAccountService.create(request));
    }

    @Operation(summary = "Cập nhật số tài khoản")
    @PutMapping("/{id}")
    public ApiResponse<BankAccountResponseDto> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody BankAccountUpsertRequest request) {
        return ApiResponse.success("Đã cập nhật số tài khoản.", bankAccountService.update(id, request));
    }

    @Operation(summary = "Đặt làm STK đang sử dụng (các STK khác sẽ tắt)")
    @PostMapping("/{id}/activate")
    public ApiResponse<BankAccountResponseDto> activate(@PathVariable("id") Long id) {
        return ApiResponse.success("Đã chuyển STK đang sử dụng.", bankAccountService.activate(id));
    }

    @Operation(summary = "Xóa mềm số tài khoản (không xóa được STK đang dùng)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        bankAccountService.softDelete(id);
        return ApiResponse.success("Đã xóa số tài khoản.", null);
    }
}
