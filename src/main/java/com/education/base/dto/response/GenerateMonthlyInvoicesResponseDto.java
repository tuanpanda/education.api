package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả sinh hàng loạt phiếu học phí theo lớp / tháng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateMonthlyInvoicesResponseDto {

    private int createdCount;
    private int updatedCount;
    private int skippedNoAttendance;

    @Builder.Default
    private List<TuitionSlipResponseDto> slips = new ArrayList<>();
}
