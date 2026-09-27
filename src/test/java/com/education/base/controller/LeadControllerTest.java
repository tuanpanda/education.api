package com.education.base.controller;

import com.education.base.dto.request.LeadConvertRequest;
import com.education.base.dto.response.StudentDetailResponse;
import com.education.base.service.LeadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeadController.class)
class LeadControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private LeadService leadService;

    @Test
    void convert_returnsStudentWrappedInApiResponse() throws Exception {
        LeadConvertRequest request = LeadConvertRequest.builder()
                .studentCode("SV01")
                .note("Chuyen doi")
                .build();
        when(leadService.convert(eq(7L), any())).thenReturn(StudentDetailResponse.builder()
                .id(100L).studentCode("SV01").fullName("Tran Van B").status("ACTIVE").build());

        mockMvc.perform(post("/api/v1/leads/7/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.studentCode").value("SV01"));
    }

    @Test
    void convert_missingNote_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/leads/7/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentCode\":\"SV01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
