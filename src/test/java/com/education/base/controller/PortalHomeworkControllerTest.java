package com.education.base.controller;

import com.education.base.dto.response.PortalAssignmentListItemDto;
import com.education.base.dto.response.PortalSubmissionDto;
import com.education.base.exception.OracleBusinessException;
import com.education.base.service.homework.PortalHomeworkService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortalHomeworkController.class)
@Import(WebMvcSecurityTestConfig.class)
class PortalHomeworkControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    PortalHomeworkService portalHomeworkService;

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void list_defaultsTab() throws Exception {
        when(portalHomeworkService.list(null)).thenReturn(List.of(PortalAssignmentListItemDto.builder().id(1L)
                .tab("todo").canSubmit(true).build()));
        mockMvc.perform(get("/api/v1/portal/me/assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tab").value("todo"))
                .andExpect(jsonPath("$.data[0].canSubmit").value(true));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void otherStudentsOrDraftAssignment_is404_not403() throws Exception {
        when(portalHomeworkService.get(9L)).thenThrow(new OracleBusinessException("ASSIGNMENT_NOT_FOUND", "x"));
        mockMvc.perform(get("/api/v1/portal/me/assignments/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_FOUND"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void submit_deadlinePassed_is400() throws Exception {
        when(portalHomeworkService.submit(1L)).thenThrow(new OracleBusinessException("SUBMISSION_DEADLINE_PASSED", "x"));
        mockMvc.perform(post("/api/v1/portal/me/assignments/1/submit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUBMISSION_DEADLINE_PASSED"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void saveDraft_ok_andTooLongRejected() throws Exception {
        when(portalHomeworkService.saveDraft(eq(1L), any())).thenReturn(PortalSubmissionDto.builder().id(5L).status("DRAFT").build());
        mockMvc.perform(put("/api/v1/portal/me/assignments/1/submission").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentText\":\"<p>a</p>\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.version").doesNotExist());
        mockMvc.perform(put("/api/v1/portal/me/assignments/1/submission").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentText\":\"" + "a".repeat(50_001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAuthUser(roles = "ROLE_ADMIN")
    void staff_isStudentOnly() throws Exception {
        mockMvc.perform(get("/api/v1/portal/me/assignments"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_ONLY"));
        mockMvc.perform(post("/api/v1/portal/me/assignments/1/submit"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_ONLY"));
        verifyNoInteractions(portalHomeworkService);
    }

    @Test
    void anonymous_is401() throws Exception {
        mockMvc.perform(get("/api/v1/portal/me/assignments/summary"))
                .andExpect(status().isUnauthorized());
    }
}
