package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Một mục menu trong sidebar, có thể chứa danh sách menu con lồng nhau nhiều tầng.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItemResponseDto {

    private Long id;

    /**
     * ID menu cha; {@code null} nếu là menu gốc.
     */
    private Long parentId;

    private String menuCode;

    private String menuName;

    /**
     * {@code DIR} là thư mục chứa menu con, {@code MENU} là trang chức năng.
     */
    private String menuType;

    private String path;

    private String icon;

    private Integer sortOrder;

    /**
     * Danh sách mã chức năng người dùng được phép thực hiện trên menu này
     * (ví dụ: {@code VIEW}, {@code CREATE}, {@code EXPORT}).
     */
    @Builder.Default
    private List<String> allowedFunctions = new ArrayList<>();

    /**
     * Danh sách menu con, rỗng nếu là menu lá.
     */
    @Builder.Default
    private List<MenuItemResponseDto> children = new ArrayList<>();
}
