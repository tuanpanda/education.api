package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.dto.request.PaymentTransactionFilterRequest;
import com.education.base.dto.request.RefundTransactionRequest;
import com.education.base.dto.request.VoidTransactionRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.PaymentTransactionDetailResponse;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Lịch sử giao dịch thanh toán: tra cứu, chi tiết, phiếu thu, hủy và hoàn tiền (Stream B).
 */
@RestController
@RequestMapping("/api/v1/payments/transactions")
@RequiredArgsConstructor
@Validated
@Tag(name = "Giao dịch thanh toán", description = "Tra cứu giao dịch, in phiếu thu, hủy và hoàn tiền")
public class PaymentTransactionController {

    private final PaymentService paymentService;

    @Operation(summary = "Tra cứu giao dịch có phân trang",
            description = "Lọc theo khoảng ngày, hình thức, trạng thái, loại, học sinh, lớp, mã khoản phí, số phiếu thu. "
                    + "Mặc định mới nhất trước.")
    @GetMapping("/search")
    @RequirePermission(Permissions.PAYMENT_HISTORY_VIEW)
    public ApiResponse<PageResponse<PaymentTransactionDto>> search(
            @Valid @ModelAttribute PaymentTransactionFilterRequest filter) {
        return ApiResponse.success(paymentService.search(filter));
    }

    @Operation(summary = "Chi tiết giao dịch", description = "Kèm tóm tắt khoản học phí và các lần hoàn tiền.")
    @GetMapping("/{id}")
    @RequirePermission(Permissions.PAYMENT_HISTORY_VIEW)
    public ApiResponse<PaymentTransactionDetailResponse> getDetail(@PathVariable("id") Long id) {
        return ApiResponse.success(paymentService.getDetail(id));
    }

    @Operation(summary = "HTML phiếu thu / phiếu chi (in ấn)", description = "Trả HTML, không bọc ApiResponse.")
    @GetMapping(value = "/{id}/receipt/html", produces = MediaType.TEXT_HTML_VALUE)
    @RequirePermission(Permissions.PAYMENT_HISTORY_VIEW)
    public ResponseEntity<String> getReceiptHtml(@PathVariable("id") Long id) {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(paymentService.renderReceiptHtml(id));
    }

    @Operation(summary = "Hủy giao dịch thu",
            description = "Chỉ giao dịch PAYMENT đang SUCCESS, chưa có hoàn tiền. Tính lại số đã thu của khoản học phí.")
    @PostMapping("/{id}/void")
    @RequirePermission(Permissions.PAYMENT_HISTORY_VOID)
    public ApiResponse<PaymentTransactionDto> voidTransaction(
            @PathVariable("id") Long id,
            @Valid @RequestBody VoidTransactionRequest request) {
        return ApiResponse.success("Hủy giao dịch thành công.", paymentService.voidTransaction(id, request));
    }

    @Operation(summary = "Hoàn tiền giao dịch thu",
            description = "Tạo dòng REFUND (số dương). Hoàn đủ thì giao dịch gốc chuyển REFUNDED.")
    @PostMapping("/{id}/refund")
    @RequirePermission(Permissions.PAYMENT_HISTORY_REFUND)
    public ApiResponse<PaymentTransactionDto> refundTransaction(
            @PathVariable("id") Long id,
            @Valid @RequestBody RefundTransactionRequest request) {
        return ApiResponse.success("Hoàn tiền thành công.", paymentService.refundTransaction(id, request));
    }
}
