package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Một dòng file trên màn quản trị ({@code GET /api/v1/admin/files/search}). Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileAdminDto {

    private Long id;
    private String originalName;
    private String fileExt;
    private String contentType;
    private Long fileSize;
    private String moduleName;
    private Long referenceId;

    /** "Nơi sử dụng" (nhãn + route + trạng thái bản ghi). */
    private FileUsageDto usage;

    /** ACTIVE / PENDING / DELETED / PURGED. */
    private String status;

    /** SKIPPED / PENDING / CLEAN / INFECTED / ERROR. */
    private String scanStatus;

    private Long uploadedByUserId;
    private String uploadedByUsername;
    private String uploadedByFullName;
    /** STAFF / STUDENT / SYSTEM; NULL với dữ liệu cũ. */
    private String uploadedByType;

    private Long ownerStudentId;
    private String ownerStudentCode;
    private String ownerStudentName;

    /** SHA-256 đầy đủ (64 hex) - chỉ màn quản trị; NULL = chưa tính. */
    private String checksumSha256;
    private String storageBackend;

    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
    private String deletedBy;
    private String deleteReason;
    private LocalDateTime purgeAfter;
    private LocalDateTime purgedAt;

    /** Số ngày còn lại trước khi job xoá vĩnh viễn (chỉ DELETED/PENDING; làm tròn lên, tối thiểu 0). */
    private Integer daysUntilPurge;

    /** URL xem / tải qua API quản trị ({@code /api/v1/admin/files/{id}/view|download}). */
    private String viewUrl;
    private String downloadUrl;
}
