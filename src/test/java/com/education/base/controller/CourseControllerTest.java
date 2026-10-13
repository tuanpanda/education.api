package com.education.base.controller;

import com.education.base.dto.response.CourseCopyResultDto;
import com.education.base.dto.response.CourseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.exception.OracleBusinessException;
import com.education.base.security.Permissions;
import com.education.base.service.lesson.CourseService;
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

@WebMvcTest(CourseController.class)
@Import(WebMvcSecurityTestConfig.class)
@WithAuthUser
class CourseControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    CourseService courseService;

    @Test
    void search_andDuplicate() throws Exception {
        when(courseService.search(any())).thenReturn(PageResponse.of(List.of(CourseDto.builder().id(1L)
                .courseCode("TOAN9").build()), 1, 20, 1));
        mockMvc.perform(get("/api/v1/lms/courses").param("keyword", "toan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].courseCode").value("TOAN9"));
        when(courseService.create(any())).thenThrow(new OracleBusinessException("COURSE_CODE_DUPLICATE", "x"));
        mockMvc.perform(post("/api/v1/lms/courses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseCode\":\"TOAN9\",\"courseName\":\"Toan 9\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURSE_CODE_DUPLICATE"));
    }

    @Test
    void invalidCode_rejectedByValidation() throws Exception {
        mockMvc.perform(post("/api/v1/lms/courses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseCode\":\"TOAN 9\",\"courseName\":\"Toan 9\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(courseService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = {Permissions.COURSE_VIEW, Permissions.LESSON_CREATE})
    void teacher_viewOnly_butCanCopyToClass() throws Exception {
        mockMvc.perform(put("/api/v1/lms/courses/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseCode\":\"A\",\"courseName\":\"B\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/lms/courses/1/classes/7")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/lms/courses/1")).andExpect(status().isForbidden());
        when(courseService.copyToClass(1L, 7L)).thenReturn(CourseCopyResultDto.builder().courseId(1L).classId(7L)
                .sectionCount(2).lessonCount(3).resourceCount(1).build());
        mockMvc.perform(post("/api/v1/lms/courses/1/copy-to-class/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessonCount").value(3));
    }

    @Test
    void inUse_andNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new OracleBusinessException("COURSE_IN_USE", "x")).when(courseService).softDelete(1L);
        mockMvc.perform(delete("/api/v1/lms/courses/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURSE_IN_USE"));
        when(courseService.getById(9L)).thenThrow(new OracleBusinessException("COURSE_NOT_FOUND", "x"));
        mockMvc.perform(get("/api/v1/lms/courses/9")).andExpect(status().isNotFound());
    }
}
