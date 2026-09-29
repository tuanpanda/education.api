package com.education.base.controller;

import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.springframework.context.annotation.Import;
import com.education.base.dto.request.GradeBatchRequest;
import com.education.base.dto.request.GradeUpsertRequest;
import com.education.base.dto.response.GradeResponseDto;
import com.education.base.service.GradeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GradeController.class)
@Import(WebMvcSecurityTestConfig.class)
@WithAuthUser
class GradeControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private GradeService gradeService;

    @Test
    void batch_returnsWrappedGrades() throws Exception {
        GradeBatchRequest request = new GradeBatchRequest(List.of(GradeUpsertRequest.builder()
                .classId(2L).studentId(8L).gradeType("FINAL")
                .score(new BigDecimal("9.00")).build()));
        when(gradeService.upsertBatch(any())).thenReturn(List.of(GradeResponseDto.builder()
                .id(1L).classId(2L).studentId(8L).gradeType("FINAL").score(new BigDecimal("9.00")).build()));

        mockMvc.perform(post("/api/v1/grades/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data[0].gradeType").value("FINAL"));
    }

    @Test
    void batch_scoreOutOfRange_returnsValidationError() throws Exception {
        GradeBatchRequest request = new GradeBatchRequest(List.of(GradeUpsertRequest.builder()
                .classId(2L).studentId(8L).gradeType("FINAL")
                .score(new BigDecimal("11")).build()));

        mockMvc.perform(post("/api/v1/grades/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
