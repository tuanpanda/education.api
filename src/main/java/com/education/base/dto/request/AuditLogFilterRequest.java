package com.education.base.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Điều kiện tra cứu nhật ký hệ thống ({@code SYS_AUDIT_LOGS}), có phân trang ({@code page} bắt đầu từ 1).
 * Mặc định mới nhất trước ({@code EVENT_TIME} giảm dần).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogFilterRequest {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    static final String CODE_PATTERN = "[A-Za-z0-9_]{1,50}";

    /** Từ ngày (bao gồm). */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    /** Đến ngày (bao gồm cả ngày này). */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    private Long userId;

    /** Tìm gần đúng (không phân biệt hoa thường) theo tên đăng nhập. */
    @Size(max = 100, message = "Tên đăng nhập không được vượt quá 100 ký tự")
    private String username;

    @Pattern(regexp = CODE_PATTERN, message = "Loại tài khoản không hợp lệ")
    private String userType;

    /** Mã hành động chính xác, ví dụ {@code LOGIN_FAILED}. */
    @Pattern(regexp = CODE_PATTERN, message = "Mã hành động không hợp lệ")
    private String action;

    @Pattern(regexp = CODE_PATTERN, message = "Loại đối tượng không hợp lệ")
    private String resourceType;

    @Size(max = 100, message = "Mã đối tượng không được vượt quá 100 ký tự")
    private String resourceId;

    @Pattern(regexp = "SUCCESS|FAILURE|DENIED", message = "Kết quả chỉ nhận: SUCCESS, FAILURE, DENIED")
    private String result;

    /** Tìm theo tiền tố IP (ví dụ {@code 192.168.1.}). */
    @Size(max = 64, message = "IP không được vượt quá 64 ký tự")
    private String ip;

    /** Tìm gần đúng trong chi tiết (DETAIL). */
    @Size(max = 100, message = "Từ khóa tìm kiếm không được vượt quá 100 ký tự")
    private String keyword;

    @Min(value = 1, message = "Số trang phải lớn hơn hoặc bằng 1")
    @Builder.Default
    private Integer page = DEFAULT_PAGE;

    @Min(value = 1, message = "Số dòng mỗi trang phải lớn hơn hoặc bằng 1")
    @Max(value = MAX_SIZE, message = "Số dòng mỗi trang không được vượt quá 200")
    @Builder.Default
    private Integer size = DEFAULT_SIZE;

    @Schema(hidden = true)
    @AssertTrue(message = "Từ ngày phải nhỏ hơn hoặc bằng đến ngày")
    public boolean isDateRangeValid() {
        return fromDate == null || toDate == null || !fromDate.isAfter(toDate);
    }

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
