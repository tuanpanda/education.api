package com.education.base.controller;

import com.education.base.dto.response.PortalLessonDetailDto;
import com.education.base.dto.response.PortalLessonProgressDto;
import com.education.base.exception.OracleBusinessException;
import com.education.base.service.lesson.PortalLessonService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortalLessonController.class)
@Import(WebMvcSecurityTestConfig.class)
class PortalLessonControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    PortalLessonService portalLessonService;

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void detail_ok_noInternalFields() throws Exception {
        when(portalLessonService.get(11L, 7L)).thenReturn(PortalLessonDetailDto.builder().id(11L).classId(7L)
                .title("Bai").resources(List.of()).build());
        mockMvc.perform(get("/api/v1/portal/me/lessons/11").param("classId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.version").doesNotExist())
                .andExpect(jsonPath("$.data.sourceLessonId").doesNotExist());
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void otherClassOrDraftOrUnopened_is404_not403() throws Exception {
        when(portalLessonService.get(15L, null)).thenThrow(new OracleBusinessException("LESSON_NOT_FOUND", "x"));
        mockMvc.perform(get("/api/v1/portal/me/lessons/15"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LESSON_NOT_FOUND"));
        when(portalLessonService.getTree(8L)).thenThrow(new OracleBusinessException("CLASS_NOT_FOUND", "x"));
        mockMvc.perform(get("/api/v1/portal/me/classes/8/lessons"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void progress_validatesStatus() throws Exception {
        when(portalLessonService.recordProgress(eq(11L), isNull(), any())).thenReturn(PortalLessonProgressDto.builder()
                .lessonId(11L).status("COMPLETED").classPercent(50).build());
        mockMvc.perform(post("/api/v1/portal/me/lessons/11/progress").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classPercent").value(50));
        mockMvc.perform(post("/api/v1/portal/me/lessons/11/progress").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAuthUser(roles = "ROLE_ADMIN")
    void staff_isStudentOnly() throws Exception {
        mockMvc.perform(get("/api/v1/portal/me/lessons"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_ONLY"));
        mockMvc.perform(post("/api/v1/portal/me/lessons/1/progress").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(portalLessonService);
    }

    @Test
    void anonymous_is401() throws Exception {
        mockMvc.perform(get("/api/v1/portal/me/lessons")).andExpect(status().isUnauthorized());
    }
}
