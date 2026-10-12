package com.education.base.dto.request;

import com.education.base.common.file.FileConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Điều kiện tìm file trên màn quản trị ({@code GET /api/v1/admin/files/search}, query string).
 * Phân trang 1-based {@code pageNo}/{@code pageSize} như {@link com.education.base.dto.response.PageResponse}.
 * Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileFilterRequest {

    /** Giá trị `status` để lấy mọi trạng thái. */
    public static final String STATUS_ALL = "ALL";

    public static final String SORT_PATTERN = "createdAt|fileSize|originalName|deletedAt";

    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,49}", message = "Mã module không hợp lệ")
    private String module;

    private Long referenceId;

    private Long uploadedByUserId;

    private Long ownerStudentId;

    /** Đuôi file (không dấu chấm), không phân biệt hoa thường. */
    @Pattern(regexp = "[A-Za-z0-9]{1,10}", message = "Đuôi file không hợp lệ")
    private String ext;

    /** Tiền tố MIME, ví dụ {@code image/} hoặc {@code application/pdf}. */
    @Size(max = 100)
    private String contentType;

    /** Mặc định ACTIVE. Thùng rác: DELETED. */
    @Pattern(regexp = FileConstants.Status.PATTERN + "|" + STATUS_ALL, message = "Trạng thái không hợp lệ")
    @Builder.Default
    private String status = FileConstants.Status.ACTIVE;

    @Pattern(regexp = FileConstants.ScanStatus.PATTERN, message = "Trạng thái quét không hợp lệ")
    private String scanStatus;

    /** Trạng thái bản ghi tham chiếu: ACTIVE / DELETED / NOT_FOUND / NONE. */
    @Pattern(regexp = FileConstants.RefState.PATTERN, message = "Trạng thái bản ghi không hợp lệ")
    private String refState;

    /** Từ ngày tạo (bao gồm). */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate createdFrom;

    /** Đến ngày tạo (bao gồm cả ngày này). */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate createdTo;

    @PositiveOrZero
    private Long minSize;

    @PositiveOrZero
    private Long maxSize;

    /** Tìm gần đúng theo tên gốc (không phân biệt hoa thường). */
    @Size(max = 100, message = "Từ khóa không được vượt quá 100 ký tự")
    private String q;

    @Min(value = 1, message = "Số trang phải lớn hơn hoặc bằng 1")
    @Builder.Default
    private int pageNo = 1;

    @Min(1)
    @Max(FileConstants.MAX_PAGE_SIZE)
    @Builder.Default
    private int pageSize = FileConstants.DEFAULT_PAGE_SIZE;

    @Pattern(regexp = SORT_PATTERN, message = "Trường sắp xếp không hợp lệ")
    @Builder.Default
    private String sortBy = "createdAt";

    @Pattern(regexp = "(?i)asc|desc", message = "Chiều sắp xếp chỉ nhận asc/desc")
    @Builder.Default
    private String sortDir = "desc";

    @Schema(hidden = true)
    @AssertTrue(message = "Từ ngày phải nhỏ hơn hoặc bằng đến ngày")
    public boolean isDateRangeValid() {
        return createdFrom == null || createdTo == null || !createdFrom.isAfter(createdTo);
    }

    @Schema(hidden = true)
    @AssertTrue(message = "Dung lượng tối thiểu phải nhỏ hơn hoặc bằng tối đa")
    public boolean isSizeRangeValid() {
        return minSize == null || maxSize == null || minSize <= maxSize;
    }
}
