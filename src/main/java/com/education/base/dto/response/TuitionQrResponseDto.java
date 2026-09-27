package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Kết quả sinh VietQR gắn với một khoản học phí cụ thể.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TuitionQrResponseDto {

    private Long tuitionFeeId;

    private String feeCode;

    private String studentCode;

    private String studentName;

    /** Số tiền còn phải thu, dùng làm số tiền trên QR. */
    private BigDecimal remainingAmount;

    private String quickUrl;

    private String qrPayload;

    private String base64Image;
}
