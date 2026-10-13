package com.education.base.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tài nguyên trên cổng. FILE: {@code file.viewUrl/downloadUrl} qua /api/v1/portal/me/files/{id}. VIDEO: chỉ dùng embedUrl
 * cho iframe. ASSIGNMENT: link sang /portal/homework/{assignmentId} (chỉ khi bài tập PUBLISHED/CLOSED; khác thì bỏ khỏi danh sách).
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalLessonResourceDto {

    private Long id;

    private String resourceType;

    private String title;

    private PortalFileDto file;

    /** LINK: URL ngoài (mở tab mới, rel=noopener). VIDEO: URL gốc (nút "Mở trên YouTube/Drive"). */
    private String url;

    private String embedUrl;

    private String provider;

    private Long assignmentId;

    private String assignmentTitle;

    private LocalDateTime assignmentDueAt;
}
