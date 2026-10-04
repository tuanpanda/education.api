package com.education.base.controller;

import com.education.base.dto.request.StudentAccountFilterRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentAccountBulkResultDto;
import com.education.base.dto.response.StudentAccountCredentialDto;
import com.education.base.dto.response.StudentAccountDto;
import com.education.base.dto.response.StudentAccountStatusDto;
import com.education.base.exception.OracleBusinessException;
import com.education.base.service.StudentAccountService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentAccountController.class)
@Import(WebMvcSecurityTestConfig.class)
class StudentAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentAccountService studentAccountService;

    @Test
    void search_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/student-accounts/search"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(studentAccountService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = "MENU_STUDENT_LIST:VIEW")
    void search_withoutPermission_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/student-accounts/search"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(studentAccountService);
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = {"ROLE_STUDENT", "ROLE_ADMIN"},
            permissions = "MENU_STUDENT_ACCOUNT:VIEW")
    void search_asStudent_isStaffOnly() throws Exception {
        mockMvc.perform(get("/api/v1/student-accounts/search"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAFF_ONLY"));
        verifyNoInteractions(studentAccountService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_SUPPORT", permissions = "MENU_STUDENT_ACCOUNT:VIEW")
    void search_withViewPermission_bindsFilter() throws Exception {
        when(studentAccountService.search(any(StudentAccountFilterRequest.class))).thenReturn(PageResponse.of(
                List.of(StudentAccountDto.builder().studentId(1L).studentCode("HS1").fullName("A")
                        .userId(5L).username("hs00001").accountStatus("ACTIVE")
                        .lastLoginAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build()), 2, 10, 11));

        mockMvc.perform(get("/api/v1/student-accounts/search")
                        .param("keyword", "an").param("classId", "3").param("hasAccount", "true")
                        .param("pageNo", "2").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].username").value("hs00001"))
                .andExpect(jsonPath("$.data.content[0].accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.content[0].lastLoginAt").exists());

        ArgumentCaptor<StudentAccountFilterRequest> filter = ArgumentCaptor.forClass(StudentAccountFilterRequest.class);
        verify(studentAccountService).search(filter.capture());
        assertThat(filter.getValue().getKeyword()).isEqualTo("an");
        assertThat(filter.getValue().getClassId()).isEqualTo(3L);
        assertThat(filter.getValue().getHasAccount()).isTrue();
        assertThat(filter.getValue().getPageNo()).isEqualTo(2);
        assertThat(filter.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @WithAuthUser(roles = "ROLE_SUPPORT", permissions = "MENU_STUDENT_ACCOUNT:VIEW")
    void bulk_withOnlyViewPermission_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/student-accounts/bulk")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"studentIds\":[1]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(studentAccountService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_SUPPORT", permissions = "MENU_STUDENT_ACCOUNT:CREATE")
    void bulk_returnsCreatedAndSkippedRows() throws Exception {
        when(studentAccountService.bulkCreate(List.of(1L, 2L))).thenReturn(List.of(
                StudentAccountBulkResultDto.builder().studentId(1L).studentCode("HS1").fullName("A")
                        .username("hs00001").tempPassword("k7m2p9x4qa").status("CREATED").build(),
                StudentAccountBulkResultDto.builder().studentId(2L).studentCode("HS2").fullName("B")
                        .username("hs00002").status("SKIPPED").reason("ALREADY_HAS_ACCOUNT").build()));

        mockMvc.perform(post("/api/v1/student-accounts/bulk")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"studentIds\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data[0].tempPassword").value("k7m2p9x4qa"))
                .andExpect(jsonPath("$.data[1].status").value("SKIPPED"))
                .andExpect(jsonPath("$.data[1].reason").value("ALREADY_HAS_ACCOUNT"));
    }

    @Test
    @WithAuthUser(roles = "ROLE_SUPPORT", permissions = "MENU_STUDENT_ACCOUNT:CREATE")
    void bulk_emptyIds_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/student-accounts/bulk")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"studentIds\":[]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(studentAccountService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_SUPPORT", permissions = "MENU_STUDENT_ACCOUNT:CREATE")
    void bulk_tooManyIds_returns400() throws Exception {
        StringBuilder ids = new StringBuilder();
        for (int i = 1; i <= 201; i++) {
            ids.append(i == 1 ? "" : ",").append(i);
        }
        mockMvc.perform(post("/api/v1/student-accounts/bulk")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"studentIds\":[" + ids + "]}"))
                .andExpect(status().isBadRequest());
        verify(studentAccountService, org.mockito.Mockito.never()).bulkCreate(anyList());
    }

    @Test
    @WithAuthUser(roles = "ROLE_SUPPORT", permissions = "MENU_STUDENT_ACCOUNT:RESET_PASSWORD")
    void resetPassword_returnsCredential() throws Exception {
        when(studentAccountService.resetPassword(5L)).thenReturn(
                StudentAccountCredentialDto.builder().username("hs00005").tempPassword("r3t8w2n6hb").build());

        mockMvc.perform(post("/api/v1/student-accounts/5/reset-password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("hs00005"))
                .andExpect(jsonPath("$.data.tempPassword").value("r3t8w2n6hb"));
    }

    @Test
    @WithAuthUser(roles = "ROLE_SUPPORT", permissions = "MENU_STUDENT_ACCOUNT:LOCK")
    void resetPassword_withLockPermissionOnly_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/student-accounts/5/reset-password"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(studentAccountService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_SUPPORT", permissions = "MENU_STUDENT_ACCOUNT:LOCK")
    void lockAndUnlock() throws Exception {
        when(studentAccountService.lock(5L)).thenReturn(
                StudentAccountStatusDto.builder().userId(5L).username("hs00005").accountStatus("LOCKED").build());
        when(studentAccountService.unlock(5L)).thenReturn(
                StudentAccountStatusDto.builder().userId(5L).username("hs00005").accountStatus("ACTIVE").build());

        mockMvc.perform(post("/api/v1/student-accounts/5/lock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountStatus").value("LOCKED"));
        mockMvc.perform(post("/api/v1/student-accounts/5/unlock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"));
    }

    @Test
    @WithAuthUser(roles = "ROLE_ADMIN")
    void lock_staffUserId_returns404() throws Exception {
        when(studentAccountService.lock(2L)).thenThrow(new OracleBusinessException("USER_NOT_FOUND", "x"));

        mockMvc.perform(post("/api/v1/student-accounts/2/lock"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
