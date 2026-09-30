package com.education.base.service.impl;

import com.education.base.config.FileStorageProperties;
import com.education.base.entity.FileEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceImplTest {

    @TempDir
    Path tempDir;

    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    private static final byte[] ZIP_BYTES = {'P', 'K', 0x03, 0x04, 0x14, 0, 0, 0};

    @Mock
    private FileRepository fileRepository;

    private FileStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBaseDir(tempDir.toString());
        service = new FileStorageServiceImpl(fileRepository, properties);
    }

    @Test
    void storeFile_emptyFile_throwsBusinessException() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.storeFile(empty, "STUDENT", 1L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("FILE_EMPTY");
    }

    @Test
    void storeFile_savesPhysicalFileAndRelativePathOnly() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "ho-so.pdf", "application/pdf", "%PDF-1.4 noi dung".getBytes());
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity entity = invocation.getArgument(0);
            entity.setId(99L);
            return entity;
        });

        FileEntity saved = service.storeFile(upload, "student", 12L);

        LocalDate today = LocalDate.now();
        String expectedPrefix = "STUDENT/"
                + today.format(DateTimeFormatter.ofPattern("yyyy")) + "/"
                + today.format(DateTimeFormatter.ofPattern("MM")) + "/";

        assertThat(saved.getId()).isEqualTo(99L);
        assertThat(saved.getModuleName()).isEqualTo("STUDENT");
        assertThat(saved.getReferenceId()).isEqualTo(12L);
        assertThat(saved.getContentType()).isEqualTo("application/pdf");
        assertThat(saved.getFileSize()).isEqualTo("%PDF-1.4 noi dung".getBytes().length);
        assertThat(saved.getIsDeleted()).isZero();
        assertThat(saved.getStoredName()).endsWith("_ho-so.pdf");
        assertThat(saved.getFilePath())
                .startsWith(expectedPrefix)
                .endsWith("_ho-so.pdf")
                .doesNotContain("\\")
                .doesNotContain(tempDir.toAbsolutePath().toString());

        Path physical = tempDir.resolve(saved.getFilePath()).normalize();
        assertThat(physical).startsWith(tempDir.toAbsolutePath().normalize());
        assertThat(Files.readString(physical)).isEqualTo("%PDF-1.4 noi dung");
    }

    @Test
    void storeFile_withSubFolder_usesModuleSubFolderYearMonth() {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "ds.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                ZIP_BYTES);
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileEntity saved = service.storeFile(upload, "STUDENT", null, "IMPORT");

        LocalDate today = LocalDate.now();
        String expectedPrefix = "STUDENT/IMPORT/"
                + today.format(DateTimeFormatter.ofPattern("yyyy")) + "/"
                + today.format(DateTimeFormatter.ofPattern("MM")) + "/";
        assertThat(saved.getFilePath()).startsWith(expectedPrefix);
        assertThat(Files.isRegularFile(tempDir.resolve(saved.getFilePath()))).isTrue();
    }

    @Test
    void storeFile_stripsDirectoryTraversalFromOriginalName() {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "../../../evil.txt", "text/plain", "x".getBytes());
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileEntity saved = service.storeFile(upload, "COMMON", null);

        assertThat(saved.getOriginalName()).isEqualTo("evil.txt");
        assertThat(saved.getFilePath()).doesNotContain("..");
    }

    @Test
    void storeFile_sanitizesUnsafeCharactersInFileName() {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "hồ sơ (1).pdf", "application/pdf", "%PDF-x".getBytes());
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileEntity saved = service.storeFile(upload, "STUDENT", 1L);

        assertThat(saved.getOriginalName()).matches("[a-zA-Z0-9._-]+");
        assertThat(saved.getOriginalName()).endsWith(".pdf");
    }

    @Test
    void storeFile_blankModuleAndMissingContentType_useDefaultModuleAndDetectedType() {
        MockMultipartFile upload = new MockMultipartFile("file", "a.txt", null, "x".getBytes());
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileEntity saved = service.storeFile(upload, "  ", null);

        assertThat(saved.getModuleName()).isEqualTo("COMMON");
        assertThat(saved.getFilePath()).startsWith("COMMON/");
        assertThat(saved.getContentType()).isEqualTo("text/plain");
    }

    @Test
    void storeFile_storesServerDetectedContentTypeNotClientValue() {
        MockMultipartFile upload = new MockMultipartFile("file", "anh.png", "text/html", PNG_BYTES);
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileEntity saved = service.storeFile(upload, "STUDENT", 1L);

        assertThat(saved.getContentType()).isEqualTo("image/png");
    }

    @ParameterizedTest
    @CsvSource({
            "logo.svg, image/svg+xml, <svg xmlns=x><script>alert(1)</script></svg>, FILE_TYPE_NOT_ALLOWED",
            "trang.html, text/html, <html><script>alert(1)</script></html>, FILE_TYPE_NOT_ALLOWED",
            "trang.htm, text/html, <p>x</p>, FILE_TYPE_NOT_ALLOWED",
            "virus.exe, application/octet-stream, MZ, FILE_TYPE_NOT_ALLOWED",
            "khong-duoi, application/pdf, %PDF-1.4, FILE_TYPE_NOT_ALLOWED",
            "a.bin, application/octet-stream, x, FILE_TYPE_NOT_ALLOWED",
            "ghi-chu.txt, text/plain, <!DOCTYPE html><html></html>, FILE_CONTENT_MISMATCH",
            "bang.csv, text/csv, <svg onload=alert(1)>, FILE_CONTENT_MISMATCH",
            "gia-anh.png, image/png, <html>not a png</html>, FILE_CONTENT_MISMATCH",
            "gia.pdf, application/pdf, <html>fake</html>, FILE_CONTENT_MISMATCH",
            "gia.jpg, image/jpeg, GIF89a..., FILE_CONTENT_MISMATCH",
            "gia.docx, application/msword, %PDF-1.4, FILE_CONTENT_MISMATCH"
    })
    void storeFile_rejectsDisallowedOrMismatchedTypes(String name, String clientType, String content, String code) {
        MockMultipartFile upload = new MockMultipartFile("file", name, clientType,
                content.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.storeFile(upload, "COMMON", 1L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo(code);
        verify(fileRepository, never()).save(any());
        assertThat(tempDir.toFile().listFiles()).isEmpty();
    }

    @Test
    void storeFile_rejectsBinaryContentInTextFile() {
        MockMultipartFile upload = new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{'a', 0, 'b'});

        assertThatThrownBy(() -> service.storeFile(upload, "COMMON", 1L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("FILE_CONTENT_MISMATCH");
    }

    @Test
    void storeBytes_rejectsHtmlDisguisedAsXlsx() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.storeBytes(html, "ds.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "STUDENT", null, "IMPORT"))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("FILE_CONTENT_MISMATCH");
        verify(fileRepository, never()).save(any());
    }

    @Test
    void storeFile_databaseFails_removesOrphanPhysicalFile() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "bang-diem.txt", "text/plain", "x".getBytes());
        when(fileRepository.save(any(FileEntity.class)))
                .thenThrow(new DataIntegrityViolationException("ORA-00001"));

        assertThatThrownBy(() -> service.storeFile(upload, "STUDENT", 1L))
                .isInstanceOf(DataIntegrityViolationException.class);

        try (var stream = Files.walk(tempDir)) {
            assertThat(stream.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void getFileEntity_missingOrSoftDeleted_throwsNotFound() {
        when(fileRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getFileEntity(1L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("FILE_NOT_FOUND");

        when(fileRepository.findById(2L))
                .thenReturn(Optional.of(FileEntity.builder().id(2L).isDeleted(1).build()));
        assertThatThrownBy(() -> service.getFileEntity(2L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("FILE_NOT_FOUND");
    }

    @Test
    void storeFile_moduleWithPathSeparator_throwsInvalidModule() {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "a.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> service.storeFile(upload, "../STUDENT", 1L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("INVALID_MODULE");
    }

    @Test
    void loadFileAsResource_rejectsPathTraversal() {
        when(fileRepository.findById(5L)).thenReturn(Optional.of(FileEntity.builder()
                .id(5L)
                .filePath("../../secret.txt")
                .isDeleted(0)
                .build()));

        assertThatThrownBy(() -> service.loadFileAsResource(5L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("PATH_TRAVERSAL_DETECTED");
    }

    @Test
    void loadFileAsResource_missingPhysicalFile_throwsNotFound() {
        when(fileRepository.findById(6L)).thenReturn(Optional.of(FileEntity.builder()
                .id(6L)
                .filePath("STUDENT/2026/09/khong-ton-tai.pdf")
                .isDeleted(0)
                .build()));

        assertThatThrownBy(() -> service.loadFileAsResource(6L))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("FILE_NOT_FOUND");
    }

    @Test
    void loadFileAsResource_returnsReadableResource() throws Exception {
        String relative = "STUDENT/2026/09/sample.txt";
        Path target = tempDir.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, "hello");

        when(fileRepository.findById(7L)).thenReturn(Optional.of(FileEntity.builder()
                .id(7L)
                .filePath(relative)
                .isDeleted(0)
                .build()));

        Resource resource = service.loadFileAsResource(7L);

        assertThat(resource.exists()).isTrue();
        assertThat(resource.getContentAsByteArray()).isEqualTo("hello".getBytes());
    }

    @Test
    void getFilesByRef_normalizesModuleAndDelegatesToProcedure() {
        FileEntity entity = FileEntity.builder().id(1L).moduleName("STUDENT").build();
        when(fileRepository.getFilesByRef("STUDENT", 10L)).thenReturn(List.of(entity));

        assertThat(service.getFilesByRef("student", 10L)).containsExactly(entity);
        verify(fileRepository).getFilesByRef("STUDENT", 10L);
    }
}
