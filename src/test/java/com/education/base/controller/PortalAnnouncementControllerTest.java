package com.education.base.controller;

import com.education.base.dto.response.PortalAnnouncementDto;
import com.education.base.service.AnnouncementQueryService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortalAnnouncementController.class)
@Import(WebMvcSecurityTestConfig.class)
class PortalAnnouncementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnnouncementQueryService announcementQueryService;

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void list_asStudent_returnsAnnouncements() throws Exception {
        when(announcementQueryService.listForCurrentStudent()).thenReturn(List.of(
                PortalAnnouncementDto.builder().id(5L).title("TB").read(false).pinned(true).build()));

        mockMvc.perform(get("/api/v1/portal/me/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(5))
                .andExpect(jsonPath("$.data[0].title").value("TB"))
                .andExpect(jsonPath("$.data[0].read").value(false))
                .andExpect(jsonPath("$.data[0].pinned").value(true));
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT")
    void markRead_asStudent_ok() throws Exception {
        mockMvc.perform(post("/api/v1/portal/me/announcements/5/read"))
                .andExpect(status().isOk());
        verify(announcementQueryService).markRead(eq(5L));
    }

    @Test
    @WithAuthUser(roles = "ROLE_ADMIN")
    void list_asStaff_isStudentOnly() throws Exception {
        mockMvc.perform(get("/api/v1/portal/me/announcements"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_ONLY"));
        verifyNoInteractions(announcementQueryService);
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/portal/me/announcements"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(announcementQueryService);
    }
}
