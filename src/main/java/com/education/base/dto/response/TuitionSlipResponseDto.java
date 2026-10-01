package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Dữ liệu phiếu học phí điện tử (card in ấn + VietQR).
 * <p>
 * Phần học sinh/lớp/số buổi lấy từ {@code PRC_GET_TUITION_SLIP_DATA};
 * {@link #qrBase64} và {@link #quickPayUrl} do tầng Service sinh qua {@code VietQrHelper}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TuitionSlipResponseDto {

    private Long invoiceId;
    private String invoiceCode;
    private String monthYearText;
    private Integer month;
    private Integer year;
    private String className;
    private String classCode;
    private String studentCode;
    private String studentName;
    private String slipLabel;

    private BigDecimal pricePerSession;
    private Integer totalSessions;
    private BigDecimal totalAmount;

    /** Số tiền giảm (miễn giảm / học bổng) của khoản phí. */
    private BigDecimal discountAmount;

    /** Số tiền đã thu. */
    private BigDecimal paidAmount;

    /**
     * Số tiền còn phải thu ({@code FeeStatusCalculator.remainingOf}), cũng là số tiền trên VietQR.
     * Không có QR khi số này {@code <= 0} hoặc khoản phí {@code PAID} / {@code CANCELLED}.
     */
    private BigDecimal remainingAmount;

    /** {@code UNPAID}, {@code PARTIAL}, {@code PAID}, {@code OVERDUE}, {@code CANCELLED}. */
    private String status;

    private LocalDate dueDate;

    @Builder.Default
    private List<String> attendedDates = new ArrayList<>();

    private String teacherComment;
    private String footerWish;

    private String bankBin;
    private String bankName;
    private String accountNo;
    private String accountName;
    private String qrPayload;
    private String qrBase64;
    private String quickPayUrl;
}
