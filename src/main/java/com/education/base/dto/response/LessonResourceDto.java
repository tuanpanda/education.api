package com.education.base.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tài nguyên bài học phía nhân viên.
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonResourceDto {

    private Long id;

    private Long lessonId;

    private String resourceType;

    private String title;

    private Integer sortOrder;

    /** FILE: metadata file (URL qua /api/v1/files/{id}). */
    private FileResponseDto file;

    private String url;

    /** VIDEO: URL nhúng do server sinh. */
    private String embedUrl;

    private String provider;

    private Long assignmentId;

    private String assignmentTitle;

    private String assignmentStatus;

    private LocalDateTime assignmentDueAt;
}
