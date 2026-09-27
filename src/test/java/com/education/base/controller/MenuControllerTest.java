package com.education.base.controller;

import com.education.base.dto.response.MenuItemResponseDto;
import com.education.base.dto.response.UserNavigationResponseDto;
import com.education.base.repository.MenuRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MenuController.class)
class MenuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MenuRepository menuRepository;

    @Test
    void getUserNavigation_returnsMenuTreeAndFlatPermissions() throws Exception {
        MenuItemResponseDto child = MenuItemResponseDto.builder()
                .id(101L)
                .parentId(100L)
                .menuCode("MENU_STUDENT_LIST")
                .menuName("Hồ sơ Học sinh")
                .menuType("MENU")
                .path("/students/list")
                .icon("users")
                .sortOrder(1)
                .allowedFunctions(List.of("VIEW", "EXPORT"))
                .children(new ArrayList<>())
                .build();
        MenuItemResponseDto root = MenuItemResponseDto.builder()
                .id(100L)
                .menuCode("DIR_ACADEMIC")
                .menuName("Quản lý Đào tạo")
                .menuType("DIR")
                .icon("academic-cap")
                .sortOrder(1)
                .allowedFunctions(List.of("VIEW"))
                .children(new ArrayList<>(List.of(child)))
                .build();

        when(menuRepository.getUserNavigation(1L)).thenReturn(UserNavigationResponseDto.builder()
                .menus(List.of(root))
                .permissions(new LinkedHashSet<>(List.of("MENU_STUDENT_LIST:VIEW", "MENU_STUDENT_LIST:EXPORT")))
                .build());

        mockMvc.perform(get("/api/v1/menus/user-navigation").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.menus[0].menuCode").value("DIR_ACADEMIC"))
                .andExpect(jsonPath("$.data.menus[0].menuType").value("DIR"))
                .andExpect(jsonPath("$.data.menus[0].children[0].menuCode").value("MENU_STUDENT_LIST"))
                .andExpect(jsonPath("$.data.menus[0].children[0].path").value("/students/list"))
                .andExpect(jsonPath("$.data.menus[0].children[0].allowedFunctions[0]").value("VIEW"))
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andExpect(jsonPath("$.data.permissions[0]").value("MENU_STUDENT_LIST:VIEW"));

        verify(menuRepository).getUserNavigation(1L);
    }

    @Test
    void getUserNavigation_missingUserId_returnsValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/menus/user-navigation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Thiếu tham số bắt buộc: userId"));
    }

    @Test
    void getUserNavigation_nonNumericUserId_returnsValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/menus/user-navigation").param("userId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
