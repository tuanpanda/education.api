package com.education.base.controller;

import com.education.base.security.Permissions;
import com.education.base.security.RequirePermission;
import com.education.base.common.ApiResponse;
import com.education.base.common.VietQrHelper;
import com.education.base.dto.request.GenerateQrRequest;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.dto.response.QrPaymentResponseDto;
import com.education.base.service.BankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sinh mã VietQR thanh toán học phí từ số tài khoản đang sử dụng trong DB.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class QrPaymentController {

    private static final int QR_WIDTH = 512;
    private static final int QR_HEIGHT = 512;

    private final BankAccountService bankAccountService;

    @PostMapping("/generate-qr")
    @RequirePermission(Permissions.TUITION_PAYMENT_GEN_QR)
    public ApiResponse<QrPaymentResponseDto> generateQr(@Valid @RequestBody GenerateQrRequest request) {
        BankAccountResponseDto account = bankAccountService.requireActive();
        GenerateQrRequest params = request == null ? new GenerateQrRequest() : request;

        String qrPayload = VietQrHelper.buildVietQrPayload(
                account.getBankBin(),
                account.getAccountNo(),
                params.getAmount(),
                params.getDescription());

        QrPaymentResponseDto data = QrPaymentResponseDto.builder()
                .quickUrl(VietQrHelper.buildQuickUrl(
                        account.getBankBin(),
                        account.getAccountNo(),
                        params.getAmount(),
                        params.getDescription(),
                        firstNonBlank(params.getAccountName(), account.getAccountName())))
                .qrPayload(qrPayload)
                .base64Image(VietQrHelper.generateQrBase64(qrPayload, QR_WIDTH, QR_HEIGHT))
                .build();

        return ApiResponse.success("Tạo mã QR thành công.", data);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback;
    }
}
