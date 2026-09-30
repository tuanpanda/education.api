package com.education.base.controller;

import com.education.base.dto.request.UserCreateRequest;
import com.education.base.dto.request.UserFilterRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.RoleSummaryDto;
import com.education.base.dto.response.UserResponseDto;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.service.UserAdminService;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import(WebMvcSecurityTestConfig.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserAdminService userAdminService;

    private static UserResponseDto user() {
        return UserResponseDto.builder()
                .id(3L).username("teacher1").fullName("Giáo viên").status("ACTIVE").active(true)
                .roles(List.of(RoleSummaryDto.builder().id(2L).roleCode("ROLE_TEACHER").roleName("Giáo viên").build()))
                .build();
    }

    @Test
    void search_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(userAdminService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = "MENU_STUDENT_LIST:VIEW")
    void search_withoutPermission_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(userAdminService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_ADMIN", mustChangePassword = true)
    void search_pendingPasswordChange_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    @WithAuthUser(roles = "ROLE_SUPPORT", permissions = "MENU_USER_LIST:VIEW")
    void search_withViewPermission_bindsFilter() throws Exception {
        when(userAdminService.search(any(UserFilterRequest.class)))
                .thenReturn(PageResponse.of(List.of(user()), 2, 10, 11));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("page", "2").param("size", "10")
                        .param("keyword", "teach").param("status", "ACTIVE").param("roleId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].username").value("teacher1"))
                .andExpect(jsonPath("$.data.content[0].roles[0].roleCode").value("ROLE_TEACHER"));

        ArgumentCaptor<UserFilterRequest> captor = ArgumentCaptor.forClass(UserFilterRequest.class);
        verify(userAdminService).search(captor.capture());
        assertThat(captor.getValue().getKeyword()).isEqualTo("teach");
        assertThat(captor.getValue().getRoleId()).isEqualTo(2L);
        assertThat(captor.getValue().getPage()).isEqualTo(2);
    }

    @Test
    @WithAuthUser(roles = "ROLE_SUPPORT", permissions = "MENU_USER_LIST:VIEW")
    void create_withOnlyViewPermission_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new1\",\"password\":\"Pass@1234\",\"fullName\":\"Mới\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userAdminService);
    }

    @Test
    @WithAuthUser
    void create_invalidStatus_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new1\",\"password\":\"Pass@1234\",\"fullName\":\"Mới\",\"status\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithAuthUser
    void create_asAdmin_returnsCreatedUser() throws Exception {
        when(userAdminService.create(any(UserCreateRequest.class))).thenReturn(user());

        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"teacher1\",\"password\":\"Pass@1234\",\"fullName\":\"Giáo viên\",\"roleIds\":[2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3));
    }

    @Test
    @WithAuthUser(id = 1L)
    void changeStatus_passesCurrentUserForSelfGuard() throws Exception {
        when(userAdminService.changeStatus(3L, false, 1L)).thenReturn(user());

        mockMvc.perform(patch("/api/v1/admin/users/3/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã khóa người dùng."));
    }

    @Test
    @WithAuthUser(id = 1L)
    void changeStatus_lockSelf_returnsBusinessError() throws Exception {
        when(userAdminService.changeStatus(eq(1L), anyBoolean(), eq(1L)))
                .thenThrow(new OracleBusinessException("CANNOT_LOCK_SELF", "Không thể khóa tài khoản đang đăng nhập."));

        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_LOCK_SELF"));
    }

    @Test
    @WithAuthUser(id = 1L)
    void delete_lastAdmin_returnsBusinessError() throws Exception {
        doThrow(new OracleBusinessException("LAST_ADMIN", "Không thể xóa quản trị viên cuối cùng của hệ thống."))
                .when(userAdminService).delete(2L, 1L);

        mockMvc.perform(delete("/api/v1/admin/users/2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    @Test
    @WithAuthUser(id = 1L)
    void resetPassword_delegates() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/3/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"Reset@5678\"}"))
                .andExpect(status().isOk());
        verify(userAdminService).resetPassword(3L, "Reset@5678", 1L);
    }

    @Test
    @WithAuthUser
    void assignRoles_delegates() throws Exception {
        when(userAdminService.assignRoles(anyLong(), any())).thenReturn(user());

        mockMvc.perform(put("/api/v1/admin/users/3/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0].id").value(2));
        verify(userAdminService).assignRoles(3L, List.of(2L));
    }

    @Test
    @WithAuthUser(id = 5L, roles = "ROLE_SUPPORT", permissions = "MENU_USER_LIST:UPDATE")
    void assignRoles_adminGuardInService_returns403WithMessage() throws Exception {
        when(userAdminService.assignRoles(anyLong(), any())).thenThrow(new ForbiddenException(
                "ADMIN_ROLE_ASSIGNMENT_FORBIDDEN", "Chỉ quản trị viên hệ thống mới được gán vai trò Quản trị viên."));

        mockMvc.perform(put("/api/v1/admin/users/3/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[1]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ROLE_ASSIGNMENT_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Chỉ quản trị viên hệ thống mới được gán vai trò Quản trị viên."));
    }
}
