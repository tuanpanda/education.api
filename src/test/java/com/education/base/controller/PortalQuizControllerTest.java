package com.education.base.controller;

import com.education.base.dto.response.PortalSaveAnswerDto;
import com.education.base.dto.response.PortalSubmitResultDto;
import com.education.base.exception.OracleBusinessException;
import com.education.base.exception.RateLimitedException;
import com.education.base.service.quiz.impl.PortalQuizServiceImpl;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortalQuizController.class)
@Import(WebMvcSecurityTestConfig.class)
class PortalQuizControllerTest {

    static final String JSON_SAVE = "{\"selectedOptionIds\":[12]}";

    @Autowired
    MockMvc mockMvc;
    @MockBean
    PortalQuizServiceImpl service;

    @Test
    @WithAuthUser(roles = "ROLE_ADMIN")
    void staff_isStudentOnly_QZ_K15() throws Exception {
        mockMvc.perform(get("/api/v1/portal/me/quizzes")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_ONLY"));
        mockMvc.perform(post("/api/v1/portal/me/quizzes/1/start")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_ONLY"));
        mockMvc.perform(put("/api/v1/portal/me/attempts/1/answers/2").contentType(MediaType.APPLICATION_JSON).content(JSON_SAVE))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STUDENT_ONLY"));
        verifyNoInteractions(service);
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER")
    void teacher_isStudentOnly_QZ_K16() throws Exception {
        mockMvc.perform(post("/api/v1/portal/me/attempts/1/submit")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_ONLY"));
        verifyNoInteractions(service);
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void foreignAttempt_is404_QZ_K03() throws Exception {
        when(service.getAttempt(9L)).thenThrow(new OracleBusinessException("QUIZ_ATTEMPT_NOT_FOUND", "x"));
        mockMvc.perform(get("/api/v1/portal/me/attempts/9")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUIZ_ATTEMPT_NOT_FOUND"));
        when(service.get(9L)).thenThrow(new OracleBusinessException("QUIZ_NOT_FOUND", "x"));
        mockMvc.perform(get("/api/v1/portal/me/quizzes/9")).andExpect(status().isNotFound());
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void rateLimited_is429WithRetryAfter_QZ_L02() throws Exception {
        when(service.saveAnswer(eq(1L), eq(2L), any())).thenThrow(new RateLimitedException("RATE_LIMITED", "Chậm lại", 7));
        mockMvc.perform(put("/api/v1/portal/me/attempts/1/answers/2").contentType(MediaType.APPLICATION_JSON).content(JSON_SAVE))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "7"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED")).andExpect(jsonPath("$.data.retryAfterSeconds").value(7));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void save_expired_is400_save_ok() throws Exception {
        when(service.saveAnswer(eq(1L), eq(2L), any())).thenReturn(PortalSaveAnswerDto.builder().questionId(2L)
                .remainingSeconds(10L).build());
        mockMvc.perform(put("/api/v1/portal/me/attempts/1/answers/2").contentType(MediaType.APPLICATION_JSON).content(JSON_SAVE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.questionId").value(2));
        when(service.saveAnswer(eq(1L), eq(3L), any())).thenThrow(new OracleBusinessException("QUIZ_ATTEMPT_EXPIRED", "x"));
        mockMvc.perform(put("/api/v1/portal/me/attempts/1/answers/3").contentType(MediaType.APPLICATION_JSON).content(JSON_SAVE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("QUIZ_ATTEMPT_EXPIRED"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void lazySubmit_is200AutoSubmitted_AC39() throws Exception {
        when(service.submit(1L)).thenReturn(PortalSubmitResultDto.builder().attemptId(1L).status("AUTO_SUBMITTED")
                .remainingSeconds(0L).build());
        mockMvc.perform(post("/api/v1/portal/me/attempts/1/submit")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AUTO_SUBMITTED"));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void events_200_foreign404_badType400_QZ_K18_K19() throws Exception {
        mockMvc.perform(post("/api/v1/portal/me/attempts/1/events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"FOCUS_LOST\"}")).andExpect(status().isOk());
        verify(service).event(eq(1L), any(), any(), any());
        doThrow(new OracleBusinessException("QUIZ_ATTEMPT_NOT_FOUND", "x")).when(service).event(eq(9L), any(), any(), any());
        mockMvc.perform(post("/api/v1/portal/me/attempts/9/events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"FOCUS_LOST\"}")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/portal/me/attempts/1/events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"HACK\"}")).andExpect(status().isBadRequest());
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void list_statusAliasForTab_QZ_K12() throws Exception {
        when(service.list("DONE")).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/portal/me/quizzes").param("status", "DONE")).andExpect(status().isOk());
        verify(service).list("DONE");
    }
}
