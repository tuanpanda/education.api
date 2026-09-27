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
 * Điều kiện tìm kiếm nguồn tuyển sinh, có phân trang.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadFilterRequest {

    public static final int DEFAULT_PAGE_NO = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 200;

    /** Tìm theo mã lead, họ tên, số điện thoại hoặc email. */
    @Size(max = 100, message = "Từ khóa tìm kiếm không được vượt quá 100 ký tự")
    private String keyword;

    @Pattern(regexp = DomainConstants.LEAD_STATUS_PATTERN,
            message = "Trạng thái chỉ nhận: NEW, CONTACTED, QUALIFIED, CONVERTED, LOST")
    private String status;

    @Pattern(regexp = DomainConstants.LEAD_SOURCE_PATTERN,
            message = "Nguồn chỉ nhận: WEBSITE, FACEBOOK, ZALO, REFERRAL, WALK_IN, HOTLINE, OTHER")
    private String source;

    private Long assignedToId;

    /** Lọc theo khoảng ngày tạo lead. */
    private LocalDate fromDate;

    private LocalDate toDate;

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
