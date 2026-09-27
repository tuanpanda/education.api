package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.ConfirmPaymentRequest;
import com.education.base.dto.request.TuitionFeeCreateRequest;
import com.education.base.dto.request.TuitionFeeFilterRequest;
import com.education.base.dto.request.TuitionQrRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.dto.response.TuitionFeeDetailResponse;
import com.education.base.dto.response.TuitionFeeReportDto;
import com.education.base.dto.response.TuitionQrResponseDto;
import com.education.base.service.TuitionFeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tuition-fees")
@RequiredArgsConstructor
@Validated
@Tag(name = "Học phí & VietQR", description = "Quản lý khoản học phí, sinh mã VietQR và xác nhận thanh toán")
public class TuitionFeeController {

    private final TuitionFeeService tuitionFeeService;

    @Operation(summary = "Tìm kiếm khoản học phí có phân trang")
    @GetMapping("/search")
    public ApiResponse<PageResponse<TuitionFeeReportDto>> search(
            @Valid @ModelAttribute TuitionFeeFilterRequest filter) {
        return ApiResponse.success(tuitionFeeService.search(filter));
    }

    @Operation(summary = "Chi tiết khoản học phí",
            description = "Gọi procedure PRC_GET_TUITION_FEE_DETAIL kèm lịch sử giao dịch.")
    @GetMapping("/{id}")
    public ApiResponse<TuitionFeeDetailResponse> getDetail(@PathVariable("id") Long id) {
        return ApiResponse.success(tuitionFeeService.getDetail(id));
    }

    @Operation(summary = "Tạo khoản học phí")
    @PostMapping
    public ApiResponse<TuitionFeeDetailResponse> create(@Valid @RequestBody TuitionFeeCreateRequest request) {
        return ApiResponse.success("Tạo khoản học phí thành công.", tuitionFeeService.create(request));
    }

    @Operation(summary = "Sinh mã VietQR thanh toán học phí",
            description = "Tự sinh chuỗi EMVCo và render QR Base64 PNG qua ZXing theo số tiền còn phải thu.")
    @PostMapping("/{id}/create-qr")
    public ApiResponse<TuitionQrResponseDto> createQr(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) TuitionQrRequest request) {
        TuitionQrRequest payload = request == null ? new TuitionQrRequest() : request;
        return ApiResponse.success("Tạo mã VietQR thành công.", tuitionFeeService.createQr(id, payload));
    }

    @Operation(summary = "Xác nhận thanh toán học phí",
            description = "Ghi giao dịch SUCCESS và cập nhật PAID_AMOUNT/STATUS của khoản học phí trong cùng transaction.")
    @PostMapping("/{id}/confirm-payment")
    public ApiResponse<PaymentTransactionDto> confirmPayment(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) ConfirmPaymentRequest request) {
        ConfirmPaymentRequest payload = request == null ? new ConfirmPaymentRequest() : request;
        return ApiResponse.success("Xác nhận thanh toán thành công.",
                tuitionFeeService.confirmPayment(id, payload));
    }
}
