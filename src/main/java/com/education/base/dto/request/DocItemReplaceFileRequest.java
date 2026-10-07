package com.education.base.dto.request;

import com.education.base.common.document.DocumentConstants;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thay file (phiên bản mới): {@code POST /api/v1/documents/items/{id}/file} multipart gồm part {@code file} +
 * {@code versionNote}. File cũ chuyển vào thùng rác (module LIBRARY, giữ {@code trash-days}). Hợp đồng Luồng 0 -
 * chỉ Luồng D sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocItemReplaceFileRequest {

    @Size(max = DocumentConstants.VERSION_NOTE_MAX, message = "Ghi chú phiên bản không được vượt quá 500 ký tự")
    private String versionNote;
}
