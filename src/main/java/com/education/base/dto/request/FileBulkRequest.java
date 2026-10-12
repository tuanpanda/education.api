package com.education.base.dto.request;

import com.education.base.common.file.FileConstants;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Thao tác hàng loạt ({@code POST /api/v1/admin/files/bulk-delete|bulk-restore}), tối đa 100 ID.
 * {@code reason} bắt buộc với bulk-delete, tuỳ chọn với bulk-restore. Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileBulkRequest {

    @NotEmpty(message = "Vui lòng chọn ít nhất một file")
    @Size(max = FileConstants.MAX_BULK_IDS, message = "Tối đa 100 file mỗi lần")
    private List<@NotNull Long> ids;

    @Size(max = FileConstants.MAX_REASON_LENGTH, message = "Lý do không được vượt quá 500 ký tự")
    private String reason;
}
