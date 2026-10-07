package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Một mục phát hiện trong đợt đối soát ({@code SYS_FILE_SCAN_ITEMS}). Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileScanItemDto {

    private Long id;
    private Long runId;
    /** MISSING_BLOB / ORPHAN_BLOB / SIZE_MISMATCH / CHECKSUM_MISMATCH / REF_DELETED / REF_NOT_FOUND. */
    private String itemType;
    /** NULL với ORPHAN_BLOB. */
    private Long fileId;
    private String originalName;
    private String moduleName;
    private Long referenceId;
    private String storageKey;
    /** Kích thước trong DB / trên kho (byte). */
    private Long dbSize;
    private Long blobSize;
    private LocalDateTime blobModifiedAt;
    /** NULL = chưa xử lý; MARKED_MISSING / QUARANTINED / BLOB_DELETED / IGNORED. */
    private String resolution;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
}
