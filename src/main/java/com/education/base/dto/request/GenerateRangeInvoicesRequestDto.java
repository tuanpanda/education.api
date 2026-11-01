package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import com.education.base.common.validation.MaxCodePoints;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Sinh phiếu học phí theo gói 10 buổi cho các học sinh được chọn ({@code POST /api/v1/tuition-fees/generate-range},
 * contract fee-range-api v1.4 §13).
 * <p>
 * {@code fromDate <= toDate} và khoảng tối đa {@code DomainConstants.FEE_RANGE_MAX_DAYS} ngày được kiểm ở Service
 * (400 {@code VALIDATION_ERROR}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateRangeInvoicesRequestDto {

    @NotNull(message = "ID lớp học không được để trống")
    private Long classId;

    @NotNull(message = "Từ ngày không được để trống")
    private LocalDate fromDate;

    @NotNull(message = "Đến ngày không được để trống")
    private LocalDate toDate;

    @NotNull(message = "Đơn giá mỗi buổi không được để trống")
    @DecimalMin(value = "0", inclusive = false, message = "Đơn giá mỗi buổi phải lớn hơn 0")
    @Digits(integer = 13, fraction = 2, message = "Đơn giá tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
    private BigDecimal pricePerSession;

    /** Tối đa 1000 ký tự (code point, cột {@code TEACHER_COMMENT VARCHAR2(1000 CHAR)} sau V25_1). */
    @MaxCodePoints(value = DomainConstants.FEE_TEACHER_COMMENT_MAX,
            message = "Nhận xét giáo viên không được vượt quá 1000 ký tự")
    private String teacherComment;

    /** Tối đa 500 ký tự (code point, cột {@code FOOTER_WISH VARCHAR2(500 CHAR)} sau V25_1). */
    @MaxCodePoints(value = DomainConstants.FEE_FOOTER_WISH_MAX, message = "Lời chúc không được vượt quá 500 ký tự")
    private String footerWish;

    /**
     * Học sinh được chọn (bắt buộc, ≥ 1). Rỗng / trùng / thiếu lý do... được kiểm ở Service để trả đủ
     * {@code data.details} (contract §13.6), nên không gắn {@code @NotEmpty} ở đây.
     */
    private List<StudentSelection> students;

    /** Một học sinh trong {@code students}. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentSelection {

        private Long studentId;

        /** {@code true}: cho thu khi chưa đủ 10 buổi (1-9 buổi), bắt buộc {@link #partialReason}. Bỏ qua khi đủ 10. */
        private Boolean allowPartial;

        /** Lý do thu chưa đủ gói, 1-200 ký tự (bắt buộc khi {@code allowPartial} và học sinh < 10 buổi). */
        private String partialReason;
    }
}
