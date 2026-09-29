package com.education.base.controller;

import com.education.base.dto.request.MenuReorderRequest;
import com.education.base.dto.request.MenuUpsertRequest;
import com.education.base.dto.response.AdminMenuResponseDto;
import com.education.base.dto.response.FunctionOptionDto;
import com.education.base.service.MenuAdminService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMenuController.class)
@Import(WebMvcSecurityTestConfig.class)
class AdminMenuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MenuAdminService menuAdminService;

    private static AdminMenuResponseDto menu() {
        return AdminMenuResponseDto.builder().id(303L).parentId(300L).code("MENU_USER_LIST").name("Người dùng")
                .menuType("MENU").path("/admin/users").icon("user-gear").sortOrder(1).active(true)
                .functions(List.of(FunctionOptionDto.builder().id(1L).code("VIEW").name("Xem danh sách").build()))
                .children(List.of()).build();
    }

    @Test
    @WithAuthUser(roles = "ROLE_SUPPORT", permissions = "MENU_MENU_CONFIG:VIEW")
    void tree_withViewPermission() throws Exception {
        when(menuAdminService.getTree()).thenReturn(List.of(menu()));

        mockMvc.perform(get("/api/v1/admin/menus/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("MENU_USER_LIST"))
                .andExpect(jsonPath("$.data[0].path").value("/admin/users"))
                .andExpect(jsonPath("$.data[0].functions[0].code").value("VIEW"));
    }

    @Test
    @WithAuthUser(roles = "ROLE_SUPPORT", permissions = "MENU_MENU_CONFIG:VIEW")
    void create_withoutCreatePermission_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MENU_X\",\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(menuAdminService);
    }

    @Test
    @WithAuthUser
    void create_bindsBody() throws Exception {
        when(menuAdminService.create(any())).thenReturn(menu());

        mockMvc.perform(post("/api/v1/admin/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":300,\"code\":\"MENU_X\",\"name\":\"X\",\"path\":\"/x\",\"icon\":\"bars\","
                                + "\"sortOrder\":5,\"active\":true,\"functionCodes\":[\"VIEW\",\"EXPORT\"]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<MenuUpsertRequest> captor = ArgumentCaptor.forClass(MenuUpsertRequest.class);
        verify(menuAdminService).create(captor.capture());
        assertThat(captor.getValue().getParentId()).isEqualTo(300L);
        assertThat(captor.getValue().getFunctionCodes()).containsExactly("VIEW", "EXPORT");
    }

    @Test
    @WithAuthUser
    void create_invalidCode_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/admin/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"menu x\",\"name\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithAuthUser
    void reorder_isNotCapturedByIdRoute() throws Exception {
        when(menuAdminService.reorder(any())).thenReturn(List.of(menu()));

        mockMvc.perform(put("/api/v1/admin/menus/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":303,\"parentId\":300,\"sortOrder\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã lưu thứ tự menu."));

        ArgumentCaptor<MenuReorderRequest> captor = ArgumentCaptor.forClass(MenuReorderRequest.class);
        verify(menuAdminService).reorder(captor.capture());
        assertThat(captor.getValue().getItems().get(0).getSortOrder()).isEqualTo(2);
    }

    @Test
    @WithAuthUser
    void update_and_delete_delegate() throws Exception {
        when(menuAdminService.update(eq(303L), any())).thenReturn(menu());

        mockMvc.perform(put("/api/v1/admin/menus/303")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MENU_USER_LIST\",\"name\":\"Người dùng\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/menus/303"))
                .andExpect(status().isOk());
        verify(menuAdminService).delete(303L);
    }

    @Test
    @WithAuthUser(roles = "ROLE_SUPPORT", permissions = "MENU_ROLE_LIST:VIEW")
    void functions_allowedForRoleViewers() throws Exception {
        when(menuAdminService.listFunctions()).thenReturn(List.of(
                FunctionOptionDto.builder().code("VIEW").name("Xem danh sách").menuCount(10).build()));

        mockMvc.perform(get("/api/v1/admin/functions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("VIEW"))
                .andExpect(jsonPath("$.data[0].menuCount").value(10));
    }
}
