package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Chi tiết một khoản học phí kèm toàn bộ lịch sử giao dịch.
 * <p>
 * Map trực tiếp từ hai Ref Cursor của {@code PRC_GET_TUITION_FEE_DETAIL}:
 * {@code O_FEE_CURSOR} cho phần thông tin chung và {@code O_TRANSACTION_CURSOR}
 * cho danh sách {@link #transactions}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TuitionFeeDetailResponse {

    private Long id;
    private String feeCode;

    private Long studentId;
    private String studentCode;
    private String studentName;

    private Long classId;
    private String classCode;
    private String className;

    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;

    private LocalDate dueDate;
    private String status;
    private String note;

    /** Kỳ thu theo khoảng ngày ({@code null} với phiếu cũ chưa backfill / khoản tạo tay). */
    private java.time.LocalDate periodFrom;

    private java.time.LocalDate periodTo;

    /** {@code "Từ dd/MM/yyyy đến dd/MM/yyyy"} hoặc {@code "Tháng MM/yyyy"} (phiếu cũ). */
    private String periodText;

    /**
     * {@code true} khi phiếu tạo bởi "thu theo khoảng ngày" (DB {@code FEE_MONTH IS NULL AND PERIOD_FROM IS NOT NULL});
     * phiếu tháng cũ (kể cả đã backfill PERIOD_*) = {@code false}. FE dùng để ẩn dòng giảm giá.
     */
    private boolean rangeFee;

    /** {@code true}: phiếu gói chưa đủ 10 buổi ({@code IS_PARTIAL = 1}, contract v1.4 §13.2). */
    private boolean partial;

    /** Lý do thu chưa đủ gói ({@code null} nếu không phải phiếu partial). */
    private String partialReason;

    /** Lý do hủy ({@code STATUS = CANCELLED}); lấy từ {@code FIN_TUITION_FEES.CANCEL_REASON} (V14_1). */
    private String cancelReason;

    /** Trạng thái học sinh ({@code EDU_STUDENTS.STATUS}) để giao diện gắn nhãn "Đã nghỉ". */
    private String studentStatus;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Lịch sử giao dịch, sắp xếp mới nhất trước. */
    @Builder.Default
    private List<PaymentTransactionDto> transactions = new ArrayList<>();

    /** Biên lai, ủy nhiệm chi đính kèm - module {@code TUITION}. */
    @Builder.Default
    private List<FileResponseDto> attachments = new ArrayList<>();
}
