package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Vai trò trong màn hình quản trị.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleResponseDto {

    private Long id;

    private String roleCode;

    private String roleName;

    private String description;

    private String status;

    private boolean active;

    /** {@code true} với vai trò hệ thống {@code ROLE_ADMIN} (không xóa/khóa, luôn toàn quyền). */
    private boolean system;

    /** Số tài khoản chưa xóa đang được gán vai trò này. */
    private long userCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
