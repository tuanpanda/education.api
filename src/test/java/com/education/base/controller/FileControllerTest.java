package com.education.base.controller;

import com.education.base.audit.AuditActions;
import com.education.base.config.FilesProperties;
import com.education.base.dto.response.FileUploadPolicyDto;
import com.education.base.entity.FileEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapperImpl;
import com.education.base.security.FileAccessPolicy;
import com.education.base.security.Permissions;
import com.education.base.service.AuditService;
import com.education.base.service.FileStorageService;
import com.education.base.service.file.FileLifecycleService;
import com.education.base.service.file.FileLimitService;
import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@Import({WebMvcSecurityTestConfig.class, FileMapperImpl.class, FilesProperties.class})
@WithAuthUser
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileStorageService fileStorageService;
    @MockBean
    private FileAccessPolicy fileAccessPolicy;
    @MockBean
    private FileLifecycleService fileLifecycleService;
    @MockBean
    private FileLimitService fileLimitService;
    @MockBean
    private AuditService auditService;

    @Test
    void upload_checksPolicyAndReturnsMetadata() throws Exception {
        FileEntity saved = FileEntity.builder().id(8L).originalName("Hồ sơ.pdf").contentType("application/pdf")
                .fileSize(3L).moduleName("STUDENT").referenceId(15L).createdBy("giaovu").fileExt("pdf")
                .checksumSha256("ab".repeat(32)).createdAt(LocalDateTime.of(2026, 9, 16, 10, 0)).build();
        when(fileStorageService.storeFile(any(), eq("STUDENT"), eq(15L))).thenReturn(saved);
        when(fileAccessPolicy.canDelete(saved)).thenReturn(true);

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "Hồ sơ.pdf", "application/pdf", "abc".getBytes()))
                        .param("moduleName", "STUDENT")
                        .param("referenceId", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.id").value(8))
                .andExpect(jsonPath("$.data.originalName").value("Hồ sơ.pdf"))
                .andExpect(jsonPath("$.data.viewUrl").value("/api/v1/files/view/8"))
                .andExpect(jsonPath("$.data.downloadUrl").value("/api/v1/files/download/8"))
                .andExpect(jsonPath("$.data.fileExt").value("pdf"))
                .andExpect(jsonPath("$.data.uploadedBy").value("giaovu"))
                .andExpect(jsonPath("$.data.checksumSha256").value("ab".repeat(32)))
                .andExpect(jsonPath("$.data.canDelete").value(true));
        verify(fileAccessPolicy).checkUpload("STUDENT", 15L);
    }

    @Test
    void upload_policyRejection_doesNotStore() throws Exception {
        doThrow(new OracleBusinessException("INVALID_MODULE", "x")).when(fileAccessPolicy).checkUpload("LIBRARY", 1L);

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.4".getBytes()))
                        .param("moduleName", "LIBRARY")
                        .param("referenceId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MODULE"));
        verify(fileStorageService, never()).storeFile(any(), anyString(), any());
    }

    @Test
    void upload_storageErrorCode_isPropagated() throws Exception {
        when(fileStorageService.storeFile(any(), eq("COMMON"), isNull())).thenThrow(
                new OracleBusinessException("FILE_QUOTA_EXCEEDED", "het quota"));

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_QUOTA_EXCEEDED"));
    }

    @Test
    void viewFile_pdf_isInlineWithAllSecurityHeaders() throws Exception {
        stubFile(fileOf(30L, "TUITION", 4L, "phieu.pdf", "application/pdf"));

        mockMvc.perform(get("/api/v1/files/view/30"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("inline;")))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"));
        verify(fileAccessPolicy).checkRead(any(FileEntity.class), eq(FileAccessPolicy.Action.VIEW));
        verify(auditService, never()).success(eq(AuditActions.FILE_VIEWED), anyString(), any(), anyMap());
    }

    @Test
    void viewFile_unicodeName_isRfc5987Encoded() throws Exception {
        stubFile(fileOf(36L, "STUDENT", 4L, "Hồ sơ.pdf", "application/pdf"));

        mockMvc.perform(get("/api/v1/files/view/36"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("filename*=UTF-8''H%E1%BB%93%20s%C6%A1.pdf")));
    }

    @Test
    void viewFile_nonImageNonPdf_isForcedToAttachment() throws Exception {
        stubFile(fileOf(31L, "COMMON", 1L, "hop-dong.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));

        mockMvc.perform(get("/api/v1/files/view/31"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")));
    }

    @Test
    void viewFile_textFile_isAttachmentWithUtf8TextType() throws Exception {
        stubFile(fileOf(34L, "COMMON", 1L, "note.txt", "text/plain"));

        mockMvc.perform(get("/api/v1/files/view/34"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(header().string("Content-Type", containsString("text/plain")));
    }

    @Test
    void viewFile_legacyHtmlContentType_isServedAsOctetStreamAttachment() throws Exception {
        stubFile(fileOf(32L, "COMMON", 1L, "evil.html", "text/html"));

        mockMvc.perform(get("/api/v1/files/view/32"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(header().string("Content-Type", "application/octet-stream"));
    }

    @Test
    void viewFile_storedTypeNotMatchingExtension_isNotInline() throws Exception {
        stubFile(fileOf(33L, "COMMON", 1L, "logo.svg", "image/png"));

        mockMvc.perform(get("/api/v1/files/view/33"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(header().string("Content-Type", "application/octet-stream"));
    }

    @Test
    void downloadFile_isAttachmentAndAudited() throws Exception {
        stubFile(fileOf(35L, "STUDENT", 9L, "anh.png", "image/png"));

        mockMvc.perform(get("/api/v1/files/download/35"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().longValue("Content-Length", 7L));
        verify(fileAccessPolicy).checkRead(any(FileEntity.class), eq(FileAccessPolicy.Action.DOWNLOAD));
        verify(auditService).success(eq(AuditActions.FILE_DOWNLOADED), eq(AuditActions.RESOURCE_FILE), eq(35L),
                anyMap());
    }

    @Test
    @WithAuthUser(username = "giaovien", roles = {"ROLE_TEACHER"}, permissions = {Permissions.STUDENT_VIEW})
    void downloadFile_policyDenied_returns403AndDoesNotLoadOrAudit() throws Exception {
        when(fileStorageService.getFileEntity(41L)).thenReturn(fileOf(41L, "TUITION", 4L, "a.pdf", "application/pdf"));
        doThrow(new ForbiddenException(FileAccessPolicy.FORBIDDEN_CODE, "x"))
                .when(fileAccessPolicy).checkRead(any(), eq(FileAccessPolicy.Action.DOWNLOAD));

        mockMvc.perform(get("/api/v1/files/download/41"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_FORBIDDEN"));
        verify(fileStorageService, never()).loadFileAsResource(anyLong());
        verify(auditService, never()).success(eq(AuditActions.FILE_DOWNLOADED), anyString(), any(), anyMap());
    }

    @Test
    @WithAuthUser(username = "ketoan", roles = {"ROLE_ACCOUNTANT"}, permissions = {Permissions.CLASS_CREATE})
    void viewFile_withoutAnyViewGatePermission_isForbiddenBeforePolicy() throws Exception {
        mockMvc.perform(get("/api/v1/files/view/1"))
                .andExpect(status().isForbidden());
        verify(fileStorageService, never()).getFileEntity(anyLong());
    }

    @Test
    void viewFile_notFound_is404() throws Exception {
        when(fileStorageService.getFileEntity(99L))
                .thenThrow(new OracleBusinessException("FILE_NOT_FOUND", "khong thay"));

        mockMvc.perform(get("/api/v1/files/view/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
    }

    @Test
    void viewFile_blobMissing_returnsBlobMissingCode() throws Exception {
        // HTTP 400 cho tới khi GlobalExceptionHandler ánh xạ FILE_BLOB_MISSING -> 404 (docs/files-api.md mục 14, #1).
        when(fileStorageService.getFileEntity(98L)).thenReturn(fileOf(98L, "STUDENT", 1L, "a.pdf", "application/pdf"));
        when(fileStorageService.loadFileAsResource(98L))
                .thenThrow(new OracleBusinessException("FILE_BLOB_MISSING", "mat"));

        mockMvc.perform(get("/api/v1/files/view/98"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("FILE_BLOB_MISSING"));
    }

    @Test
    void getByRef_checksListPolicyAndFillsCanDelete() throws Exception {
        FileEntity f = FileEntity.builder().id(1L).originalName("a.txt").contentType("text/plain").fileSize(10L)
                .moduleName("STUDENT").referenceId(99L).build();
        when(fileStorageService.getFilesByRef("STUDENT", 99L)).thenReturn(List.of(f));
        when(fileAccessPolicy.canDelete(f)).thenReturn(false);

        mockMvc.perform(get("/api/v1/files/by-ref").param("moduleName", "STUDENT").param("referenceId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].viewUrl").value("/api/v1/files/view/1"))
                .andExpect(jsonPath("$.data[0].canDelete").value(false));
        verify(fileAccessPolicy).checkList("STUDENT", 99L);
    }

    @Test
    void getByRef_missingReferenceId_returnsValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/files/by-ref").param("moduleName", "STUDENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void delete_softDeletesAndAudits() throws Exception {
        FileEntity f = fileOf(50L, "STUDENT", 3L, "a.pdf", "application/pdf");
        when(fileStorageService.getFileEntity(50L)).thenReturn(f);
        FileEntity deleted = fileOf(50L, "STUDENT", 3L, "a.pdf", "application/pdf");
        deleted.setStatus("DELETED");
        when(fileLifecycleService.softDelete(50L, "trung")).thenReturn(deleted);

        mockMvc.perform(delete("/api/v1/files/50").param("reason", "trung"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(50))
                .andExpect(jsonPath("$.data.canDelete").value(false));
        verify(fileAccessPolicy).checkDelete(f);
        verify(auditService).success(eq(AuditActions.FILE_DELETED), eq(AuditActions.RESOURCE_FILE), eq(50L), anyMap());
    }

    @Test
    void delete_policyDenied_doesNotDelete() throws Exception {
        FileEntity f = fileOf(51L, "STUDENT", 3L, "a.pdf", "application/pdf");
        when(fileStorageService.getFileEntity(51L)).thenReturn(f);
        doThrow(new ForbiddenException("FILE_FORBIDDEN", "x")).when(fileAccessPolicy).checkDelete(f);

        mockMvc.perform(delete("/api/v1/files/51"))
                .andExpect(status().isForbidden());
        verify(fileLifecycleService, never()).softDelete(anyLong(), any());
    }

    @Test
    void delete_reasonTooLong_is400() throws Exception {
        mockMvc.perform(delete("/api/v1/files/52").param("reason", "x".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(fileLifecycleService, never()).softDelete(anyLong(), any());
    }

    @Test
    @WithAuthUser(username = "tuyensinh", roles = {"ROLE_ADMISSION"}, permissions = {Permissions.LEAD_VIEW})
    void delete_withoutWriteGate_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/files/53"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAuthUser(username = "tuyensinh", roles = {"ROLE_ADMISSION"}, permissions = {})
    void policies_anyStaffUser_getsPolicies() throws Exception {
        when(fileLimitService.policies("STUDENT")).thenReturn(List.of(FileUploadPolicyDto.builder()
                .moduleName("STUDENT").maxFileSizeBytes(20971520L).maxFilesPerRef(30)
                .allowedExtensions(List.of("pdf")).inlineExtensions(List.of("pdf")).referenceRequired(true).build()));

        mockMvc.perform(get("/api/v1/files/policies").param("module", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].moduleName").value("STUDENT"))
                .andExpect(jsonPath("$.data[0].maxFileSizeBytes").value(20971520))
                .andExpect(jsonPath("$.data[0].referenceRequired").value(true));
    }

    @Test
    @WithAuthUser(username = "hs", roles = {}, userType = "STUDENT", studentId = 5L)
    void policies_studentAccount_isStaffOnly() throws Exception {
        mockMvc.perform(get("/api/v1/files/policies"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAFF_ONLY"));
    }

    private void stubFile(FileEntity entity) {
        when(fileStorageService.getFileEntity(entity.getId())).thenReturn(entity);
        when(fileStorageService.loadFileAsResource(entity.getId()))
                .thenReturn(new ByteArrayResource("content".getBytes()));
    }

    private static FileEntity fileOf(Long id, String module, Long referenceId, String name, String contentType) {
        return FileEntity.builder().id(id).moduleName(module).referenceId(referenceId)
                .filePath(module + "/2026/10/x.bin").originalName(name).contentType(contentType)
                .fileSize(7L).isDeleted(0).status("ACTIVE").build();
    }
}
