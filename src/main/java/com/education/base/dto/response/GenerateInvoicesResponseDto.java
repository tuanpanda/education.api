package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả {@code POST /api/v1/tuition-fees/generate-range} (contract fee-range-api v1.3 mục 2).
 * <p>
 * Tất cả hoặc không: một học sinh lỗi (400) hoặc trùng buổi (409) thì không tạo phiếu nào. {@code feeIds.size() == createdCount}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateInvoicesResponseDto {

    private int createdCount;

    /** Học sinh ĐANG HỌC không có buổi tính phí trong khoảng (không tạo phiếu). */
    private int skippedNoAttendance;

    /** ID mọi phiếu vừa tạo, không phân trang. */
    @Builder.Default
    private List<Long> feeIds = new ArrayList<>();

    /** Chi tiết từng phiếu vừa tạo (cùng thứ tự {@link #feeIds}), contract v1.4 §13.2. */
    @Builder.Default
    private List<CreatedSlip> slips = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatedSlip {
        private Long feeId;
        private String feeCode;
        private Long studentId;
        private String studentCode;
        private int sessionCount;
        /** Ngày buổi đầu / buổi cuối thực thu (không phải from/to của request). */
        private java.time.LocalDate periodFrom;
        private java.time.LocalDate periodTo;
        private java.math.BigDecimal totalAmount;
        /** {@code true}: phiếu chưa đủ 10 buổi ({@code IS_PARTIAL = 1}). */
        private boolean partial;
        private String partialReason;
    }
}
