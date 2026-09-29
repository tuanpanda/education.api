package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Người dùng trong màn hình quản trị.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDto {

    private Long id;

    private String username;

    private String fullName;

    private String email;

    private String phone;

    /** {@code ACTIVE}, {@code INACTIVE} hoặc {@code LOCKED}. */
    private String status;

    /** {@code true} khi {@code status = ACTIVE}. */
    private boolean active;

    private boolean mustChangePassword;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String createdBy;

    private String updatedBy;

    @Builder.Default
    private List<RoleSummaryDto> roles = new ArrayList<>();
}
