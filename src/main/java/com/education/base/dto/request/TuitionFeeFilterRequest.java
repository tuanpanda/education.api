package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Điều kiện tra cứu khoản học phí, có phân trang.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TuitionFeeFilterRequest {

    public static final int DEFAULT_PAGE_NO = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 200;

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

    @Min(value = 1, message = "Số trang phải lớn hơn hoặc bằng 1")
    @Builder.Default
    private Integer pageNo = DEFAULT_PAGE_NO;

    @Min(value = 1, message = "Số dòng mỗi trang phải lớn hơn hoặc bằng 1")
    @Max(value = MAX_PAGE_SIZE, message = "Số dòng mỗi trang không được vượt quá 200")
    @Builder.Default
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    public int resolvePageNo() {
        return (pageNo == null || pageNo < 1) ? DEFAULT_PAGE_NO : pageNo;
    }

    public int resolvePageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
