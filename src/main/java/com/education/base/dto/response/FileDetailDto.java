package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Chi tiết file trên màn quản trị ({@code GET /api/v1/admin/files/{id}}). Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileDetailDto {

    private FileAdminDto file;

    /** Storage key tương đối ({@code FILE_PATH}) - chỉ admin xem, không bao giờ là đường dẫn tuyệt đối. */
    private String storageKey;

    /** Tên vật lý ({@code STORED_NAME}). */
    private String storedName;

    /** Blob còn tồn tại trên kho lưu trữ tại thời điểm xem (NULL nếu không kiểm tra được). */
    private Boolean blobExists;

    private String scanDetail;
    private LocalDateTime scannedAt;

    /** 20 sự kiện nhật ký gần nhất ({@code RESOURCE_TYPE=FILE, RESOURCE_ID=id}), mới nhất trước. */
    @Builder.Default
    private List<AuditLogResponseDto> auditTrail = List.of();
}
