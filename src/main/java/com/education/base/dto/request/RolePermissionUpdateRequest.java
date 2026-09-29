package com.education.base.dto.request;

import com.education.base.common.DomainConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Ghi đè toàn bộ ma trận quyền (menu x chức năng) của một vai trò.
 * Menu không có trong danh sách (hoặc {@code functions} rỗng) sẽ bị thu hồi quyền.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermissionUpdateRequest {

    @NotNull(message = "Danh sách quyền không được để trống")
    @Builder.Default
    private List<@Valid @NotNull MenuPermission> permissions = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MenuPermission {

        @NotNull(message = "menuId không được để trống")
        private Long menuId;

        @Builder.Default
        private List<@NotNull @Pattern(regexp = DomainConstants.SYSTEM_CODE_PATTERN,
                message = "Mã chức năng không hợp lệ") String> functions = new ArrayList<>();
    }
}
