package com.education.base.controller;

import com.education.base.dto.request.CancelSessionRequest;
import com.education.base.dto.request.GenerateSessionsRequest;
import com.education.base.dto.request.SaveClassScheduleRequest;
import com.education.base.dto.request.WeeklySlotRequest;
import com.education.base.dto.response.ClassScheduleResponse;
import com.education.base.dto.response.GenerateSessionsResponse;
import com.education.base.dto.response.TimetableItemDto;
import com.education.base.dto.response.TimetableResponse;
import com.education.base.service.TimetableService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TimetableController.class)
class TimetableControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private TimetableService timetableService;

    @Test
    void saveSchedules_wrapsResponse() throws Exception {
        SaveClassScheduleRequest request = SaveClassScheduleRequest.builder()
                .slots(List.of(WeeklySlotRequest.builder()
                        .dayOfWeek(2).startTime("08:00").endTime("09:30").roomName("A101").build()))
                .build();
        when(timetableService.saveClassSchedules(eq(1L), any())).thenReturn(List.of(
                ClassScheduleResponse.builder().id(9L).classId(1L).dayOfWeek(2)
                        .startTime("08:00").endTime("09:30").build()));

        mockMvc.perform(put("/api/v1/classes/1/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data[0].dayOfWeek").value(2));
    }

    @Test
    void saveSchedules_invalidTime_returnsValidationError() throws Exception {
        mockMvc.perform(put("/api/v1/classes/1/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slots\":[{\"dayOfWeek\":2,\"startTime\":\"8h\",\"endTime\":\"09:00\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void generate_wrapsCounts() throws Exception {
        GenerateSessionsRequest request = GenerateSessionsRequest.builder()
                .fromDate(LocalDate.of(2026, 9, 21))
                .toDate(LocalDate.of(2026, 9, 25))
                .build();
        when(timetableService.generateSessions(eq(1L), any())).thenReturn(GenerateSessionsResponse.builder()
                .classId(1L).createdCount(3).skippedCount(0).build());

        mockMvc.perform(post("/api/v1/classes/1/sessions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdCount").value(3));
    }

    @Test
    void timetable_requiresFromDate() throws Exception {
        mockMvc.perform(get("/api/v1/timetable").param("toDate", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void timetable_wrapsItems() throws Exception {
        when(timetableService.getTimetable(any())).thenReturn(TimetableResponse.builder()
                .fromDate(LocalDate.of(2026, 9, 21))
                .toDate(LocalDate.of(2026, 9, 27))
                .items(List.of(TimetableItemDto.builder().id(1L).classCode("EC9").status("SCHEDULED").build()))
                .build());

        mockMvc.perform(get("/api/v1/timetable")
                        .param("fromDate", "2026-09-21")
                        .param("toDate", "2026-09-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("SCHEDULED"));
        verify(timetableService).getTimetable(any());
    }

    @Test
    void cancel_requiresReason() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/5/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void cancel_wrapsResponse() throws Exception {
        CancelSessionRequest request = CancelSessionRequest.builder().reason("GV nghỉ phép").build();
        when(timetableService.cancelSession(eq(5L), any())).thenReturn(
                TimetableItemDto.builder().id(5L).status("CANCELLED").note("GV nghỉ phép").build());

        mockMvc.perform(post("/api/v1/sessions/5/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}
