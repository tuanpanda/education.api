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

    /** Nội dung chuyển khoản đúng như addInfo trong QR (tag 62/08) và quick link; {@code null} khi không có QR / không có nội dung. */
    private String transferContent;

    private String base64Image;
}
