package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Thu tiền của một lớp trong kỳ ({@code O_DATA_CURSOR} của {@code PRC_RPT_CLASS_COLLECTION}).
 * <p>
 * Khoản phí không gắn lớp được gom vào một dòng có {@code classId = null}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassCollectionDto {

    private Long classId;

    private String classCode;

    private String className;

    private String classStatus;

    private Long feeCount;

    private Long studentCount;

    /** Tổng TOTAL_AMOUNT các khoản chưa hủy. */
    private BigDecimal billedAmount;

    private BigDecimal discountAmount;

    /** Phải thu sau miễn giảm. */
    private BigDecimal netAmount;

    /** Tổng giao dịch {@code SUCCESS} của các khoản trong kỳ. */
    private BigDecimal collectedAmount;

    /** Còn phải thu của các khoản đang mở. */
    private BigDecimal outstandingAmount;

    /** Tỷ lệ thu {@code collected / net * 100} (2 chữ số thập phân); null khi phải thu bằng 0. */
    private BigDecimal collectionRate;
}
