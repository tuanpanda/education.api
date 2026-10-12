package com.education.base.dto.request;

import com.education.base.common.file.FileConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Xoá mềm một file ({@code POST /api/v1/admin/files/{id}/delete}). Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileDeleteRequest {

    @NotBlank(message = "Vui lòng nhập lý do xoá")
    @Size(max = FileConstants.MAX_REASON_LENGTH, message = "Lý do không được vượt quá 500 ký tự")
    private String reason;
}
