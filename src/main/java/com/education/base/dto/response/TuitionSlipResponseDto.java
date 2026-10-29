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

    /** Kỳ thu theo khoảng ngày; {@code null} với phiếu cũ chưa backfill (V23_8). */
    private LocalDate periodFrom;
    private LocalDate periodTo;

    /** {@code "Từ dd/MM/yyyy đến dd/MM/yyyy"} hoặc {@code "Tháng MM/yyyy"} (phiếu cũ). */
    private String periodText;

    /**
     * {@code true} khi phiếu tạo bởi "thu theo khoảng ngày" (DB {@code FEE_MONTH IS NULL AND PERIOD_FROM IS NOT NULL});
     * phiếu tháng cũ (kể cả đã backfill PERIOD_*) = {@code false}. FE dùng để ẩn dòng giảm giá.
     */
    private boolean rangeFee;
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

    /**
     * Ngày trên phiếu dạng {@code dd/MM}, tăng dần, không đánh dấu muộn. Phiếu khoảng ngày đọc từ
     * {@code FIN_TUITION_FEE_SESSIONS}; phiếu đã hủy không có ngày.
     */
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
