package com.education.base.controller;

import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.common.ApiResponse;
import com.education.base.dto.request.ConfirmPaymentRequest;
import com.education.base.dto.request.CreateMonthlyInvoiceRequestDto;
import com.education.base.dto.request.TuitionFeeCancelRequest;
import com.education.base.dto.request.TuitionFeeCreateRequest;
import com.education.base.dto.request.TuitionFeeFilterRequest;
import com.education.base.dto.request.TuitionFeeUpdateRequest;
import com.education.base.dto.request.TuitionQrRequest;
import com.education.base.dto.response.GenerateMonthlyInvoicesResponseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.dto.response.TuitionFeeDetailResponse;
import com.education.base.dto.response.TuitionFeeListItemDto;
import com.education.base.dto.response.TuitionQrResponseDto;
import com.education.base.dto.response.TuitionSlipResponseDto;
import com.education.base.service.TuitionFeeService;
import com.education.base.service.TuitionSlipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/tuition-fees")
@RequiredArgsConstructor
@Validated
@Tag(name = "Học phí & VietQR", description = "Quản lý khoản học phí, sinh mã VietQR và xác nhận thanh toán")
public class TuitionFeeController {

    private final TuitionFeeService tuitionFeeService;
    private final TuitionSlipService tuitionSlipService;

    @Operation(summary = "Tìm kiếm khoản học phí có phân trang")
    @GetMapping("/search")
    @RequirePermission({Permissions.TUITION_FEE_VIEW, Permissions.PAYMENT_HISTORY_VIEW, Permissions.TUITION_PAYMENT_VIEW})
    public ApiResponse<PageResponse<TuitionFeeListItemDto>> search(
            @Valid @ModelAttribute TuitionFeeFilterRequest filter) {
        return ApiResponse.success(tuitionFeeService.search(filter));
    }

    @Operation(summary = "Chi tiết khoản học phí",
            description = "Gọi procedure PRC_GET_TUITION_FEE_DETAIL kèm lịch sử giao dịch.")
    @GetMapping("/{id}")
    @RequirePermission({Permissions.TUITION_FEE_VIEW, Permissions.PAYMENT_HISTORY_VIEW, Permissions.TUITION_PAYMENT_VIEW})
    public ApiResponse<TuitionFeeDetailResponse> getDetail(@PathVariable("id") Long id) {
        return ApiResponse.success(tuitionFeeService.getDetail(id));
    }

    @Operation(summary = "Tạo khoản học phí", description = "Mã khoản phí bỏ trống thì tự sinh theo quy luật TUITION.")
    @PostMapping
    @RequirePermission(Permissions.TUITION_FEE_CREATE)
    public ApiResponse<TuitionFeeDetailResponse> create(@Valid @RequestBody TuitionFeeCreateRequest request) {
        return ApiResponse.success("Tạo khoản học phí thành công.", tuitionFeeService.create(request));
    }

    @Operation(summary = "Cập nhật khoản học phí",
            description = "Thay thế tổng tiền / tiền giảm / hạn thu / ghi chú và tính lại trạng thái."
                    + " Lỗi: FEE_CANCELLED, INVALID_DISCOUNT, FEE_TOTAL_BELOW_PAID.")
    @PutMapping("/{id}")
    @RequirePermission(Permissions.TUITION_FEE_UPDATE)
    public ApiResponse<TuitionFeeDetailResponse> update(@PathVariable("id") Long id,
                                                        @Valid @RequestBody TuitionFeeUpdateRequest request) {
        return ApiResponse.success("Cập nhật khoản học phí thành công.", tuitionFeeService.update(id, request));
    }

    @Operation(summary = "Hủy khoản học phí",
            description = "Chuyển STATUS = CANCELLED kèm lý do. Chỉ khi chưa thu đồng nào (FEE_HAS_PAYMENTS).")
    @PostMapping("/{id}/cancel")
    @RequirePermission(Permissions.TUITION_FEE_CANCEL)
    public ApiResponse<TuitionFeeDetailResponse> cancel(@PathVariable("id") Long id,
                                                        @Valid @RequestBody TuitionFeeCancelRequest request) {
        return ApiResponse.success("Đã hủy khoản học phí.", tuitionFeeService.cancel(id, request));
    }

