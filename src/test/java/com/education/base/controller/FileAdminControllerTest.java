package com.education.base.controller;

import com.education.base.dto.request.FileFilterRequest;
import com.education.base.dto.response.FileAdminDto;
import com.education.base.dto.response.FileBulkResultDto;
import com.education.base.dto.response.FileScanItemDto;
import com.education.base.dto.response.FileScanRunDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.entity.FileEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.security.Permissions;
import com.education.base.service.FileAdminService;
import com.education.base.service.file.FileMaintenanceService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileAdminController.class)
@Import(WebMvcSecurityTestConfig.class)
class FileAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private FileAdminService fileAdminService;
    @MockBean
    private FileMaintenanceService maintenanceService;

    @Test
    @WithAnonymousUser
    void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/files/search")).andExpect(status().isUnauthorized());
        verifyNoInteractions(fileAdminService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_TEACHER", permissions = {"MENU_FILE_EXPLORER:VIEW", "MENU_FILE_EXPLORER:DOWNLOAD"})
    void teacherWithLibraryPermissions_isForbiddenEverywhere() throws Exception {
        mockMvc.perform(get("/api/v1/admin/files/search")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/files/1/download")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/files/1/delete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/files/maintenance/orphan-scan")).andExpect(status().isForbidden());
        verifyNoInteractions(fileAdminService, maintenanceService);
    }

    @Test
    @WithAuthUser(roles = "ROLE_OPS", permissions = Permissions.FILE_MANAGER_VIEW)
    void viewOnly_canSearchButNotDeleteOrConfigure() throws Exception {
        when(fileAdminService.search(any())).thenReturn(PageResponse.of(List.of(FileAdminDto.builder().id(1L)
                .originalName("a.pdf").build()), 2, 50, 51));
        mockMvc.perform(get("/api/v1/admin/files/search")
                        .param("status", "ALL").param("module", "STUDENT").param("q", "Hồ sơ")
                        .param("createdFrom", "2026-10-01").param("createdTo", "2026-10-07")
                        .param("pageNo", "2").param("pageSize", "50").param("sortBy", "fileSize")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(2));
        ArgumentCaptor<FileFilterRequest> captor = ArgumentCaptor.forClass(FileFilterRequest.class);
        verify(fileAdminService).search(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ALL");
        assertThat(captor.getValue().getPageNo()).isEqualTo(2);
        assertThat(captor.getValue().getSortBy()).isEqualTo("fileSize");

        mockMvc.perform(post("/api/v1/admin/files/1/delete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/files/settings/quotas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quotas\":[]}")).andExpect(status().isForbidden());
        verify(fileAdminService, never()).delete(anyLong(), any());
    }

    @Test
    @WithAuthUser
    void search_validation() throws Exception {
        mockMvc.perform(get("/api/v1/admin/files/search").param("pageSize", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/admin/files/search").param("status", "GONE"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/files/search").param("sortBy", "filePath"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/files/search").param("minSize", "10").param("maxSize", "1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(fileAdminService);
    }

    @Test
    @WithAuthUser
    void download_setsSafeHeaders() throws Exception {
        FileEntity file = FileEntity.builder().id(5L).originalName("Hồ sơ.pdf").build();
        when(fileAdminService.open(5L, true)).thenReturn(new FileAdminService.FileContent(file,
                new ByteArrayResource(new byte[]{1, 2, 3}), "application/pdf", false));
        mockMvc.perform(get("/api/v1/admin/files/5/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"H%E1%BB%93%20s%C6%A1.pdf\"; filename*=UTF-8''H%E1%BB%93%20s%C6%A1.pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"));
    }

    @Test
    @WithAuthUser
    void view_inlineWhenServiceSaysSafe_andBlobMissingIsJsonError() throws Exception {
        FileEntity file = FileEntity.builder().id(5L).originalName("a.png").build();
        when(fileAdminService.open(5L, false)).thenReturn(new FileAdminService.FileContent(file,
                new ByteArrayResource(new byte[]{1}), "image/png", true));
        mockMvc.perform(get("/api/v1/admin/files/5/view"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline;")));
        when(fileAdminService.open(6L, false)).thenThrow(new OracleBusinessException("FILE_BLOB_MISSING", "x"));
        mockMvc.perform(get("/api/v1/admin/files/6/view")).andExpect(status().isBadRequest());
    }

    @Test
    @WithAuthUser
    void delete_requiresReason_andBulkLimit() throws Exception {
        mockMvc.perform(post("/api/v1/admin/files/1/delete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
        String ids = java.util.stream.LongStream.rangeClosed(1, 101).mapToObj(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        mockMvc.perform(post("/api/v1/admin/files/bulk-delete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[" + ids + "],\"reason\":\"x\"}")).andExpect(status().isBadRequest());
        when(fileAdminService.bulkRestore(any())).thenReturn(FileBulkResultDto.builder().succeeded(List.of(1L))
                .build());
        mockMvc.perform(post("/api/v1/admin/files/bulk-restore").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.succeeded[0]").value(1));
    }

    @Test
    @WithAuthUser
    void purge_errorsMapToHttp() throws Exception {
        when(fileAdminService.purge(eq(1L), any())).thenThrow(
                new OracleBusinessException("FILE_PURGE_CONFIRM_MISMATCH", "Tên file xác nhận không khớp."));
        mockMvc.perform(post("/api/v1/admin/files/1/purge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"r\",\"confirmName\":\"b.pdf\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_PURGE_CONFIRM_MISMATCH"));
        when(fileAdminService.purge(eq(2L), any())).thenThrow(new OracleBusinessException("FILE_NOT_FOUND", "x"));
        mockMvc.perform(post("/api/v1/admin/files/2/purge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"r\",\"confirmName\":\"b.pdf\"}")).andExpect(status().isNotFound());
    }

    @Test
    @WithAuthUser
    void settings_validation() throws Exception {
        mockMvc.perform(put("/api/v1/admin/files/settings/modules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settings\":[{\"moduleName\":\"STUDENT\",\"maxFileSizeBytes\":0,"
                                + "\"trashRetentionDays\":30}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/files/settings/quotas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quotas\":[{\"scopeType\":\"WORLD\",\"maxBytes\":1}]}"))
                .andExpect(status().isBadRequest());
        verify(fileAdminService, never()).updateModuleSettings(any());
    }

    @Test
    @WithAuthUser(roles = "ROLE_OPS", permissions = Permissions.FILE_MANAGER_MAINTAIN)
    void maintenanceEndpoints() throws Exception {
        when(maintenanceService.startOrphanScan()).thenReturn(FileScanRunDto.builder().id(3L).status("RUNNING")
                .runType("ORPHAN").build());
        mockMvc.perform(post("/api/v1/admin/files/maintenance/orphan-scan"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RUNNING"));
        when(maintenanceService.startChecksumBackfill()).thenThrow(
                new OracleBusinessException("FILE_SCAN_RUNNING", "Đang chạy"));
        mockMvc.perform(post("/api/v1/admin/files/maintenance/checksum-backfill"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_SCAN_RUNNING"));
        when(maintenanceService.listItems(eq(3L), eq("ORPHAN_BLOB"), eq(false), eq(1), eq(20)))
                .thenReturn(PageResponse.of(List.of(FileScanItemDto.builder().id(9L).build()), 1, 20, 1));
        mockMvc.perform(get("/api/v1/admin/files/maintenance/scans/3/items")
                        .param("itemType", "ORPHAN_BLOB").param("resolved", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(9));
        mockMvc.perform(get("/api/v1/admin/files/maintenance/scans/3/items").param("itemType", "BAD"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/files/maintenance/scans").param("pageSize", "500"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/files/maintenance/scans/3/items/9/resolve")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"NUKE\"}"))
                .andExpect(status().isBadRequest());
        verify(maintenanceService, never()).resolveItem(anyLong(), anyLong(), any());
        // Người chỉ có MAINTAIN không xem được danh sách file.
        mockMvc.perform(get("/api/v1/admin/files/search")).andExpect(status().isForbidden());
        verify(maintenanceService, never()).listRuns(anyInt(), anyInt());
        verify(fileAdminService, never()).open(anyLong(), anyBoolean());
    }
}
