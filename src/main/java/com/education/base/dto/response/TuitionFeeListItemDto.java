package com.education.base.dto.response;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Một dòng trong danh sách khoản học phí ({@code GET /api/v1/tuition-fees/search}): mọi trường của
 * {@link TuitionFeeReportDto} cộng thêm trạng thái học sinh để giao diện gắn nhãn "Đã nghỉ" (B8).
 * <p>
 * Kế thừa thay vì sửa {@code TuitionFeeReportDto} (DTO đó dùng chung với báo cáo của Stream C).
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class TuitionFeeListItemDto extends TuitionFeeReportDto {

    /** {@code EDU_STUDENTS.STATUS}: {@code ACTIVE}, {@code INACTIVE}, {@code GRADUATED}, {@code SUSPENDED}. */
    private String studentStatus;

    /** Kỳ thu của phiếu tháng ({@code null} với khoản phí tạo tay). */
    private Integer feeMonth;

    private Integer feeYear;
}
