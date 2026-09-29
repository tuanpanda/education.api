package com.education.base.controller;

import com.education.base.dto.response.MenuItemResponseDto;
import com.education.base.dto.response.UserNavigationResponseDto;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.service.AccessControlService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MenuController.class)
@Import(WebMvcSecurityTestConfig.class)
class MenuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccessControlService accessControlService;

    @Test
    @WithAuthUser(id = 7L, username = "teacher1", roles = "ROLE_TEACHER",
            permissions = {"MENU_STUDENT_LIST:VIEW", "MENU_STUDENT_LIST:EXPORT"})
    void getUserNavigation_usesUserFromToken() throws Exception {
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

        when(accessControlService.getNavigation(any(AuthUserPrincipal.class))).thenReturn(UserNavigationResponseDto.builder()
                .menus(List.of(root))
                .permissions(new LinkedHashSet<>(List.of("MENU_STUDENT_LIST:VIEW", "MENU_STUDENT_LIST:EXPORT")))
                .build());

        mockMvc.perform(get("/api/v1/menus/user-navigation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.menus[0].menuCode").value("DIR_ACADEMIC"))
                .andExpect(jsonPath("$.data.menus[0].menuType").value("DIR"))
                .andExpect(jsonPath("$.data.menus[0].children[0].menuCode").value("MENU_STUDENT_LIST"))
                .andExpect(jsonPath("$.data.menus[0].children[0].path").value("/students/list"))
                .andExpect(jsonPath("$.data.menus[0].children[0].allowedFunctions[0]").value("VIEW"))
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andExpect(jsonPath("$.data.permissions[0]").value("MENU_STUDENT_LIST:VIEW"));

        ArgumentCaptor<AuthUserPrincipal> captor = ArgumentCaptor.forClass(AuthUserPrincipal.class);
        verify(accessControlService).getNavigation(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(7L);
        assertThat(captor.getValue().getUsername()).isEqualTo("teacher1");
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", mustChangePassword = true)
    void getUserNavigation_allowedWhilePasswordChangePending() throws Exception {
        when(accessControlService.getNavigation(any(AuthUserPrincipal.class))).thenReturn(
                UserNavigationResponseDto.builder().menus(List.of()).permissions(new LinkedHashSet<>()).build());

        mockMvc.perform(get("/api/v1/menus/user-navigation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"));
    }

    @Test
    void getUserNavigation_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/menus/user-navigation"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(accessControlService);
    }
}
