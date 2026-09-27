package com.education.base.controller;

import com.education.base.common.ApiResponse;
import com.education.base.common.VietQrHelper;
import com.education.base.dto.request.GenerateQrRequest;
import com.education.base.dto.response.QrPaymentResponseDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module Sinh mã VietQR Thanh toán Học phí.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class QrPaymentController {

    private static final int QR_WIDTH = 512;
    private static final int QR_HEIGHT = 512;

    /**
     * Sinh mã VietQR, trả về đồng thời link nhanh, payload TLV và ảnh Base64.
     */
    @PostMapping("/generate-qr")
    public ApiResponse<QrPaymentResponseDto> generateQr(@Valid @RequestBody GenerateQrRequest request) {
        String qrPayload = VietQrHelper.buildVietQrPayload(
                request.getBankBin(),
                request.getAccountNo(),
                request.getAmount(),
                request.getDescription());

        QrPaymentResponseDto data = QrPaymentResponseDto.builder()
                .quickUrl(VietQrHelper.buildQuickUrl(
                        request.getBankBin(),
                        request.getAccountNo(),
                        request.getAmount(),
                        request.getDescription(),
                        request.getAccountName()))
                .qrPayload(qrPayload)
                .base64Image(VietQrHelper.generateQrBase64(qrPayload, QR_WIDTH, QR_HEIGHT))
                .build();

        return ApiResponse.success("Tạo mã QR thành công.", data);
    }
}
