package com.education.base.controller;

import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.springframework.context.annotation.Import;
import com.education.base.dto.request.ClassCreateRequest;
import com.education.base.dto.request.ClassFilterRequest;
import com.education.base.dto.request.EnrollStudentsRequest;
import com.education.base.dto.response.ClassDetailResponse;
import com.education.base.dto.response.ClassOptionResponse;
import com.education.base.dto.response.ClassReportDto;
import com.education.base.dto.response.EnrolledStudentDto;
import com.education.base.dto.response.NextClassCodeResponse;
import com.education.base.dto.response.PageResponse;
import com.education.base.service.ClassService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClassController.class)
@Import(WebMvcSecurityTestConfig.class)
@WithAuthUser
class ClassControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private ClassService classService;

    @Test
    void search_returnsPagedApiResponse() throws Exception {
        when(classService.search(any(ClassFilterRequest.class)))
                .thenReturn(PageResponse.of(List.of(ClassReportDto.builder()
                        .id(1L).classCode("C01").className("TOEIC 450").status("OPEN").build()), 1, 20, 1));

        mockMvc.perform(get("/api/v1/classes/search").param("keyword", "TOEIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.content[0].classCode").value("C01"));
    }

    @Test
    void create_validatesAndWrapsResponse() throws Exception {
        ClassCreateRequest request = new ClassCreateRequest();
        request.setClassName("TOEIC 450");
        request.setGradeLevel(9);
        when(classService.create(any())).thenReturn(ClassDetailResponse.builder()
                .id(1L).classCode("LH920260001").className("TOEIC 450").gradeLevel(9).build());

        mockMvc.perform(post("/api/v1/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.classCode").value("LH920260001"));
    }

    @Test
    void create_missingGradeLevel_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"className\":\"TOEIC 450\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_missingClassName_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void enroll_callsServiceWithPathId() throws Exception {
        EnrollStudentsRequest request = EnrollStudentsRequest.builder()
                .classId(5L).studentIds(List.of(9L)).build();
        when(classService.enroll(eq(5L), any())).thenReturn(List.of(EnrolledStudentDto.builder()
                .studentId(9L).studentCode("SV01").status("ENROLLED").build()));

        mockMvc.perform(post("/api/v1/classes/5/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].studentCode").value("SV01"));
        verify(classService).enroll(eq(5L), any());
    }

    @Test
    void unenroll_callsService() throws Exception {
        mockMvc.perform(delete("/api/v1/classes/5/students/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"));
        verify(classService).unenroll(5L, 9L);
    }

    @Test
    void listOpenOptions_returnsDropdownPayload() throws Exception {
        when(classService.listOpenOptions()).thenReturn(List.of(ClassOptionResponse.builder()
                .id(1L)
                .classCode("C01")
                .className("TOEIC 450")
                .courseName("TOEIC")
                .status("OPEN")
                .build()));

        mockMvc.perform(get("/api/v1/classes/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].classCode").value("C01"))
                .andExpect(jsonPath("$.data[0].courseName").value("TOEIC"))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"));
        verify(classService).listOpenOptions();
    }

    @Test
    void peekNextCode_returnsPreviewFromRule() throws Exception {
        when(classService.peekNextClassCode(9)).thenReturn(NextClassCodeResponse.builder()
                .classCode("LH920260001")
                .gradeLevel(9)
                .preview(true)
                .build());

        mockMvc.perform(get("/api/v1/classes/next-code").param("gradeLevel", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.classCode").value("LH920260001"))
                .andExpect(jsonPath("$.data.gradeLevel").value(9))
                .andExpect(jsonPath("$.data.preview").value(true));
        verify(classService).peekNextClassCode(9);
    }
}
