package com.education.base.controller;

import com.education.base.dto.request.RolePermissionUpdateRequest;
import com.education.base.dto.response.FunctionOptionDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.PermissionMenuNodeDto;
import com.education.base.dto.response.RolePermissionMatrixResponse;
import com.education.base.dto.response.RoleResponseDto;
import com.education.base.exception.OracleBusinessException;
import com.education.base.service.RoleAdminService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminRoleController.class)
@Import(WebMvcSecurityTestConfig.class)
class AdminRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoleAdminService roleAdminService;

    private static RoleResponseDto role() {
        return RoleResponseDto.builder().id(2L).roleCode("ROLE_TEACHER").roleName("Giáo viên")
                .status("ACTIVE").active(true).userCount(3L).build();
    }

    @Test
    @WithAuthUser(roles = "ROLE_SUPPORT", permissions = "MENU_USER_LIST:VIEW")
    void search_allowedForUserManagers() throws Exception {
        when(roleAdminService.search(any())).thenReturn(PageResponse.of(List.of(role()), 1, 20, 1));

        mockMvc.perform(get("/api/v1/admin/roles").param("keyword", "teach"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].roleCode").value("ROLE_TEACHER"))
                .andExpect(jsonPath("$.data.content[0].userCount").value(3));
    }

    @Test
    @WithAuthUser(roles = "ROLE_SUPPORT", permissions = "MENU_USER_LIST:VIEW")
    void getPermissions_requiresRoleView() throws Exception {
        mockMvc.perform(get("/api/v1/admin/roles/2/permissions"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(roleAdminService);
    }

    @Test
    @WithAuthUser
    void getPermissions_returnsMatrix() throws Exception {
        when(roleAdminService.getPermissions(2L)).thenReturn(RolePermissionMatrixResponse.builder()
                .roleId(2L).roleCode("ROLE_TEACHER").editable(true)
                .functions(List.of(FunctionOptionDto.builder().code("VIEW").name("Xem danh sách").build()))
                .menus(List.of(PermissionMenuNodeDto.builder().menuId(100L).menuCode("DIR_ACADEMIC")
                        .availableFunctions(List.of("VIEW")).grantedFunctions(List.of("VIEW"))
                        .children(List.of()).build()))
                .build());

        mockMvc.perform(get("/api/v1/admin/roles/2/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editable").value(true))
                .andExpect(jsonPath("$.data.functions[0].code").value("VIEW"))
                .andExpect(jsonPath("$.data.menus[0].grantedFunctions[0]").value("VIEW"));
    }

    @Test
    @WithAuthUser
    void updatePermissions_bindsBody() throws Exception {
        when(roleAdminService.updatePermissions(eq(2L), any())).thenReturn(RolePermissionMatrixResponse.builder()
                .roleId(2L).editable(true).functions(List.of()).menus(List.of()).build());

        mockMvc.perform(put("/api/v1/admin/roles/2/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[{\"menuId\":101,\"functions\":[\"VIEW\",\"EXPORT\"]}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã lưu phân quyền."));

        ArgumentCaptor<RolePermissionUpdateRequest> captor = ArgumentCaptor.forClass(RolePermissionUpdateRequest.class);
        verify(roleAdminService).updatePermissions(eq(2L), captor.capture());
        assertThat(captor.getValue().getPermissions().get(0).getFunctions()).containsExactly("VIEW", "EXPORT");
    }

    @Test
    @WithAuthUser
    void create_invalidCode_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/admin/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"bad code\",\"roleName\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithAuthUser
    void delete_adminRole_returnsBusinessError() throws Exception {
        doThrow(new OracleBusinessException("ADMIN_ROLE_PROTECTED", "Không thể xóa vai trò Quản trị viên hệ thống."))
                .when(roleAdminService).delete(1L);

        mockMvc.perform(delete("/api/v1/admin/roles/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN_ROLE_PROTECTED"));
    }
}
