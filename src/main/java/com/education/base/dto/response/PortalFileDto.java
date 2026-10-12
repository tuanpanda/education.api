package com.education.base.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * File trên cổng học sinh. URL luôn qua {@code /api/v1/portal/me/files/{id}} (kiểm tra sở hữu, 404 nếu không).
 * Hợp đồng Luồng 0 (docs/homework-api.md). Thêm trường được (Bot 1 + Bot 2 cùng cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalFileDto {

    private Long id;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private String fileExt;
    /** {@code /api/v1/portal/me/files/{id}} (inline cho ảnh/PDF). */
    private String viewUrl;
    /** {@code /api/v1/portal/me/files/{id}?download=true}. */
    private String downloadUrl;
    private LocalDateTime createdAt;
    /** Học sinh xoá được (file bài làm của mình khi bài còn sửa được). */
    private Boolean canDelete;
}
