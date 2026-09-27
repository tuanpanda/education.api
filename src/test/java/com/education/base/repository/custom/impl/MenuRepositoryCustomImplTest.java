package com.education.base.repository.custom.impl;

import com.education.base.dto.response.MenuItemResponseDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm tra thuật toán gom menu phẳng (từ REF CURSOR) thành cây phân cấp đa tầng.
 */
class MenuRepositoryCustomImplTest {

    @Test
    void buildTree_attachesChildrenEvenWhenChildRowComesBeforeParent() {
        List<MenuItemResponseDto> flat = new ArrayList<>(List.of(
                menu(101L, 100L, "MENU_STUDENT_LIST", 1),
                menu(100L, null, "DIR_ACADEMIC", 1),
                menu(201L, 200L, "MENU_TUITION_PAYMENT", 1),
                menu(200L, null, "DIR_FINANCE", 2)));

        List<MenuItemResponseDto> roots = MenuRepositoryCustomImpl.buildTree(flat);

        assertThat(roots).extracting(MenuItemResponseDto::getMenuCode)
                .containsExactly("DIR_ACADEMIC", "DIR_FINANCE");
        assertThat(roots.get(0).getChildren()).extracting(MenuItemResponseDto::getMenuCode)
                .containsExactly("MENU_STUDENT_LIST");
        assertThat(roots.get(1).getChildren()).extracting(MenuItemResponseDto::getMenuCode)
                .containsExactly("MENU_TUITION_PAYMENT");
    }

    @Test
    void buildTree_sortsRootsAndChildrenBySortOrder() {
        List<MenuItemResponseDto> flat = new ArrayList<>(List.of(
                menu(300L, null, "DIR_SYSTEM", 3),
                menu(100L, null, "DIR_ACADEMIC", 1),
                menu(200L, null, "DIR_FINANCE", 2),
                menu(102L, 100L, "MENU_CLASS", 2),
                menu(101L, 100L, "MENU_STUDENT_LIST", 1)));

        List<MenuItemResponseDto> roots = MenuRepositoryCustomImpl.buildTree(flat);

        assertThat(roots).extracting(MenuItemResponseDto::getMenuCode)
                .containsExactly("DIR_ACADEMIC", "DIR_FINANCE", "DIR_SYSTEM");
        assertThat(roots.get(0).getChildren()).extracting(MenuItemResponseDto::getMenuCode)
                .containsExactly("MENU_STUDENT_LIST", "MENU_CLASS");
    }

    @Test
    void buildTree_supportsMultipleLevels() {
        List<MenuItemResponseDto> flat = new ArrayList<>(List.of(
                menu(1L, null, "LEVEL_1", 1),
                menu(2L, 1L, "LEVEL_2", 1),
                menu(3L, 2L, "LEVEL_3", 1)));

        List<MenuItemResponseDto> roots = MenuRepositoryCustomImpl.buildTree(flat);

        assertThat(roots).hasSize(1);
        MenuItemResponseDto level2 = roots.get(0).getChildren().get(0);
        assertThat(level2.getMenuCode()).isEqualTo("LEVEL_2");
        assertThat(level2.getChildren()).extracting(MenuItemResponseDto::getMenuCode).containsExactly("LEVEL_3");
    }

    @Test
    void buildTree_promotesMenuWhoseParentIsNotPermitted() {
        List<MenuItemResponseDto> flat = new ArrayList<>(List.of(
                menu(101L, 100L, "MENU_STUDENT_LIST", 1)));

        List<MenuItemResponseDto> roots = MenuRepositoryCustomImpl.buildTree(flat);

        assertThat(roots).extracting(MenuItemResponseDto::getMenuCode).containsExactly("MENU_STUDENT_LIST");
    }

    @Test
    void buildTree_emptyInputReturnsEmptyList() {
        assertThat(MenuRepositoryCustomImpl.buildTree(List.of())).isEmpty();
    }

    private static MenuItemResponseDto menu(Long id, Long parentId, String code, Integer sortOrder) {
        return MenuItemResponseDto.builder()
                .id(id)
                .parentId(parentId)
                .menuCode(code)
                .menuName(code)
                .menuType(parentId == null ? "DIR" : "MENU")
                .sortOrder(sortOrder)
                .children(new ArrayList<>())
                .build();
    }
}
