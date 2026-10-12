package com.education.base.service.impl;

import com.education.base.audit.AuditActions;
import com.education.base.config.FileStorageProperties;
import com.education.base.config.FilesProperties;
import com.education.base.entity.FileEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.FileRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.service.AuditService;
import com.education.base.service.file.CommonFileModule;
import com.education.base.service.file.FileLimitService;
import com.education.base.service.file.FileModuleRegistry;
import com.education.base.service.file.FileSettingsSource;
import com.education.base.service.file.StudentFileModule;
import com.education.base.service.file.StudentImportFileModule;
import com.education.base.service.file.TeacherFileScope;
import com.education.base.storage.LocalDiskBlobStore;
import com.education.base.storage.StorageKeyStrategy;
import com.education.base.storage.VirusScanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStorageServiceImplTest {

    @TempDir
    Path tempDir;

    private static final byte[] PDF = "%PDF-1.4 noi dung".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_BYTES = {'P', 'K', 0x03, 0x04, 0x14, 0, 0, 0};

    private FileRepository fileRepository;
    private StudentRepository studentRepository;
    private AuditService auditService;
    private VirusScanner virusScanner;
    private FileStorageServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        fileRepository = mock(FileRepository.class);
        studentRepository = mock(StudentRepository.class);
        auditService = mock(AuditService.class);
        virusScanner = mock(VirusScanner.class);
        when(virusScanner.scan(any(), any())).thenReturn(VirusScanner.ScanResult.skipped());
        FileStorageProperties props = new FileStorageProperties();
        props.setBaseDir(tempDir.toString());
        props.setMinFreeDisk(DataSize.ofBytes(0));
        TeachingAssignmentGuard guard = mock(TeachingAssignmentGuard.class);
        when(guard.taughtClassIdsIfRestricted()).thenReturn(Optional.empty());
        TeacherFileScope scope = new TeacherFileScope(guard, mock(ClassStudentRepository.class), new FilesProperties());
        FileModuleRegistry registry = new FileModuleRegistry(List.of(
                new StudentFileModule(studentRepository, scope), new StudentImportFileModule(), new CommonFileModule()));
        LocalDiskBlobStore blobStore = new LocalDiskBlobStore(props);
        ObjectProvider<FileSettingsSource> none = mock(ObjectProvider.class);
        FileLimitService limits = new FileLimitService(props, none, fileRepository, blobStore, registry);
        service = new FileStorageServiceImpl(fileRepository, blobStore, new StorageKeyStrategy(), registry, limits,
                virusScanner, auditService);
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(inv -> {
            FileEntity e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });
        when(studentRepository.findById(12L)).thenReturn(Optional.of(student(12L, 0)));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                AuthUserPrincipal.builder().id(7L).username("giaovu").role("ROLE_ADMIN").build(), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void storeFile_emptyFile_throwsFileEmpty() {
        assertCode(() -> service.storeFile(new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]),
                "STUDENT", 12L), "FILE_EMPTY");
    }

    @Test
    void storeFile_writesUuidKeyKeepsUnicodeNameAndMetadata() throws Exception {
        FileEntity saved = service.storeFile(new MockMultipartFile("file", "Hồ sơ nhập học.PDF", "x/y", PDF),
                "student", 12L);

        LocalDate today = LocalDate.now();
        String prefix = String.format("STUDENT/%04d/%02d/", today.getYear(), today.getMonthValue());
        assertThat(saved.getFilePath()).startsWith(prefix).matches(".*/[0-9a-f]{32}\\.pdf");
        assertThat(saved.getStoredName()).matches("[0-9a-f]{32}\\.pdf");
        assertThat(saved.getOriginalName()).isEqualTo("Hồ sơ nhập học.PDF");
        assertThat(saved.getModuleName()).isEqualTo("STUDENT");
        assertThat(saved.getContentType()).isEqualTo("application/pdf");
        assertThat(saved.getFileSize()).isEqualTo(PDF.length);
        assertThat(saved.getFileExt()).isEqualTo("pdf");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getScanStatus()).isEqualTo("SKIPPED");
        assertThat(saved.getStorageBackend()).isEqualTo("LOCAL");
        assertThat(saved.getOwnerStudentId()).isEqualTo(12L);
        assertThat(saved.getUploadedByUserId()).isEqualTo(7L);
        assertThat(saved.getUploadedByType()).isEqualTo("STAFF");
        assertThat(saved.getCreatedBy()).isEqualTo("giaovu");
        assertThat(saved.getChecksumSha256())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PDF)));
        assertThat(Files.readAllBytes(tempDir.resolve(saved.getFilePath()))).isEqualTo(PDF);
        verify(auditService).success(eq(AuditActions.FILE_UPLOADED), eq(AuditActions.RESOURCE_FILE), eq(99L), anyMap());
    }

    @Test
    void storeBytes_importSubFolder_usesStudentImportRulesWithoutReference() {
        FileEntity saved = service.storeBytes(ZIP_BYTES, "danh sách.xlsx", "x", "STUDENT", null, "IMPORT");

        assertThat(saved.getFilePath()).startsWith("STUDENT/IMPORT/").endsWith(".xlsx");
        assertThat(saved.getModuleName()).isEqualTo("STUDENT");
        assertThat(saved.getReferenceId()).isNull();
        assertThat(saved.getOwnerStudentId()).isNull();
    }

    @Test
    void storeBytes_importSubFolder_rejectsNonXlsx() {
        assertCode(() -> service.storeBytes(PDF, "a.pdf", "x", "STUDENT", null, "IMPORT"), "FILE_TYPE_NOT_ALLOWED");
    }

    @Test
    void storeFile_unregisteredModule_isRejectedFailClosed() {
        assertCode(() -> service.storeFile(pdf(), "PAYROLL", 1L), "INVALID_MODULE");
        verify(fileRepository, never()).save(any());
    }

    @Test
    void storeFile_referenceRequired() {
        assertCode(() -> service.storeFile(pdf(), "STUDENT", null), "FILE_REFERENCE_REQUIRED");
    }

    @Test
    void storeFile_referenceNotFound() {
        when(studentRepository.findById(404L)).thenReturn(Optional.empty());
        assertCode(() -> service.storeFile(pdf(), "STUDENT", 404L), "FILE_REFERENCE_NOT_FOUND");
    }

    @Test
    void storeFile_referenceSoftDeleted_isInactive() {
        when(studentRepository.findById(13L)).thenReturn(Optional.of(student(13L, 1)));
        assertCode(() -> service.storeFile(pdf(), "STUDENT", 13L), "FILE_REFERENCE_INACTIVE");
        verify(auditService).failure(eq(AuditActions.FILE_UPLOAD_REJECTED), eq(AuditActions.RESOURCE_FILE), isNull(),
                anyMap());
    }

    @ParameterizedTest
    @CsvSource({
            "logo.svg, <svg/>, FILE_TYPE_NOT_ALLOWED",
            "virus.exe, MZ, FILE_TYPE_NOT_ALLOWED",
            "fake.pdf, hello, FILE_CONTENT_MISMATCH",
            "noext, abc, FILE_TYPE_NOT_ALLOWED"
    })
    void storeFile_rejectsDisallowedOrMismatchedTypes(String name, String content, String code) {
        assertCode(() -> service.storeFile(new MockMultipartFile("file", name, "application/pdf",
                content.getBytes()), "STUDENT", 12L), code);
        assertThat(blobs()).isEmpty();
    }

    @Test
    void storeFile_tooLargeForModule() {
        byte[] big = new byte[20 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        assertCode(() -> service.storeFile(new MockMultipartFile("file", "a.pdf", "x", big), "STUDENT", 12L),
                "FILE_TOO_LARGE");
    }

    @Test
    void storeFile_countLimitPerReference() {
        when(fileRepository.countByModuleNameAndReferenceIdAndStatusIn(eq("STUDENT"), eq(12L), any())).thenReturn(30L);
        assertCode(() -> service.storeFile(pdf(), "STUDENT", 12L), "FILE_LIMIT_COUNT");
    }

    @Test
    void storeFile_databaseFailsWithoutTransaction_removesBlob() {
        when(fileRepository.save(any(FileEntity.class))).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> service.storeFile(pdf(), "STUDENT", 12L))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(blobs()).isEmpty();
    }

    @Test
    void storeFile_transactionRollback_removesBlob_commitKeepsIt() {
        TransactionSynchronizationManager.initSynchronization();
        FileEntity first = service.storeFile(pdf(), "STUDENT", 12L);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(Files.exists(tempDir.resolve(first.getFilePath()))).isFalse();

        TransactionSynchronizationManager.initSynchronization();
        FileEntity second = service.storeFile(pdf(), "STUDENT", 12L);
        complete(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(Files.exists(tempDir.resolve(second.getFilePath()))).isTrue();
    }

    @Test
    void storeFile_infected_isQuarantinedAndRejected() throws Exception {
        when(virusScanner.scan(any(), any())).thenReturn(new VirusScanner.ScanResult("INFECTED", "Eicar"));

        assertCode(() -> service.storeFile(pdf(), "STUDENT", 12L), "FILE_INFECTED");
        assertThat(blobs()).isEmpty();
        assertThat(Files.exists(tempDir.resolve(".quarantine"))).isTrue();
        verify(fileRepository, never()).save(any());
        verify(auditService).denied(eq(AuditActions.FILE_INFECTED), anyString(), any(), anyMap());
    }

    @Test
    void getFileEntity_nonActive_throwsNotFound() {
        when(fileRepository.findById(1L)).thenReturn(Optional.of(FileEntity.builder().id(1L).isDeleted(0)
                .status("PENDING").build()));
        when(fileRepository.findById(2L)).thenReturn(Optional.of(FileEntity.builder().id(2L).isDeleted(1).build()));
        when(fileRepository.findById(3L)).thenReturn(Optional.empty());
        when(fileRepository.findById(4L)).thenReturn(Optional.of(FileEntity.builder().id(4L).isDeleted(0).build()));

        assertCode(() -> service.getFileEntity(1L), "FILE_NOT_FOUND");
        assertCode(() -> service.getFileEntity(2L), "FILE_NOT_FOUND");
        assertCode(() -> service.getFileEntity(3L), "FILE_NOT_FOUND");
        assertThat(service.getFileEntity(4L).getId()).isEqualTo(4L);
    }

    @Test
    void loadFileAsResource_rejectsPathTraversal() {
        stubActive(5L, "../../etc/passwd");
        assertCode(() -> service.loadFileAsResource(5L), "FILE_PATH_ERROR");
    }

    @Test
    void loadFileAsResource_missingBlob_throwsBlobMissing() {
        stubActive(6L, "STUDENT/2026/09/missing.pdf");
        assertCode(() -> service.loadFileAsResource(6L), "FILE_BLOB_MISSING");
    }

    @Test
    void loadFileAsResource_legacyPath_returnsReadableResource() throws Exception {
        Path legacy = tempDir.resolve("STUDENT/2026/09/abc_ho-so.pdf");
        Files.createDirectories(legacy.getParent());
        Files.write(legacy, PDF);
        stubActive(7L, "STUDENT/2026/09/abc_ho-so.pdf");

        Resource r = service.loadFileAsResource(7L);
        assertThat(r.isReadable()).isTrue();
        assertThat(r.getContentAsByteArray()).isEqualTo(PDF);
    }

    @Test
    void loadResource_purged_isNotFound() {
        assertCode(() -> service.loadResource(FileEntity.builder().id(8L).status("PURGED").filePath("a/b.pdf").build()),
                "FILE_NOT_FOUND");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Hồ sơ.pdf|Hồ sơ.pdf",
            "../../evil.txt|evil.txt",
            "C:\\Users\\a\\Bảng điểm.xlsx|Bảng điểm.xlsx",
            "'  '|unnamed",
            "...|unnamed",
            "a\u0000b\u202E.txt|ab.txt"
    })
    void cleanOriginalName_keepsUnicodeAndStripsUnsafeParts(String in, String expected) {
        assertThat(FileStorageServiceImpl.cleanOriginalName(in)).isEqualTo(expected);
    }

    @Test
    void cleanOriginalName_decomposedVietnamese_isNfcNormalized() {
        String nfd = java.text.Normalizer.normalize("Hồ sơ.pdf", java.text.Normalizer.Form.NFD);
        assertThat(FileStorageServiceImpl.cleanOriginalName(nfd)).isEqualTo("Hồ sơ.pdf");
    }

    @Test
    void cleanOriginalName_truncatesTo255KeepingExtension() {
        String name = FileStorageServiceImpl.cleanOriginalName("ả".repeat(300) + ".pdf");
        assertThat(name).hasSize(255).endsWith(".pdf");
    }

    @Test
    void getFilesByRef_normalizesModuleAndDelegatesToProcedure() {
        when(fileRepository.getFilesByRef("CLASS", 3L)).thenReturn(List.of());
        assertThat(service.getFilesByRef(" class ", 3L)).isEmpty();
        verify(fileRepository).getFilesByRef("CLASS", 3L);
    }

    // ---------------------------------------------------------------------------------------------

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "a.pdf", "application/pdf", PDF);
    }

    private static StudentEntity student(Long id, int deleted) {
        StudentEntity s = new StudentEntity();
        s.setId(id);
        s.setIsDeleted(deleted);
        s.setStudentCode("HS" + id);
        s.setFullName("Nguyễn Văn A");
        return s;
    }

    private void stubActive(Long id, String path) {
        when(fileRepository.findById(id)).thenReturn(Optional.of(FileEntity.builder().id(id).isDeleted(0)
                .status("ACTIVE").filePath(path).build()));
    }

    private static void complete(int status) {
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        syncs.forEach(s -> s.afterCompletion(status));
    }

    private List<Path> blobs() {
        try (Stream<Path> s = Files.walk(tempDir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> !tempDir.relativize(p).toString().startsWith("."))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo(code);
    }
}
