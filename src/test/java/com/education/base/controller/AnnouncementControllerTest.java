package com.education.base.controller;

import com.education.base.dto.response.AnnouncementDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.service.AnnouncementService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnnouncementController.class)
@Import(WebMvcSecurityTestConfig.class)
@WithAuthUser
class AnnouncementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnnouncementService announcementService;

    @Test
    void search_returnsPage() throws Exception {
        when(announcementService.search(any())).thenReturn(PageResponse.of(
                List.of(AnnouncementDto.builder().id(1L).title("Hello").status("DRAFT").build()),
                0, 20, 1));

        mockMvc.perform(get("/api/v1/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Hello"));
    }

    @Test
    void create_returnsCreated() throws Exception {
        when(announcementService.create(any())).thenReturn(AnnouncementDto.builder()
                .id(9L).title("Tieu de").status("DRAFT").scopeType("ALL").audience("STUDENT").build());

        mockMvc.perform(post("/api/v1/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Tieu de","content":"<p>Hi</p>","scopeType":"ALL","audience":"STUDENT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void publish_returnsPublished() throws Exception {
        when(announcementService.publish(eq(3L))).thenReturn(AnnouncementDto.builder()
                .id(3L).status("PUBLISHED").build());

        mockMvc.perform(post("/api/v1/announcements/3/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    void update_and_delete_ok() throws Exception {
        when(announcementService.update(eq(2L), any())).thenReturn(AnnouncementDto.builder()
                .id(2L).title("Updated").build());

        mockMvc.perform(put("/api/v1/announcements/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated","content":"<b>x</b>","scopeType":"ALL","audience":"ALL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated"));

        mockMvc.perform(delete("/api/v1/announcements/2"))
                .andExpect(status().isOk());
        verify(announcementService).softDelete(2L);
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER")
    void search_withoutPermission_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/announcements"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(announcementService);
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void student_cannotCallStaffApis() throws Exception {
        mockMvc.perform(get("/api/v1/announcements"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/announcements/1/publish"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(announcementService);
    }
}
