package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu trong màn hình quản trị menu (cây đa cấp).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminMenuResponseDto {

    private Long id;

    private Long parentId;

    private String code;

    private String name;

    /** {@code DIR} hoặc {@code MENU}. */
    private String menuType;

    private String path;

    private String icon;

    private Integer sortOrder;

    private boolean active;

    private boolean hidden;

    @Builder.Default
    private List<FunctionOptionDto> functions = new ArrayList<>();

    @Builder.Default
    private List<AdminMenuResponseDto> children = new ArrayList<>();
}
