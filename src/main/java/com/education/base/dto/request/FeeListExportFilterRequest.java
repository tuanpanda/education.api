package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Bộ lọc xuất Excel danh sách khoản học phí ({@code GET /api/v1/reports/export/tuition-fees}).
 * <p>
 * Cùng tên tham số với tra cứu {@code GET /api/v1/tuition-fees/search} (không có phân trang). Khác với màn hình
 * danh sách, file xuất gồm cả khoản phí của học sinh không còn {@code ACTIVE} (báo cáo công nợ đầy đủ, B8).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeListExportFilterRequest {

    /** Tìm theo mã khoản phí, mã học sinh hoặc họ tên học sinh. */
    @Size(max = 100, message = "Từ khóa tìm kiếm không được vượt quá 100 ký tự")
    private String keyword;

    @Pattern(regexp = DomainConstants.FEE_STATUS_PATTERN,
            message = "Trạng thái chỉ nhận: UNPAID, PARTIAL, PAID, OVERDUE, CANCELLED")
    private String status;

    private Long studentId;

    private Long classId;

    /** Lọc theo khoảng hạn thu. */
    private LocalDate dueFromDate;

    private LocalDate dueToDate;

    /** Chỉ lấy các khoản đã quá hạn mà chưa thu đủ. */
    @Builder.Default
    private Boolean overdueOnly = Boolean.FALSE;
}
