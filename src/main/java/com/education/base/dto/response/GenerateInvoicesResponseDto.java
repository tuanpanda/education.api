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
 * Tất cả hoặc không: có xung đột thì 409 và không tạo phiếu nào. {@code feeIds.size() == createdCount}.
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
}
