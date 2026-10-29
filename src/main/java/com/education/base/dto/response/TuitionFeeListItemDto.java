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

    /** Kỳ thu của phiếu tháng; phiếu khoảng ngày: tháng của {@code periodFrom} (chỉ hiển thị, DB NULL). */
    private Integer feeMonth;

    private Integer feeYear;

    /** Kỳ thu theo khoảng ngày ({@code null} với phiếu cũ chưa backfill / khoản tạo tay). */
    private java.time.LocalDate periodFrom;

    private java.time.LocalDate periodTo;

    /** {@code "Từ dd/MM/yyyy đến dd/MM/yyyy"} / {@code "Tháng MM/yyyy"} (phiếu cũ); FE hiển thị trường này. */
    private String periodText;

    /**
     * {@code true} khi phiếu tạo bởi "thu theo khoảng ngày" (DB {@code FEE_MONTH IS NULL AND PERIOD_FROM IS NOT NULL});
     * phiếu tháng cũ (kể cả đã backfill PERIOD_*) = {@code false}. FE dùng để ẩn dòng giảm giá.
     */
    private boolean rangeFee;
}
