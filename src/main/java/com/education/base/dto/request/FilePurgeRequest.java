package com.education.base.dto.request;

import com.education.base.common.file.FileConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Xoá vĩnh viễn ({@code DELETE /api/v1/admin/files/{id}/purge}, body JSON): chỉ file DELETED; {@code confirmName}
 * phải trùng {@code ORIGINAL_NAME}. Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilePurgeRequest {

    @NotBlank(message = "Vui lòng nhập lý do")
    @Size(max = FileConstants.MAX_REASON_LENGTH)
    private String reason;

    @NotBlank(message = "Vui lòng nhập lại tên file để xác nhận")
    @Size(max = 255)
    private String confirmName;
}
