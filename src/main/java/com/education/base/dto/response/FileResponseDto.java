package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO trả về thông tin file kèm link để xem/tải trực tiếp qua API.
 * JSON giữ tương thích ngược: chỉ THÊM field (xem {@code docs/files-api.md}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileResponseDto {

    private Long id;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private String moduleName;
    private Long referenceId;

    /**
     * Đường dẫn API để xem trực tiếp file trên trình duyệt (inline).
     */
    private String viewUrl;

    /**
     * Đường dẫn API để tải file về máy (attachment).
     */
    private String downloadUrl;

    private LocalDateTime createdAt;

    // ---- Bổ sung V19 (hợp đồng Luồng 0, chỉ Luồng A sửa) - NULL với dữ liệu cũ / procedure cũ ----------

    /** Đuôi file viết thường, không dấu chấm. */
    private String fileExt;

    /** SHA-256 đầy đủ (64 hex) hoặc NULL nếu chưa tính; frontend tự rút gọn khi hiển thị. */
    private String checksumSha256;

    /** Tên đăng nhập người upload (NULL với dữ liệu cũ). */
    private String uploadedBy;

    /** Người dùng hiện tại được xoá mềm file này qua {@code DELETE /api/v1/files/{id}} (NULL = không tính). */
    private Boolean canDelete;
}
