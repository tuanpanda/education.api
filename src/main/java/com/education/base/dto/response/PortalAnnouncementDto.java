package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** DTO thông báo trên cổng học sinh. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalAnnouncementDto {

    private Long id;
    private String title;
    private String content;
    private String scopeType;
    private Long classId;
    private String className;
    private boolean pinned;
    private LocalDateTime publishedAt;
    private LocalDateTime expiresAt;
    private boolean read;
}
