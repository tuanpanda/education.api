package com.education.base.security;

import com.education.base.audit.AuditActions;
import com.education.base.config.FileStorageProperties;
import com.education.base.config.FilesProperties;
import com.education.base.dto.response.FileModuleSettingDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.FileEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.FileRepository;
import com.education.base.repository.LeadRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.service.AuditService;
import com.education.base.service.file.ClassFileModule;
import com.education.base.service.file.CommonFileModule;
import com.education.base.service.file.FileLimitService;
import com.education.base.service.file.FileModuleRegistry;
import com.education.base.service.file.FileSettingsSource;
import com.education.base.service.file.LeadFileModule;
import com.education.base.service.file.StudentFileModule;
import com.education.base.service.file.StudentImportFileModule;
import com.education.base.service.file.TeacherFileScope;
import com.education.base.service.file.TuitionFileModule;
import com.education.base.service.impl.TeachingAssignmentGuard;
import com.education.base.storage.BlobStore;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ma trận vai trò × module × trạng thái × phạm vi giáo viên (kế hoạch 4.5). */
class FileAccessPolicyTest {

    private static final Long TAUGHT_CLASS = 10L;
    private static final Long OTHER_CLASS = 20L;
    private static final Long STUDENT_IN_TAUGHT = 1L;
    private static final Long STUDENT_ELSEWHERE = 2L;
    private static final Long STUDENT_DELETED = 3L;

    private StudentRepository studentRepository;
    private ClassRepository classRepository;
    private TuitionFeeRepository tuitionFeeRepository;
    private TeachingAssignmentGuard guard;
    private AuditService auditService;
    private FilesProperties filesProperties;
    private FileStorageProperties storageProperties;
    private FileSettingsSource settingsSource;
    private FileAccessPolicy policy;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        studentRepository = mock(StudentRepository.class);
        classRepository = mock(ClassRepository.class);
        tuitionFeeRepository = mock(TuitionFeeRepository.class);
        ClassStudentRepository classStudentRepository = mock(ClassStudentRepository.class);
        guard = mock(TeachingAssignmentGuard.class);
        when(guard.taughtClassIdsIfRestricted()).thenReturn(Optional.empty());
        auditService = mock(AuditService.class);
        filesProperties = new FilesProperties();
        storageProperties = new FileStorageProperties();
        settingsSource = mock(FileSettingsSource.class);
        when(settingsSource.moduleSetting(anyString())).thenReturn(Optional.empty());
        ObjectProvider<FileSettingsSource> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(settingsSource);

        TeacherFileScope scope = new TeacherFileScope(guard, classStudentRepository, filesProperties);
        FileModuleRegistry registry = new FileModuleRegistry(List.of(
                new StudentFileModule(studentRepository, scope),
                new StudentImportFileModule(),
                new ClassFileModule(classRepository, scope),
                new LeadFileModule(mock(LeadRepository.class)),
                new TuitionFileModule(tuitionFeeRepository),
                new CommonFileModule()));
        FileLimitService limits = new FileLimitService(storageProperties, provider, mock(FileRepository.class),
                mock(BlobStore.class), registry);
        policy = new FileAccessPolicy(registry, limits, storageProperties, auditService);

