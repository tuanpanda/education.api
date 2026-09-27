package com.education.base.controller;

import com.education.base.dto.request.AttendanceMarkRequest;
import com.education.base.dto.response.AttendanceResponseDto;
import com.education.base.service.AttendanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttendanceController.class)
class AttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private AttendanceService attendanceService;

    @Test
    void batch_returnsWrappedList() throws Exception {
        AttendanceMarkRequest request = AttendanceMarkRequest.builder()
                .classId(3L)
                .attendanceDate(LocalDate.of(2026, 9, 18))
                .entries(List.of(AttendanceMarkRequest.Entry.builder()
                        .studentId(9L).status("PRESENT").build()))
                .build();
        when(attendanceService.markBatch(any())).thenReturn(List.of(AttendanceResponseDto.builder()
                .id(1L).classId(3L).studentId(9L).status("PRESENT").build()));

        mockMvc.perform(post("/api/v1/attendance/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data[0].status").value("PRESENT"));
    }

    @Test
    void batch_emptyEntries_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/attendance/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":3,\"attendanceDate\":\"2026-09-18\",\"entries\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
