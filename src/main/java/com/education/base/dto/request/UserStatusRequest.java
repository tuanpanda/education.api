package com.education.base.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Khóa ({@code active = false}) hoặc mở khóa ({@code active = true}) tài khoản.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusRequest {

    @NotNull(message = "active không được để trống")
    private Boolean active;
}
