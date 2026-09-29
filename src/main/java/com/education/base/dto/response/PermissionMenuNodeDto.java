package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Một dòng (menu) trong ma trận phân quyền của vai trò, lồng nhau theo cây menu.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionMenuNodeDto {

    private Long menuId;

    private Long parentId;

    private String menuCode;

    private String menuName;

    private String menuType;

    private Integer sortOrder;

    private boolean active;

    /** Chức năng menu hỗ trợ (ô có thể tick). */
    @Builder.Default
    private List<String> availableFunctions = new ArrayList<>();

    /** Chức năng vai trò đang được cấp. */
    @Builder.Default
    private List<String> grantedFunctions = new ArrayList<>();

    @Builder.Default
    private List<PermissionMenuNodeDto> children = new ArrayList<>();
}
