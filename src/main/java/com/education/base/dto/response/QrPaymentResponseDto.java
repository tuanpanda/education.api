package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả sinh mã VietQR thanh toán học phí.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QrPaymentResponseDto {

    /**
     * Link nhanh tới ảnh QR của dịch vụ {@code img.vietqr.io}.
     */
    private String quickUrl;

    /**
     * Chuỗi payload TLV chuẩn EMVCo (đã gồm CRC16), dùng để đối soát hoặc tự render QR ở client.
     */
    private String qrPayload;

    /**
     * Ảnh QR dạng Base64 PNG data URL, tự sinh bằng ZXing nên không phụ thuộc dịch vụ ngoài.
     */
    private String base64Image;
}
