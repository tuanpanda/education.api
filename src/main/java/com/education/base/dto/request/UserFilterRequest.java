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

/**
 * Điều kiện tìm kiếm người dùng, có phân trang ({@code page} bắt đầu từ 1).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFilterRequest {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    /** Tìm theo tên đăng nhập, họ tên, email hoặc số điện thoại. */
    @Size(max = 100, message = "Từ khóa tìm kiếm không được vượt quá 100 ký tự")
    private String keyword;

    @Pattern(regexp = DomainConstants.USER_STATUS_PATTERN,
            message = "Trạng thái chỉ nhận: ACTIVE, INACTIVE, LOCKED")
    private String status;

    private Long roleId;

    @Min(value = 1, message = "Số trang phải lớn hơn hoặc bằng 1")
    @Builder.Default
    private Integer page = DEFAULT_PAGE;

    @Min(value = 1, message = "Số dòng mỗi trang phải lớn hơn hoặc bằng 1")
    @Max(value = MAX_SIZE, message = "Số dòng mỗi trang không được vượt quá 200")
    @Builder.Default
    private Integer size = DEFAULT_SIZE;

    public int resolvePage() {
        return (page == null || page < 1) ? DEFAULT_PAGE : page;
    }

    public int resolveSize() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
