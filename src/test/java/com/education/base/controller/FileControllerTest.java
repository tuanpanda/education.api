package com.education.base.controller;

import com.education.base.support.WebMvcSecurityTestConfig;
import com.education.base.support.WithAuthUser;
import com.education.base.entity.FileEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapperImpl;
import com.education.base.security.FileAccessPolicy;
import com.education.base.security.Permissions;
import com.education.base.service.FileStorageService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@Import({WebMvcSecurityTestConfig.class, FileMapperImpl.class, FileAccessPolicy.class})
@WithAuthUser
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileStorageService fileStorageService;

    @Test
    void upload_returnsMetadataWithViewAndDownloadUrls() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "ho-so.pdf", "application/pdf", "abc".getBytes());

        when(fileStorageService.storeFile(any(), eq("STUDENT"), eq(15L))).thenReturn(FileEntity.builder()
                .id(8L)
                .originalName("ho-so.pdf")
                .contentType("application/pdf")
                .fileSize(3L)
                .moduleName("STUDENT")
                .referenceId(15L)
                .createdAt(LocalDateTime.of(2026, 9, 16, 10, 0))
                .build());

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(file)
                        .param("moduleName", "STUDENT")
                        .param("referenceId", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.id").value(8))
                .andExpect(jsonPath("$.data.originalName").value("ho-so.pdf"))
                .andExpect(jsonPath("$.data.moduleName").value("STUDENT"))
                .andExpect(jsonPath("$.data.viewUrl").value("/api/v1/files/view/8"))
                .andExpect(jsonPath("$.data.downloadUrl").value("/api/v1/files/download/8"));
    }

    @Test
    void viewFile_usesInlineContentDisposition() throws Exception {
        when(fileStorageService.getFileEntity(3L)).thenReturn(FileEntity.builder()
                .id(3L)
                .originalName("anh-the.png")
                .contentType("image/png")
                .build());
        when(fileStorageService.loadFileAsResource(3L)).thenReturn(new ByteArrayResource("png".getBytes()));

        mockMvc.perform(get("/api/v1/files/view/3"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Content-Type", containsString("image/png")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"));
    }

    @Test
    void viewFile_pdf_isInlineWithSecurityHeaders() throws Exception {
        stubFile(fileOf(30L, "TUITION", 4L, "TUITION/2026/09/x_phieu.pdf", "phieu.pdf", "application/pdf"));

        mockMvc.perform(get("/api/v1/files/view/30"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("inline;")))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"));
    }

    @Test
    void viewFile_nonImageNonPdf_isForcedToAttachment() throws Exception {
        stubFile(fileOf(31L, "COMMON", 1L, "COMMON/2026/09/x_hop-dong.docx", "hop-dong.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));

        mockMvc.perform(get("/api/v1/files/view/31"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"));
    }

    @Test
    void viewFile_textFile_isAttachmentWithUtf8TextType() throws Exception {
        stubFile(fileOf(34L, "COMMON", 1L, "COMMON/2026/09/x_note.txt", "note.txt", "text/plain"));

        mockMvc.perform(get("/api/v1/files/view/34"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(header().string("Content-Type", containsString("text/plain")));
    }

    @Test
    void viewFile_legacyHtmlContentType_isServedAsOctetStreamAttachment() throws Exception {
        stubFile(fileOf(32L, "COMMON", 1L, "COMMON/2026/09/x_evil.html", "evil.html", "text/html"));

        mockMvc.perform(get("/api/v1/files/view/32"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"));
    }

    @Test
    void viewFile_storedTypeNotMatchingExtension_isNotInline() throws Exception {
        // CONTENT_TYPE cũ do client khai báo image/png nhưng tên file là .svg -> không tin, trả octet-stream.
        stubFile(fileOf(33L, "COMMON", 1L, "COMMON/2026/09/x_logo.svg", "logo.svg", "image/png"));

        mockMvc.perform(get("/api/v1/files/view/33"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(header().string("Content-Type", "application/octet-stream"));
    }

    @Test
    void downloadFile_imageIsStillAttachmentWithSecurityHeaders() throws Exception {
        stubFile(fileOf(35L, "STUDENT", 9L, "STUDENT/2026/09/x_anh.png", "anh.png", "image/png"));

        mockMvc.perform(get("/api/v1/files/download/35"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"));
    }

    // ---- Phân quyền theo module --------------------------------------------------------------

    @Test
    @WithAuthUser(username = "ketoan", roles = {"ROLE_ACCOUNTANT"}, permissions = {Permissions.TUITION_FEE_VIEW})
    void viewFile_tuitionFile_allowedWithTuitionViewPermission() throws Exception {
        stubFile(fileOf(40L, "TUITION", 4L, "TUITION/2026/09/x_bien-lai.pdf", "bien-lai.pdf", "application/pdf"));

        mockMvc.perform(get("/api/v1/files/view/40"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAuthUser(username = "giaovien", roles = {"ROLE_TEACHER"}, permissions = {Permissions.STUDENT_VIEW})
    void viewFile_tuitionFile_forbiddenWithOnlyStudentView() throws Exception {
        when(fileStorageService.getFileEntity(41L)).thenReturn(
                fileOf(41L, "TUITION", 4L, "TUITION/2026/09/x_bien-lai.pdf", "bien-lai.pdf", "application/pdf"));

        mockMvc.perform(get("/api/v1/files/view/41"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FileAccessPolicy.FORBIDDEN_CODE));
        verify(fileStorageService, never()).loadFileAsResource(anyLong());
    }

    @Test
    @WithAuthUser(username = "giaovien", roles = {"ROLE_TEACHER"},
            permissions = {Permissions.STUDENT_VIEW, Permissions.FILE_VIEW, Permissions.FILE_DOWNLOAD})
    void downloadFile_tuitionFile_forbiddenEvenWithFileExplorerPermissions() throws Exception {
        when(fileStorageService.getFileEntity(42L)).thenReturn(
                fileOf(42L, "TUITION", 4L, "TUITION/2026/09/x_bien-lai.pdf", "bien-lai.pdf", "application/pdf"));

        mockMvc.perform(get("/api/v1/files/download/42"))
                .andExpect(status().isForbidden());
        verify(fileStorageService, never()).loadFileAsResource(anyLong());
    }

    @Test
    @WithAuthUser(username = "giaovien", roles = {"ROLE_TEACHER"}, permissions = {Permissions.STUDENT_VIEW})
    void viewFile_studentFile_allowedWithStudentView() throws Exception {
        stubFile(fileOf(43L, "STUDENT", 9L, "STUDENT/2026/09/x_anh.jpg", "anh.jpg", "image/jpeg"));

        mockMvc.perform(get("/api/v1/files/view/43"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("inline;")));
    }

    @Test
    @WithAuthUser(username = "ketoan", roles = {"ROLE_ACCOUNTANT"},
            permissions = {Permissions.TUITION_FEE_VIEW, Permissions.FILE_VIEW})
    void viewFile_studentFile_forbiddenWithoutStudentView() throws Exception {
        when(fileStorageService.getFileEntity(44L)).thenReturn(
                fileOf(44L, "STUDENT", 9L, "STUDENT/2026/09/x_anh.jpg", "anh.jpg", "image/jpeg"));

        mockMvc.perform(get("/api/v1/files/view/44"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAuthUser(username = "giaovien", roles = {"ROLE_TEACHER"}, permissions = {Permissions.STUDENT_VIEW})
    void downloadFile_studentImportFile_forbiddenWithOnlyStudentView() throws Exception {
        when(fileStorageService.getFileEntity(45L)).thenReturn(fileOf(45L, "STUDENT", null,
                "STUDENT/IMPORT/2026/09/x_ds.xlsx", "ds.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        mockMvc.perform(get("/api/v1/files/download/45"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAuthUser(username = "tuyensinh", roles = {"ROLE_ADMISSION"},
            permissions = {Permissions.STUDENT_VIEW, Permissions.STUDENT_IMPORT})
    void downloadFile_studentImportFile_allowedWithImportPermission() throws Exception {
        stubFile(fileOf(46L, "STUDENT", null, "STUDENT/IMPORT/2026/09/x_ds.xlsx", "ds.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        mockMvc.perform(get("/api/v1/files/download/46"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")));
    }

    @Test
    @WithAuthUser(username = "tuyensinh", roles = {"ROLE_ADMISSION"}, permissions = {Permissions.STUDENT_CREATE})
    void downloadFile_studentImportFile_allowedWithStudentCreate() throws Exception {
        stubFile(fileOf(47L, "STUDENT", null, "STUDENT/IMPORT/2026/09/x_ds.xlsx", "ds.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        mockMvc.perform(get("/api/v1/files/download/47"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAuthUser(username = "giaovien", roles = {"ROLE_TEACHER"}, permissions = {Permissions.FILE_VIEW})
    void commonFile_viewNeedsFileView_downloadNeedsFileDownload() throws Exception {
        stubFile(fileOf(48L, "COMMON", 1L, "COMMON/2026/09/x_tai-lieu.pdf", "tai-lieu.pdf", "application/pdf"));

        mockMvc.perform(get("/api/v1/files/view/48"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/files/download/48"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAuthUser(username = "giaovien", roles = {"ROLE_TEACHER"}, permissions = {Permissions.STUDENT_VIEW})
    void getByRef_tuitionModule_forbiddenWithOnlyStudentView() throws Exception {
        mockMvc.perform(get("/api/v1/files/by-ref")
                        .param("moduleName", "tuition")
                        .param("referenceId", "4"))
                .andExpect(status().isForbidden());
        verify(fileStorageService, never()).getFilesByRef(anyString(), anyLong());
    }

    @Test
    @WithAuthUser(username = "ketoan", roles = {"ROLE_ACCOUNTANT"}, permissions = {Permissions.TUITION_FEE_VIEW})
    void getByRef_tuitionModule_allowedWithTuitionView() throws Exception {
        when(fileStorageService.getFilesByRef("TUITION", 4L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/files/by-ref")
                        .param("moduleName", "TUITION")
                        .param("referenceId", "4"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAuthUser(username = "giaovien", roles = {"ROLE_TEACHER"}, permissions = {Permissions.FILE_UPLOAD})
    void upload_studentModule_forbiddenWithOnlyFileUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.4".getBytes()))
                        .param("moduleName", "STUDENT")
                        .param("referenceId", "15"))
                .andExpect(status().isForbidden());
        verify(fileStorageService, never()).storeFile(any(), anyString(), any());
    }

    @Test
    @WithAuthUser(username = "giaovien", roles = {"ROLE_TEACHER"}, permissions = {Permissions.FILE_UPLOAD})
    void upload_commonModule_allowedWithFileUpload() throws Exception {
        when(fileStorageService.storeFile(any(), eq("COMMON"), eq(1L))).thenReturn(
                fileOf(50L, "COMMON", 1L, "COMMON/2026/09/x_a.pdf", "a.pdf", "application/pdf"));

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.4".getBytes()))
                        .param("moduleName", "COMMON")
                        .param("referenceId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAuthUser(username = "ketoan", roles = {"ROLE_ACCOUNTANT"}, permissions = {Permissions.TUITION_FEE_UPDATE})
    void upload_tuitionModule_withoutReferenceId_isRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.4".getBytes()))
                        .param("moduleName", "TUITION"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_REFERENCE_REQUIRED"));
    }

    @Test
    void upload_unknownModule_isRejectedEvenForAdmin() throws Exception {
        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.4".getBytes()))
                        .param("moduleName", "PAYROLL")
                        .param("referenceId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MODULE"));
    }

    @Test
    void upload_disallowedType_returnsBusinessError() throws Exception {
        when(fileStorageService.storeFile(any(), eq("COMMON"), eq(1L))).thenThrow(
                new OracleBusinessException("FILE_TYPE_NOT_ALLOWED", "Không cho phép tải lên file .svg"));

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "logo.svg", "image/svg+xml", "<svg/>".getBytes()))
                        .param("moduleName", "COMMON")
                        .param("referenceId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));
    }

    private void stubFile(FileEntity entity) {
        when(fileStorageService.getFileEntity(entity.getId())).thenReturn(entity);
        when(fileStorageService.loadFileAsResource(entity.getId()))
                .thenReturn(new ByteArrayResource("content".getBytes()));
    }

    private static FileEntity fileOf(Long id, String module, Long referenceId, String path,
                                     String name, String contentType) {
        return FileEntity.builder()
                .id(id)
                .moduleName(module)
                .referenceId(referenceId)
                .filePath(path)
                .originalName(name)
                .contentType(contentType)
                .fileSize(7L)
                .isDeleted(0)
                .build();
    }

    @Test
    void downloadFile_usesAttachmentContentDispositionAndLength() throws Exception {
        when(fileStorageService.getFileEntity(4L)).thenReturn(FileEntity.builder()
                .id(4L)
                .originalName("tai-lieu.pdf")
                .contentType("application/pdf")
                .fileSize(3L)
                .build());
        when(fileStorageService.loadFileAsResource(4L)).thenReturn(new ByteArrayResource("pdf".getBytes()));

        mockMvc.perform(get("/api/v1/files/download/4"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().longValue("Content-Length", 3L));
    }

    @Test
    void getByRef_returnsListFromProcedure() throws Exception {
        when(fileStorageService.getFilesByRef("STUDENT", 99L)).thenReturn(List.of(FileEntity.builder()
                .id(1L)
                .originalName("a.txt")
                .contentType("text/plain")
                .fileSize(10L)
                .moduleName("STUDENT")
                .referenceId(99L)
                .build()));

        mockMvc.perform(get("/api/v1/files/by-ref")
                        .param("moduleName", "STUDENT")
                        .param("referenceId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].viewUrl").value("/api/v1/files/view/1"))
                .andExpect(jsonPath("$.data[0].downloadUrl").value("/api/v1/files/download/1"));
    }

    @Test
    void getByRef_missingReferenceId_returnsValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/files/by-ref").param("moduleName", "STUDENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void viewFile_notFound_isHandledByGlobalExceptionHandler() throws Exception {
        when(fileStorageService.getFileEntity(99L))
                .thenThrow(new OracleBusinessException("FILE_NOT_FOUND", "Không tìm thấy thông tin file với ID: 99"));

        mockMvc.perform(get("/api/v1/files/view/99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
    }
}
