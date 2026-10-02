package com.education.base.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Điều kiện lọc cho các báo cáo tài chính ({@code /api/v1/reports/finance/**}).
 * <ul>
 *     <li>{@code fromDate} / {@code toDate}: tổng hợp tài chính ({@code PRC_RPT_FINANCE_SUMMARY}); bỏ trống thì
 *     Procedure lấy 12 tháng gần nhất, {@code fromDate > toDate} trả lỗi {@code INVALID_DATE_RANGE}.</li>
 *     <li>{@code asOfDate}: ngày chốt tuổi nợ ({@code PRC_RPT_DEBT_AGING}); bỏ trống là hôm nay.</li>
 *     <li>{@code year} / {@code month}: kỳ thu tiền theo lớp ({@code PRC_RPT_CLASS_COLLECTION}); bỏ trống
 *     {@code year} là năm hiện tại, bỏ trống {@code month} là cả năm.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinanceReportFilterRequest {

    private LocalDate fromDate;

    private LocalDate toDate;

    private LocalDate asOfDate;

    @Min(value = 2000, message = "Năm phải từ 2000 đến 2100")
    @Max(value = 2100, message = "Năm phải từ 2000 đến 2100")
    private Integer year;

    @Min(value = 1, message = "Tháng phải từ 1 đến 12")
    @Max(value = 12, message = "Tháng phải từ 1 đến 12")
    private Integer month;
}
