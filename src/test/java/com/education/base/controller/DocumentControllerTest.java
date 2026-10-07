package com.education.base.controller;

import com.education.base.dto.response.DocFolderTreeNodeDto;
import com.education.base.dto.response.DocItemDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.security.Permissions;
import com.education.base.service.DocumentFolderService;
import com.education.base.service.DocumentItemService;
import com.education.base.service.DocumentShareService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import(WebMvcSecurityTestConfig.class)
@WithAuthUser
class DocumentControllerTest {

    private static final String VIEW = Permissions.DOC_LIBRARY_VIEW;
    private static final String UPLOAD = Permissions.DOC_LIBRARY_UPLOAD;

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private DocumentFolderService folderService;
    @MockBean
    private DocumentItemService itemService;
    @MockBean
    private DocumentShareService shareService;

    @Test
    void tree_ok() throws Exception {
        when(folderService.getTree()).thenReturn(List.of(DocFolderTreeNodeDto.builder().id(1L).name("A").build()));
        mockMvc.perform(get("/api/v1/documents/folders/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("A"));
    }

    @Test
    @WithAuthUser(id = 7, username = "gv", roles = "ROLE_TEACHER", permissions = {VIEW})
    void search_bindsFilter() throws Exception {
        when(itemService.search(any())).thenReturn(PageResponse.of(List.of(DocItemDto.builder().id(3L).build()),
                2, 10, 11));
        mockMvc.perform(get("/api/v1/documents/items/search")
                        .param("folderId", "5").param("includeSubfolders", "true").param("q", "toan")
                        .param("tag", "lop10").param("pageNo", "2").param("pageSize", "10")
                        .param("sortBy", "updatedAt").param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));
        ArgumentCaptor<com.education.base.dto.request.DocItemFilterRequest> cap =
                ArgumentCaptor.forClass(com.education.base.dto.request.DocItemFilterRequest.class);
        verify(itemService).search(cap.capture());
        assertThat(cap.getValue().getFolderId()).isEqualTo(5L);
        assertThat(cap.getValue().isIncludeSubfolders()).isTrue();
        assertThat(cap.getValue().getPageNo()).isEqualTo(2);
    }

    @Test
    @WithAuthUser(id = 7, username = "gv", roles = "ROLE_TEACHER", permissions = {VIEW})
    void search_invalidSort_400() throws Exception {
        mockMvc.perform(get("/api/v1/documents/items/search").param("sortBy", "storageKey"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(itemService);
    }

    @Test
    @WithAuthUser(id = 7, username = "gv", roles = "ROLE_TEACHER", permissions = {})
    void withoutView_403() throws Exception {
        mockMvc.perform(get("/api/v1/documents/folders/tree")).andExpect(status().isForbidden());
        verifyNoInteractions(folderService);
    }

    @Test
    @WithAuthUser(id = 7, username = "gv", roles = "ROLE_TEACHER", permissions = {VIEW, UPLOAD})
    void folderWrites_requireManage() throws Exception {
        mockMvc.perform(post("/api/v1/documents/folders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/documents/folders/1")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/documents/folders/1/shares").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shares\":[]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(folderService, shareService);
    }

    @Test
    @WithAuthUser(id = 9, username = "ts", roles = "ROLE_ADMISSION", permissions = {VIEW,
            Permissions.DOC_LIBRARY_DOWNLOAD})
    void viewOnly_cannotUploadEditDeleteShare() throws Exception {
        mockMvc.perform(multipart("/api/v1/documents/items")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/documents/items/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/documents/items/1")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/documents/share-targets")).andExpect(status().isForbidden());
        verifyNoInteractions(itemService, shareService);
    }

    @Test
    @WithAuthUser(id = 7, username = "gv", roles = "ROLE_TEACHER", permissions = {VIEW, UPLOAD})
    void createItem_multipartWithTags() throws Exception {
        when(itemService.create(any(), any())).thenReturn(DocItemDto.builder().id(40L).versionNo(1).build());
        mockMvc.perform(multipart("/api/v1/documents/items")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1}))
                        .param("folderId", "2").param("title", "Đề cương")
                        .param("tags", "toan").param("tags", "lop10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(40));
        ArgumentCaptor<com.education.base.dto.request.DocItemCreateRequest> cap =
                ArgumentCaptor.forClass(com.education.base.dto.request.DocItemCreateRequest.class);
        verify(itemService).create(any(), cap.capture());
        assertThat(cap.getValue().getTags()).containsExactly("toan", "lop10");
        assertThat(cap.getValue().getFolderId()).isEqualTo(2L);
    }

    @Test
    @WithAuthUser(id = 7, username = "gv", roles = "ROLE_TEACHER", permissions = {VIEW, UPLOAD})
    void replaceFile_passesVersionNote() throws Exception {
        when(itemService.replaceFile(eq(40L), any(), eq("v2"))).thenReturn(DocItemDto.builder().versionNo(2).build());
        mockMvc.perform(multipart("/api/v1/documents/items/40/file")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1}))
                        .param("versionNote", "v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(2));
    }

    @Test
    @WithAuthUser(id = 7, username = "gv", roles = "ROLE_TEACHER", permissions = {VIEW, UPLOAD})
    void serviceErrors_mapTo404And403() throws Exception {
        when(itemService.getItem(5L)).thenThrow(new OracleBusinessException("DOC_ITEM_NOT_FOUND", "x"));
        mockMvc.perform(get("/api/v1/documents/items/5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOC_ITEM_NOT_FOUND"));
        when(shareService.putItemShares(eq(6L), any()))
                .thenThrow(new ForbiddenException("DOC_SHARE_CLASS_FORBIDDEN", "x"));
        mockMvc.perform(put("/api/v1/documents/items/6/shares").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shares\":[{\"targetType\":\"CLASS\",\"targetId\":99,\"visibleToStudents\":true}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DOC_SHARE_CLASS_FORBIDDEN"));
    }

    @Test
    @WithAuthUser(id = 7, username = "gv", roles = "ROLE_TEACHER", permissions = {VIEW, UPLOAD})
    void shareRequest_inconsistentTarget_400() throws Exception {
        mockMvc.perform(put("/api/v1/documents/items/6/shares").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shares\":[{\"targetType\":\"ALL_STAFF\",\"targetId\":3}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(shareService);
    }

    @Test
    @WithAuthUser(userType = "STUDENT", studentId = 42, roles = "ROLE_STUDENT", permissions = {VIEW})
    void student_blocked() throws Exception {
        mockMvc.perform(get("/api/v1/documents/folders/tree")).andExpect(status().isForbidden());
        verifyNoInteractions(folderService);
    }
}