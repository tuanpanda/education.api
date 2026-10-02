package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả sinh hàng loạt phiếu học phí theo lớp / tháng.
 * <p>
 * Mỗi học sinh được ghi trong một transaction riêng: một khoản phí lỗi không làm rollback cả lớp.
 * Khoản phí không thể cập nhật an toàn được giữ nguyên và trả về trong {@link #conflicts};
 * lỗi bất ngờ của từng học sinh nằm trong {@link #errors}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateMonthlyInvoicesResponseDto {

    /** Lý do xung đột: tổng mới (sau giảm) nhỏ hơn số đã thu - khoản phí giữ nguyên. */
    public static final String CONFLICT_NET_BELOW_PAID = "NET_BELOW_PAID";

    /** Lý do xung đột: không còn buổi tính phí nhưng khoản phí đã thu tiền - khoản phí giữ nguyên. */
    public static final String CONFLICT_NO_SESSIONS_HAS_PAYMENTS = "NO_SESSIONS_HAS_PAYMENTS";

    private int createdCount;
    private int updatedCount;
    private int skippedNoAttendance;

    /** Khoản phí đã có của tháng bị hệ thống tự hủy vì không còn buổi tính phí (chưa thu đồng nào). */
    private int cancelledCount;

    @Builder.Default
    private List<TuitionSlipResponseDto> slips = new ArrayList<>();

    @Builder.Default
    private List<FeeConflict> conflicts = new ArrayList<>();

    @Builder.Default
    private List<StudentError> errors = new ArrayList<>();

    /** Khoản phí đã có không được cập nhật vì xung đột với số tiền đã thu. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeeConflict {
        private Long feeId;
        private String feeCode;
        private Long studentId;
        private String studentCode;
        private String studentName;
        /** {@link #CONFLICT_NET_BELOW_PAID} hoặc {@link #CONFLICT_NO_SESSIONS_HAS_PAYMENTS}. */
        private String reason;
        private String message;
        /** Số buổi tính phí và tổng tiền / tiền giảm tính lại (chưa ghi vào khoản phí). */
        private Integer billableSessions;
        private BigDecimal newTotalAmount;
        private BigDecimal newDiscountAmount;
        private BigDecimal paidAmount;
    }

    /** Lỗi khi sinh phiếu cho một học sinh (các học sinh khác vẫn được ghi). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentError {
        private Long studentId;
        private String studentCode;
        private String studentName;
        private String code;
        private String message;
    }
}
