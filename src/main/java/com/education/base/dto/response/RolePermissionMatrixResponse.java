package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Ma trận phân quyền của vai trò: dòng là cây menu, cột là chức năng.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermissionMatrixResponse {

    private Long roleId;

    private String roleCode;

    private String roleName;

    /** {@code false} với {@code ROLE_ADMIN}: luôn toàn quyền, không chỉnh sửa. */
    private boolean editable;

    /** Các cột chức năng (hợp của chức năng trên mọi menu), theo thứ tự hiển thị. */
    @Builder.Default
    private List<FunctionOptionDto> functions = new ArrayList<>();

    @Builder.Default
    private List<PermissionMenuNodeDto> menus = new ArrayList<>();
}
