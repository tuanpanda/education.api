package com.education.base.controller;

import com.education.base.dto.request.AnnouncementFilterRequest;
import com.education.base.dto.response.AnnouncementDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.service.AnnouncementService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
                1, 20, 1));

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

    /** Hợp đồng với frontend (src/api/announcements.ts): tên query + PageResponse 1-based + JSON field names. */
    @Test
    void search_bindsFrontendQueryParamsAndReturnsPageShape() throws Exception {
        when(announcementService.search(any())).thenReturn(PageResponse.of(
                List.of(AnnouncementDto.builder().id(1L).title("Hello").scopeType("CLASS").classId(5L)
                        .audience("STUDENT").pinned(true).status("ARCHIVED").createdBy("giaovu01").build()),
                2, 20, 21));

        mockMvc.perform(get("/api/v1/announcements")
                        .param("keyword", "lich")
                        .param("status", "ARCHIVED")
                        .param("scopeType", "CLASS")
                        .param("classId", "5")
                        .param("audience", "STUDENT")
                        .param("pageNo", "2")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.totalRows").value(21))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].scopeType").value("CLASS"))
                .andExpect(jsonPath("$.data.content[0].pinned").value(true))
                .andExpect(jsonPath("$.data.content[0].audience").value("STUDENT"))
                .andExpect(jsonPath("$.data.content[0].createdBy").value("giaovu01"));

        ArgumentCaptor<AnnouncementFilterRequest> captor = ArgumentCaptor.forClass(AnnouncementFilterRequest.class);
        verify(announcementService).search(captor.capture());
        AnnouncementFilterRequest filter = captor.getValue();
        assertThat(filter.getKeyword()).isEqualTo("lich");
        assertThat(filter.getStatus()).isEqualTo("ARCHIVED");
        assertThat(filter.getScopeType()).isEqualTo("CLASS");
        assertThat(filter.getClassId()).isEqualTo(5L);
        assertThat(filter.getAudience()).isEqualTo("STUDENT");
        assertThat(filter.getPageNo()).isEqualTo(2);
        assertThat(filter.getPageSize()).isEqualTo(20);
    }

    @Test
    void search_rejectsZeroPageNo() throws Exception {
        mockMvc.perform(get("/api/v1/announcements").param("pageNo", "0"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(announcementService);
    }

    @Test
    void archive_returnsArchived() throws Exception {
        when(announcementService.archive(eq(4L))).thenReturn(AnnouncementDto.builder()
                .id(4L).status("ARCHIVED").build());

        mockMvc.perform(post("/api/v1/announcements/4/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }
}
