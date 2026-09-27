package com.education.base.controller;

import com.education.base.entity.FileEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapperImpl;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@Import(FileMapperImpl.class)
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
                .andExpect(header().string("Content-Type", containsString("image/png")));
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
