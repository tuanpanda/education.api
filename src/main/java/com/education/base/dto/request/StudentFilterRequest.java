package com.education.base.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Điều kiện tìm kiếm và phân trang danh sách học sinh,
 * truyền vào Standalone Procedure {@code PRC_SEARCH_STUDENTS_PAGING}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentFilterRequest {

    /** Số trang mặc định khi client không truyền. */
    public static final int DEFAULT_PAGE_NO = 1;

    /** Số dòng mỗi trang mặc định khi client không truyền. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * Từ khóa tìm theo mã học sinh, họ tên hoặc email. Để trống là lấy tất cả.
     */
    @Size(max = 100, message = "Từ khóa tìm kiếm không được vượt quá 100 ký tự")
    private String keyword;

    /**
     * Lọc theo trạng thái. Client có thể gửi; repository luôn tìm {@code ACTIVE}.
     */
    @Pattern(regexp = "ACTIVE|INACTIVE|GRADUATED|SUSPENDED",
            message = "Trạng thái chỉ nhận một trong các giá trị: ACTIVE, INACTIVE, GRADUATED, SUSPENDED")
    private String status;

    @Min(value = 1, message = "Số trang phải lớn hơn hoặc bằng 1")
    @Builder.Default
    private Integer pageNo = DEFAULT_PAGE_NO;

    @Min(value = 1, message = "Số dòng mỗi trang phải lớn hơn hoặc bằng 1")
    @Max(value = 200, message = "Số dòng mỗi trang không được vượt quá 200")
    @Builder.Default
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    /**
     * @return số trang đã chuẩn hóa, luôn {@code >= 1}.
     */
    public int resolvePageNo() {
        return (pageNo == null || pageNo < 1) ? DEFAULT_PAGE_NO : pageNo;
    }

    /**
     * @return số dòng mỗi trang đã chuẩn hóa, luôn nằm trong khoảng {@code 1..200}.
     */
    public int resolvePageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, 200);
    }
}