        when(studentRepository.findById(STUDENT_IN_TAUGHT)).thenReturn(Optional.of(student(STUDENT_IN_TAUGHT, 0)));
        when(studentRepository.findById(STUDENT_ELSEWHERE)).thenReturn(Optional.of(student(STUDENT_ELSEWHERE, 0)));
        when(studentRepository.findById(STUDENT_DELETED)).thenReturn(Optional.of(student(STUDENT_DELETED, 1)));
        when(classRepository.findById(TAUGHT_CLASS)).thenReturn(Optional.of(clazz(TAUGHT_CLASS)));
        when(classRepository.findById(OTHER_CLASS)).thenReturn(Optional.of(clazz(OTHER_CLASS)));
        TuitionFeeEntity fee = new TuitionFeeEntity();
        fee.setId(4L);
        fee.setIsDeleted(0);
        fee.setFeeCode("HP4");
        when(tuitionFeeRepository.findById(4L)).thenReturn(Optional.of(fee));
        when(classStudentRepository.findByStudentIdAndIsDeleted(STUDENT_IN_TAUGHT, 0))
                .thenReturn(List.of(enrollment(TAUGHT_CLASS)));
        when(classStudentRepository.findByStudentIdAndIsDeleted(STUDENT_ELSEWHERE, 0))
                .thenReturn(List.of(enrollment(OTHER_CLASS)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---- Đọc ---------------------------------------------------------------------------------

    @Test
    void admin_readsEverything_includingDeletedReference() {
        login("admin", List.of("ROLE_ADMIN"));
        assertOk(() -> policy.checkRead(file("STUDENT", STUDENT_DELETED), FileAccessPolicy.Action.DOWNLOAD));
        assertOk(() -> policy.checkRead(file("PAYROLL", 1L), FileAccessPolicy.Action.VIEW));
    }

    @Test
    void unregisteredModule_isDeniedEvenWithFileExplorerPermissions_andAudited() {
        login("gv", List.of("ROLE_TEACHER"), Permissions.FILE_VIEW, Permissions.FILE_DOWNLOAD, Permissions.FILE_UPLOAD);
        assertForbidden(() -> policy.checkRead(file("PAYROLL", 1L), FileAccessPolicy.Action.VIEW));
        verify(auditService).denied(eq(AuditActions.FILE_ACCESS_DENIED), eq(AuditActions.RESOURCE_FILE), eq(100L),
                anyMap());
    }

    @Test
    void fileManagerDownload_bypassesModuleRules() {
        login("qlfile", List.of("ROLE_X"), Permissions.FILE_MANAGER_DOWNLOAD);
        assertOk(() -> policy.checkRead(file("PAYROLL", 1L), FileAccessPolicy.Action.DOWNLOAD));
        assertOk(() -> policy.checkRead(file("STUDENT", STUDENT_DELETED), FileAccessPolicy.Action.VIEW));
    }

    @Test
    void tuitionFile_requiresTuitionView() {
        login("gv", List.of("ROLE_TEACHER"), Permissions.STUDENT_VIEW, Permissions.FILE_VIEW, Permissions.FILE_DOWNLOAD);
        assertForbidden(() -> policy.checkRead(file("TUITION", 4L), FileAccessPolicy.Action.DOWNLOAD));
        login("kt", List.of("ROLE_ACCOUNTANT"), Permissions.TUITION_FEE_VIEW);
        assertOk(() -> policy.checkRead(file("TUITION", 4L), FileAccessPolicy.Action.DOWNLOAD));
    }

    @Test
    void deletedOrPendingFile_onlyForManagers() {
        login("gv", List.of("ROLE_GIAOVU"), Permissions.STUDENT_VIEW);
        FileEntity deleted = file("STUDENT", STUDENT_IN_TAUGHT);
        deleted.setStatus("DELETED");
        deleted.setIsDeleted(1);
        FileEntity pending = file("STUDENT", STUDENT_IN_TAUGHT);
        pending.setStatus("PENDING");
        assertForbidden(() -> policy.checkRead(deleted, FileAccessPolicy.Action.VIEW));
        assertForbidden(() -> policy.checkRead(pending, FileAccessPolicy.Action.VIEW));
        assertOk(() -> policy.checkRead(file("STUDENT", STUDENT_IN_TAUGHT), FileAccessPolicy.Action.VIEW));
    }

    @Test
    void fileOfDeletedReference_isDeniedForNonManagers() {
        login("gv", List.of("ROLE_GIAOVU"), Permissions.STUDENT_VIEW);
        assertForbidden(() -> policy.checkRead(file("STUDENT", STUDENT_DELETED), FileAccessPolicy.Action.VIEW));
        assertForbidden(() -> policy.checkList("STUDENT", STUDENT_DELETED));
    }

    @Test
    void teacher_studentFiles_onlyForStudentsOfTaughtClasses() {
        teacher(Permissions.STUDENT_VIEW, Permissions.STUDENT_UPDATE);
        assertOk(() -> policy.checkRead(file("STUDENT", STUDENT_IN_TAUGHT), FileAccessPolicy.Action.DOWNLOAD));
        assertForbidden(() -> policy.checkRead(file("STUDENT", STUDENT_ELSEWHERE), FileAccessPolicy.Action.VIEW));
        assertOk(() -> policy.checkList("STUDENT", STUDENT_IN_TAUGHT));
        assertForbidden(() -> policy.checkList("STUDENT", STUDENT_ELSEWHERE));
        assertThat(policy.canDelete(file("STUDENT", STUDENT_IN_TAUGHT))).isTrue();
        assertThat(policy.canDelete(file("STUDENT", STUDENT_ELSEWHERE))).isFalse();
    }

    @Test
    void teacher_classFiles_onlyForTaughtClasses() {
        teacher(Permissions.CLASS_VIEW, Permissions.CLASS_UPDATE);
        assertOk(() -> policy.checkRead(file("CLASS", TAUGHT_CLASS), FileAccessPolicy.Action.VIEW));
        assertForbidden(() -> policy.checkRead(file("CLASS", OTHER_CLASS), FileAccessPolicy.Action.VIEW));
        assertOk(() -> policy.checkUpload("CLASS", TAUGHT_CLASS));
        assertForbidden(() -> policy.checkUpload("CLASS", OTHER_CLASS));
    }

    @Test
    void teacherScopeFlagOff_restoresMenuBasedAccess() {
        teacher(Permissions.STUDENT_VIEW);
        filesProperties.setTeacherScopeEnabled(false);
        assertOk(() -> policy.checkRead(file("STUDENT", STUDENT_ELSEWHERE), FileAccessPolicy.Action.VIEW));
    }

    @Test
    void studentImportFile_requiresImportOrCreate() {
        FileEntity imp = file("STUDENT", null);
        imp.setFilePath("STUDENT/IMPORT/2026/10/x.xlsx");
        login("gv", List.of("ROLE_GIAOVU"), Permissions.STUDENT_VIEW);
        assertForbidden(() -> policy.checkRead(imp, FileAccessPolicy.Action.DOWNLOAD));
        login("gv", List.of("ROLE_GIAOVU"), Permissions.STUDENT_IMPORT);
        assertOk(() -> policy.checkRead(imp, FileAccessPolicy.Action.DOWNLOAD));
    }

    @Test
    void commonFiles_failClosedForViewDownloadOnlyRoles() {
        login("ts", List.of("ROLE_ADMISSION"), Permissions.FILE_VIEW, Permissions.FILE_DOWNLOAD);
        assertForbidden(() -> policy.checkRead(file("COMMON", 1L), FileAccessPolicy.Action.VIEW));
        login("kt", List.of("ROLE_ACCOUNTANT"), Permissions.FILE_VIEW, Permissions.FILE_UPLOAD);
        assertOk(() -> policy.checkRead(file("COMMON", 1L), FileAccessPolicy.Action.VIEW));
        assertForbidden(() -> policy.checkRead(file("COMMON", 1L), FileAccessPolicy.Action.DOWNLOAD));
    }

    @Test
    void infectedFile_isNeverServed_pendingBlockedWhenFailClosed() {
        login("admin", List.of("ROLE_ADMIN"));
        FileEntity infected = file("STUDENT", STUDENT_IN_TAUGHT);
        infected.setScanStatus("INFECTED");
        assertCode(() -> policy.checkRead(infected, FileAccessPolicy.Action.VIEW), "FILE_INFECTED");

        FileEntity pending = file("STUDENT", STUDENT_IN_TAUGHT);
        pending.setScanStatus("PENDING");
        assertOk(() -> policy.checkRead(pending, FileAccessPolicy.Action.VIEW));
        storageProperties.getScan().setMode("clamav");
        assertCode(() -> policy.checkRead(pending, FileAccessPolicy.Action.VIEW), "FILE_SCAN_PENDING");
    }

    // ---- Upload --------------------------------------------------------------------------------

    @Test
    void upload_genericRejectedForLibraryImportAndUnknown_evenForAdmin() {
        login("admin", List.of("ROLE_ADMIN"));
        assertCode(() -> policy.checkUpload("LIBRARY", 1L), "INVALID_MODULE");
        assertCode(() -> policy.checkUpload("STUDENT_IMPORT", null), "INVALID_MODULE");
        assertCode(() -> policy.checkUpload("PAYROLL", 1L), "INVALID_MODULE");
    }

    @Test
    void upload_referenceRequiredExistingAndActive() {
        login("admin", List.of("ROLE_ADMIN"));
        when(studentRepository.findById(404L)).thenReturn(Optional.empty());
        assertCode(() -> policy.checkUpload("STUDENT", null), "FILE_REFERENCE_REQUIRED");
        assertCode(() -> policy.checkUpload("STUDENT", 404L), "FILE_REFERENCE_NOT_FOUND");
        assertCode(() -> policy.checkUpload("STUDENT", STUDENT_DELETED), "FILE_REFERENCE_INACTIVE");
        assertOk(() -> policy.checkUpload("STUDENT", STUDENT_IN_TAUGHT));
        assertOk(() -> policy.checkUpload("COMMON", null));
    }

    @Test
    void upload_requiresModuleWritePermission() {
        login("gv", List.of("ROLE_GIAOVU"), Permissions.STUDENT_VIEW, Permissions.CLASS_UPDATE);
        assertForbidden(() -> policy.checkUpload("STUDENT", STUDENT_IN_TAUGHT));
        verify(auditService).denied(eq(AuditActions.FILE_ACCESS_DENIED), eq(AuditActions.RESOURCE_FILE), any(),
                anyMap());
    }

    @Test
    void upload_disabledModule() {
        login("admin", List.of("ROLE_ADMIN"));
        when(settingsSource.moduleSetting("CLASS")).thenReturn(Optional.of(FileModuleSettingDto.builder()
                .moduleName("CLASS").maxFileSizeBytes(1L).trashRetentionDays(30).active(false).build()));
        assertCode(() -> policy.checkUpload("CLASS", TAUGHT_CLASS), "FILE_MODULE_DISABLED");
    }

    // ---- Xoá ------------------------------------------------------------------------------------

    @Test
    void delete_rulesPerModule() {
        login("gv", List.of("ROLE_GIAOVU"), Permissions.STUDENT_VIEW, Permissions.STUDENT_CREATE);
        assertForbidden(() -> policy.checkDelete(file("STUDENT", STUDENT_IN_TAUGHT)));
        login("gv", List.of("ROLE_GIAOVU"), Permissions.STUDENT_UPDATE);
        assertOk(() -> policy.checkDelete(file("STUDENT", STUDENT_IN_TAUGHT)));
        login("qlfile", List.of("ROLE_X"), Permissions.FILE_MANAGER_DELETE);
        assertOk(() -> policy.checkDelete(file("PAYROLL", 1L)));
    }

    @Test
    void delete_commonOnlyOwnUploads() {
        login("kt", List.of("ROLE_ACCOUNTANT"), Permissions.FILE_UPLOAD);
        FileEntity mine = file("COMMON", null);
        mine.setUploadedByUserId(5L);
        FileEntity other = file("COMMON", null);
        other.setUploadedByUserId(6L);
        assertThat(policy.canDelete(mine)).isTrue();
        assertThat(policy.canDelete(other)).isFalse();
        verify(auditService, never()).denied(anyString(), anyString(), any(), anyMap());
    }

    // ---------------------------------------------------------------------------------------------

    private void teacher(String... permissions) {
        login("gv", List.of(Permissions.TEACHER_ROLE), permissions);
        when(guard.taughtClassIdsIfRestricted()).thenReturn(Optional.of(Set.of(TAUGHT_CLASS)));
    }

    private static void login(String username, List<String> roles, String... permissions) {
        AuthUserPrincipal p = AuthUserPrincipal.builder().id(5L).username(username).roles(roles)
                .permissions(Set.of(permissions)).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, List.of()));
    }

