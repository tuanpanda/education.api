package com.education.base.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Bài học đầy đủ phía nhân viên ({@code GET /api/v1/lms/lessons/{id}}).
 * Hợp đồng Luồng 0 GĐ3 (docs/portal-p3-api.md). Thêm trường được (ghi §9, Bot 2 cập nhật type TS); không đổi/bỏ trường.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonDto {

    private Long id;

    private Long sectionId;

    private String sectionTitle;

    private String ownerType;

    private Long ownerId;

    private Long courseId;

    private Long classId;

    private Long sessionId;

    private LocalDate sessionDate;

    private String title;

    private String content;

    private Integer sortOrder;

    private String status;

    private LocalDateTime availableFrom;

    private LocalDateTime effectiveAvailableFrom;

    private Boolean availableNow;

    private Integer estimatedMinutes;

    private LocalDateTime publishedAt;

    private Long sourceLessonId;

    private List<LessonResourceDto> resources;

    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String createdBy;

    private String updatedBy;

    private Boolean canManage;

    private LocalDateTime serverNow;
}
