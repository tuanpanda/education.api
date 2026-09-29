package com.education.base.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cập nhật vai trò (mã vai trò không đổi).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleUpdateRequest {

    @NotBlank(message = "Tên vai trò không được để trống")
    @Size(max = 100, message = "Tên vai trò không được vượt quá 100 ký tự")
    private String roleName;

    @Size(max = 255, message = "Mô tả không được vượt quá 255 ký tự")
    private String description;

    /** Bỏ trống thì giữ nguyên trạng thái. */
    private Boolean active;
}