    private static FileEntity file(String module, Long ref) {
        return FileEntity.builder().id(100L).moduleName(module).referenceId(ref)
                .filePath(module + "/2026/10/x.pdf").originalName("x.pdf").contentType("application/pdf")
                .isDeleted(0).status("ACTIVE").scanStatus("SKIPPED").build();
    }

    private static StudentEntity student(Long id, int deleted) {
        StudentEntity s = new StudentEntity();
        s.setId(id);
        s.setIsDeleted(deleted);
        s.setStudentCode("HS" + id);
        s.setFullName("HS " + id);
        return s;
    }

    private static ClassEntity clazz(Long id) {
        ClassEntity c = new ClassEntity();
        c.setId(id);
        c.setIsDeleted(0);
        c.setClassCode("L" + id);
        c.setClassName("Lop " + id);
        return c;
    }

    private static ClassStudentEntity enrollment(Long classId) {
        ClassStudentEntity e = new ClassStudentEntity();
        e.setClassId(classId);
        return e;
    }

    private static void assertOk(ThrowableAssert.ThrowingCallable call) {
        assertThatCode(call).doesNotThrowAnyException();
    }

    private static void assertForbidden(ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ForbiddenException.class);
    }

    private static void assertCode(ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode()).isEqualTo(code);
    }
}
