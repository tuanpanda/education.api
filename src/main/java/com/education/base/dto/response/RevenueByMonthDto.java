package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Doanh thu thực thu của một tháng, dùng vẽ biểu đồ trên Bảng điều khiển.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevenueByMonthDto {

    /** Tháng theo định dạng {@code yyyy-MM}, do Procedure trả về dạng chuỗi đã gom nhóm. */
    private String revenueMonth;

    private BigDecimal collectedAmount;

    private Integer transactionCount;
}
