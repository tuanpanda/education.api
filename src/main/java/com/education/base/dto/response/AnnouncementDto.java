package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** DTO thông báo cho API nhân viên. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementDto {

    private Long id;
    private String title;
    private String content;
    private String scopeType;
    private Long classId;
    private String classCode;
    private String className;
    private String audience;
    private boolean pinned;
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    /**
     * {@code true} nếu người dùng hiện tại được sửa/đăng/lưu trữ/xóa thông báo này.
     * Giáo viên bị giới hạn: chỉ CLASS của lớp mình dạy; ALL chỉ đọc.
     */
    private boolean canManage;
}
