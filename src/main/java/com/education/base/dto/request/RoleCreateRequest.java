package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tạo vai trò mới.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleCreateRequest {

    @NotBlank(message = "Mã vai trò không được để trống")
    @Pattern(regexp = DomainConstants.SYSTEM_CODE_PATTERN,
            message = "Mã vai trò chỉ gồm chữ IN HOA, số, gạch dưới (2-50 ký tự), ví dụ ROLE_TEACHER")
    private String roleCode;

    @NotBlank(message = "Tên vai trò không được để trống")
    @Size(max = 100, message = "Tên vai trò không được vượt quá 100 ký tự")
    private String roleName;

    @Size(max = 255, message = "Mô tả không được vượt quá 255 ký tự")
    private String description;

    /** Mặc định {@code true}. */
    private Boolean active;
}
