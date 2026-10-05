package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng {@code EDU_ANNOUNCEMENT_READS} (V18_1) - đánh dấu đã đọc theo user.
 */
@Entity
@Table(name = "EDU_ANNOUNCEMENT_READS")
@IdClass(AnnouncementReadId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementReadEntity {

    @Id
    @Column(name = "ANNOUNCEMENT_ID", nullable = false)
    private Long announcementId;

    @Id
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "READ_AT", nullable = false)
    private LocalDateTime readAt;

    @PrePersist
    void onCreate() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }
}
