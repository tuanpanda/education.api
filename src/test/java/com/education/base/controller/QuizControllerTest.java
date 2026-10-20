package com.education.base.controller;

import com.education.base.security.Permissions;
import com.education.base.dto.response.QuizDto;
import com.education.base.exception.ForbiddenException;
import com.education.base.service.quiz.impl.QuizServiceImpl;
import com.education.base.service.quiz.impl.QuizStatsServiceImpl;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QuizController.class)
@Import(WebMvcSecurityTestConfig.class)
class QuizControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    QuizServiceImpl service;
    @MockBean
    QuizStatsServiceImpl statsService;

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = {Permissions.QUIZ_CREATE, Permissions.QUIZ_VIEW})
    void copy_ok_AC40() throws Exception {
        when(service.copy(1L, 8L)).thenReturn(QuizDto.builder().id(2L).classId(8L).status("DRAFT").build());
        mockMvc.perform(post("/api/v1/lms/quizzes/1/copy").contentType(MediaType.APPLICATION_JSON).content("{\"targetClassId\":8}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.classId").value(8));
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = {Permissions.QUIZ_CREATE, Permissions.QUIZ_VIEW})
    void copy_notTeacher403_missingTarget400_QZ_C19() throws Exception {
        when(service.copy(1L, 9L)).thenThrow(new ForbiddenException("NOT_CLASS_TEACHER", "x"));
        mockMvc.perform(post("/api/v1/lms/quizzes/1/copy").contentType(MediaType.APPLICATION_JSON).content("{\"targetClassId\":9}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_CLASS_TEACHER"));
        mockMvc.perform(post("/api/v1/lms/quizzes/1/copy").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = {})
    void noPermission_is403() throws Exception {
        mockMvc.perform(get("/api/v1/lms/quizzes")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void student_cannotUseStaffApi_QZ_J01() throws Exception {
        mockMvc.perform(get("/api/v1/lms/quizzes/1/attempts")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
