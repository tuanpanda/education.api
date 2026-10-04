package com.education.base.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Điều kiện tìm kiếm màn hình "Tài khoản học sinh" ({@code GET /api/v1/student-accounts/search}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAccountFilterRequest {

    public static final int DEFAULT_PAGE_NO = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 200;

    /** Tìm theo mã học sinh, họ tên hoặc tên đăng nhập (không phân biệt hoa thường). */
    @Size(max = 100, message = "Từ khóa tìm kiếm không được vượt quá 100 ký tự")
    private String keyword;

    /** Chỉ học sinh đang ghi danh (ENROLLED) vào lớp này. */
    private Long classId;

    /** {@code true} = đã có tài khoản, {@code false} = chưa có; bỏ trống = tất cả. */
    private Boolean hasAccount;

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
