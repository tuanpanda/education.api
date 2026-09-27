package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Dữ liệu điều hướng của một người dùng: cây menu được phép xem và tập quyền dạng phẳng.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNavigationResponseDto {

    /**
     * Cây menu đa cấp (menu gốc kèm menu con lồng nhau).
     */
    @Builder.Default
    private List<MenuItemResponseDto> menus = List.of();

    /**
     * Tập quyền dạng phẳng theo định dạng {@code MENU_CODE:ACTION},
     * ví dụ: {@code MENU_STUDENT_LIST:EXPORT}.
     */
    @Builder.Default
    private Set<String> permissions = new LinkedHashSet<>();
}