    @Operation(summary = "Xóa khoản học phí",
            description = "Xóa mềm; chỉ khoản UNPAID chưa có giao dịch nào (FEE_NOT_DELETABLE).")
    @DeleteMapping("/{id}")
    @RequirePermission(Permissions.TUITION_FEE_DELETE)
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        tuitionFeeService.delete(id);
        return ApiResponse.success("Đã xóa khoản học phí.", null);
    }

    @Operation(summary = "Sinh mã VietQR thanh toán học phí",
            description = "Tự sinh chuỗi EMVCo và render QR Base64 PNG qua ZXing theo số tiền còn phải thu.")
    @PostMapping("/{id}/create-qr")
    @RequirePermission(Permissions.TUITION_PAYMENT_GEN_QR)
    public ApiResponse<TuitionQrResponseDto> createQr(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) TuitionQrRequest request) {
        TuitionQrRequest payload = request == null ? new TuitionQrRequest() : request;
        return ApiResponse.success("Tạo mã VietQR thành công.", tuitionFeeService.createQr(id, payload));
    }

    @Operation(summary = "Xác nhận thanh toán học phí",
            description = "Ghi giao dịch SUCCESS và cập nhật PAID_AMOUNT/STATUS của khoản học phí trong cùng transaction.")
    @PostMapping("/{id}/confirm-payment")
    @RequirePermission({Permissions.PAYMENT_HISTORY_APPROVE, Permissions.PAYMENT_HISTORY_CREATE})
    public ApiResponse<PaymentTransactionDto> confirmPayment(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) ConfirmPaymentRequest request) {
        ConfirmPaymentRequest payload = request == null ? new ConfirmPaymentRequest() : request;
        return ApiResponse.success("Xác nhận thanh toán thành công.",
                tuitionFeeService.confirmPayment(id, payload));
    }

    @Operation(summary = "Sinh phiếu học phí tháng theo điểm danh PRESENT + LATE",
            description = "Đếm buổi PRESENT + LATE trong tháng, totalAmount = pricePerSession * totalSessions, áp miễn giảm"
                    + " còn hiệu lực (tối đa bằng tổng tiền), tạo/cập nhật hóa đơn hàng loạt; mỗi học sinh một transaction,"
                    + " khoản phí xung đột với số đã thu trả về trong conflicts[], lỗi từng học sinh trong errors[].")
    @PostMapping("/generate-monthly")
    @RequirePermission(Permissions.TUITION_FEE_CREATE)
    public ApiResponse<GenerateMonthlyInvoicesResponseDto> generateMonthly(
            @Valid @RequestBody CreateMonthlyInvoiceRequestDto request) {
        return ApiResponse.success("Đã sinh phiếu học phí theo điểm danh.",
                tuitionSlipService.generateMonthly(request));
    }

    @Operation(summary = "Dữ liệu phiếu học phí điện tử",
            description = "Gọi PRC_GET_TUITION_SLIP_DATA và sinh VietQR (EMVCo + Base64).")
    @GetMapping("/{invoiceId}/slip")
    @RequirePermission({Permissions.TUITION_FEE_VIEW, Permissions.PAYMENT_HISTORY_VIEW, Permissions.TUITION_PAYMENT_VIEW})
    public ApiResponse<TuitionSlipResponseDto> getSlip(@PathVariable("invoiceId") Long invoiceId) {
        return ApiResponse.success(tuitionSlipService.getSlip(invoiceId));
    }

    @Operation(summary = "HTML phiếu học phí (in ấn / gửi Zalo)",
            description = "Trả HTML/CSS card mobile, không bọc ApiResponse.")
    @GetMapping(value = "/{invoiceId}/slip/html", produces = MediaType.TEXT_HTML_VALUE)
    @RequirePermission({Permissions.TUITION_FEE_VIEW, Permissions.PAYMENT_HISTORY_VIEW, Permissions.TUITION_PAYMENT_VIEW})
    public ResponseEntity<String> getSlipHtml(@PathVariable("invoiceId") Long invoiceId) {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(tuitionSlipService.generateSlipHtml(invoiceId));
    }
}
