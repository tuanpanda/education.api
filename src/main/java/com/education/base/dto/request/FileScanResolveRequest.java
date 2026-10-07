package com.education.base.dto.request;

import com.education.base.common.file.FileConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Xử lý một mục đối soát ({@code POST /api/v1/admin/files/maintenance/scans/{runId}/items/{itemId}/resolve}).
 * {@code DELETE_BLOB} cần thêm {@code MENU_FILE_MANAGER:PURGE}. Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileScanResolveRequest {

    @NotBlank
    @Pattern(regexp = FileConstants.Scan.ACTION_PATTERN,
            message = "Hành động chỉ nhận: MARK_MISSING, QUARANTINE, DELETE_BLOB, IGNORE")
    private String action;

    @Size(max = FileConstants.MAX_REASON_LENGTH)
    private String note;
}
