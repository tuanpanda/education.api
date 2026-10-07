package com.education.base.service.impl;

import com.education.base.audit.AuditActions;
import com.education.base.audit.AuditEvent;
import com.education.base.common.file.FileModuleDescriptor;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.config.FileStorageProperties;
import com.education.base.config.FilesProperties;
import com.education.base.dto.request.FileBulkRequest;
import com.education.base.dto.request.FileFilterRequest;
import com.education.base.dto.request.FileModuleSettingsUpdateRequest;
import com.education.base.dto.request.FilePurgeRequest;
import com.education.base.dto.request.FileQuotasUpdateRequest;
import com.education.base.dto.response.FileModuleSettingDto;
import com.education.base.dto.response.FileQuotaDto;
import com.education.base.entity.FileEntity;
import com.education.base.entity.FileModuleSettingEntity;
import com.education.base.entity.FileQuotaEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.UserEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AuditLogRepository;
import com.education.base.repository.FileAdminRepository;
import com.education.base.repository.FileModuleSettingRepository;
import com.education.base.repository.FileQuotaRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.UserRepository;
import com.education.base.service.AuditService;
import com.education.base.service.FileAdminService;
import com.education.base.service.file.DbFileSettingsSource;
import com.education.base.service.file.FileLifecycleService;
import com.education.base.storage.BlobStore;
import com.education.base.storage.StorageUsage;
import com.education.base.support.TestSecurityContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.unit.DataSize;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class FileAdminServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T03:00:00Z"), ZONE);

    private final FileAdminRepository files = mock(FileAdminRepository.class);
    private final FileModuleSettingRepository moduleRepo = mock(FileModuleSettingRepository.class);
    private final FileQuotaRepository quotaRepo = mock(FileQuotaRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuditLogRepository auditLogs = mock(AuditLogRepository.class);
    private final FileLifecycleService lifecycle = mock(FileLifecycleService.class);
    private final BlobStore blobStore = mock(BlobStore.class);
    private final AuditService audit = mock(AuditService.class);
    private final DbFileSettingsSource settings = mock(DbFileSettingsSource.class);
    private final FileModuleDescriptor studentModule = mock(FileModuleDescriptor.class);
    private final FilesProperties filesProperties = new FilesProperties();
    private FileAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        when(studentModule.code()).thenReturn("STUDENT");
        when(studentModule.describeReference(12L)).thenReturn(
                new FileReferenceInfo("STUDENT", 12L, "HS000012 - A", "/students/list?id=12", "ACTIVE"));
        when(studentModule.describeReference(13L)).thenReturn(
                new FileReferenceInfo("STUDENT", 13L, "HS000013 - B", null, "DELETED"));
        service = new FileAdminServiceImpl(files, moduleRepo, quotaRepo, students, users, auditLogs, lifecycle,
                blobStore, audit, settings, new FileStorageProperties(), filesProperties, List.of(studentModule),
                DataSize.ofMegabytes(50), CLOCK);
        when(auditLogs.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        TestSecurityContexts.loginAdmin(1);
    }

    @AfterEach
    void tearDown() {
        TestSecurityContexts.clear();
    }

    private static FileEntity file(long id, String status) {
        return FileEntity.builder().id(id).originalName("Hồ sơ " + id + ".pdf").filePath("STUDENT/2026/10/" + id + ".pdf")
                .contentType("application/pdf").fileSize(100L).moduleName("STUDENT").referenceId(12L)
                .status(status).uploadedByUserId(5L).ownerStudentId(12L).createdAt(LocalDateTime.now(CLOCK))
                .build();
    }

    @Test
    void search_mapsUsageUploaderStudentAndUrls() {
        FileEntity f = file(101, "DELETED");
        f.setPurgeAfter(LocalDateTime.now(CLOCK).plusDays(2).plusHours(1));
        when(files.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(f)));
        when(users.findAllById(any())).thenReturn(List.of(UserEntity.builder().id(5L).username("gv01")
                .fullName("Trần B").build()));
        when(students.findAllById(any())).thenReturn(List.of(StudentEntity.builder().id(12L)
                .studentCode("HS000012").fullName("Nguyễn A").build()));

        var page = service.search(FileFilterRequest.builder().status("DELETED").pageNo(1).pageSize(20).build());

        var dto = page.getContent().get(0);
        assertThat(dto.getUsage().getLabel()).isEqualTo("HS000012 - A");
        assertThat(dto.getUsage().getState()).isEqualTo("ACTIVE");
        assertThat(dto.getUploadedByUsername()).isEqualTo("gv01");
        assertThat(dto.getOwnerStudentCode()).isEqualTo("HS000012");
        assertThat(dto.getDaysUntilPurge()).isEqualTo(3);
        assertThat(dto.getViewUrl()).isEqualTo("/api/v1/admin/files/101/view");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(files).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void search_refStateDeleted_onlyEvaluatesDescriptors() {
        when(files.distinctReferences(any())).thenReturn(List.of(new Object[]{"STUDENT", 12L},
                new Object[]{"STUDENT", 13L}, new Object[]{"GHOST", 1L}));
        when(files.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.search(FileFilterRequest.builder().refState("DELETED").build());
        verify(studentModule).describeReference(13L);
    }

    @Test
    void describe_unknownModule_isNotFoundWithoutCallingDescriptor() {
        FileReferenceInfo info = service.describe("GHOST", 1L);
        assertThat(info.state()).isEqualTo("NOT_FOUND");
        assertThat(service.describe("STUDENT", null).state()).isEqualTo("NONE");
    }

    @Test
    void delete_activeFile_callsLifecycleAndAudits() {
        when(files.findById(1L)).thenReturn(Optional.of(file(1, "ACTIVE")));
        when(lifecycle.softDelete(1L, "trùng")).thenReturn(file(1, "DELETED"));
        var dto = service.delete(1L, "  trùng ");
        assertThat(dto.getStatus()).isEqualTo("DELETED");
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(ev.capture());
        assertThat(ev.getValue().getAction()).isEqualTo(AuditActions.FILE_DELETED);
        assertThat(ev.getValue().getDetails()).containsEntry("via", "ADMIN");
    }

    @Test
    void delete_notActive_isInvalidState_andMissing_isNotFound() {
        when(files.findById(1L)).thenReturn(Optional.of(file(1, "DELETED")));
        assertThatThrownBy(() -> service.delete(1L, "x")).extracting("errorCode").isEqualTo("FILE_INVALID_STATE");
        when(files.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(2L, "x")).extracting("errorCode").isEqualTo("FILE_NOT_FOUND");
        verify(lifecycle, never()).softDelete(any(), any());
    }

    @Test
    void restore_onlyFromTrash() {
        when(files.findById(1L)).thenReturn(Optional.of(file(1, "ACTIVE")));
        assertThatThrownBy(() -> service.restore(1L)).extracting("errorCode").isEqualTo("FILE_INVALID_STATE");
        when(files.findById(2L)).thenReturn(Optional.of(file(2, "DELETED")));
        when(lifecycle.restore(2L)).thenReturn(file(2, "ACTIVE"));
        assertThat(service.restore(2L).getStatus()).isEqualTo("ACTIVE");
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(ev.capture());
        assertThat(ev.getValue().getAction()).isEqualTo(AuditActions.FILE_RESTORED);
    }

    @Test
    void purge_requiresTrashAndExactName() {
        when(files.findById(1L)).thenReturn(Optional.of(file(1, "ACTIVE")));
        assertThatThrownBy(() -> service.purge(1L, new FilePurgeRequest("r", "Hồ sơ 1.pdf")))
                .extracting("errorCode").isEqualTo("FILE_INVALID_STATE");
        when(files.findById(2L)).thenReturn(Optional.of(file(2, "DELETED")));
        assertThatThrownBy(() -> service.purge(2L, new FilePurgeRequest("r", "ho so 2.pdf")))
                .extracting("errorCode").isEqualTo("FILE_PURGE_CONFIRM_MISMATCH");
        when(lifecycle.purge(2L, "r")).thenReturn(file(2, "PURGED"));
        assertThat(service.purge(2L, new FilePurgeRequest("r", "Hồ sơ 2.pdf")).getStatus()).isEqualTo("PURGED");
    }

    @Test
    void bulkDelete_partialFailure_doesNotStopBatch() {
        when(files.findById(1L)).thenReturn(Optional.of(file(1, "ACTIVE")));
        when(files.findById(2L)).thenReturn(Optional.empty());
        when(lifecycle.softDelete(eq(1L), any())).thenReturn(file(1, "DELETED"));
        var result = service.bulkDelete(new FileBulkRequest(List.of(1L, 2L, 1L), "dọn"));
        assertThat(result.getSucceeded()).containsExactly(1L);
        assertThat(result.getFailed()).singleElement().satisfies(f -> {
            assertThat(f.getId()).isEqualTo(2L);
            assertThat(f.getErrorCode()).isEqualTo("FILE_NOT_FOUND");
        });
    }

    @Test
    void bulk_limitsAndReason() {
        List<Long> many = LongStream.rangeClosed(1, 101).boxed().toList();
        assertThatThrownBy(() -> service.bulkRestore(new FileBulkRequest(many, null)))
                .extracting("errorCode").isEqualTo("FILE_BULK_TOO_MANY");
        assertThatThrownBy(() -> service.bulkDelete(new FileBulkRequest(List.of(1L), " ")))
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void open_purged_isInvalid_missingBlob_is404Code_viewPdfInline() throws Exception {
        when(files.findById(1L)).thenReturn(Optional.of(file(1, "PURGED")));
        assertThatThrownBy(() -> service.open(1L, true)).extracting("errorCode").isEqualTo("FILE_INVALID_STATE");

        when(files.findById(2L)).thenReturn(Optional.of(file(2, "DELETED")));
        when(blobStore.exists(any())).thenReturn(false);
        assertThatThrownBy(() -> service.open(2L, true)).extracting("errorCode").isEqualTo("FILE_BLOB_MISSING");

        when(blobStore.exists(any())).thenReturn(true);
        when(blobStore.resource(any())).thenReturn(new ByteArrayResource(new byte[]{1}));
        FileAdminService.FileContent view = service.open(2L, false);
        assertThat(view.inline()).isTrue();
        assertThat(view.contentType()).isEqualTo("application/pdf");
        verify(audit, never()).record(any());
        FileAdminService.FileContent dl = service.open(2L, true);
        assertThat(dl.inline()).isFalse();
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(ev.capture());
        assertThat(ev.getValue().getAction()).isEqualTo(AuditActions.FILE_DOWNLOADED);
    }

    @Test
    void detail_includesBlobExistsAndStorageKey() {
        when(files.findById(1L)).thenReturn(Optional.of(file(1, "ACTIVE")));
        when(blobStore.exists("STUDENT/2026/10/1.pdf")).thenReturn(true);
        var detail = service.detail(1L);
        assertThat(detail.getBlobExists()).isTrue();
        assertThat(detail.getStorageKey()).isEqualTo("STUDENT/2026/10/1.pdf");
        assertThat(detail.getAuditTrail()).isEmpty();
    }

    @Test
    void stats_fillsTwelveMonthsAndStatusBuckets() throws Exception {
        when(files.sumByStatus()).thenReturn(List.<Object[]>of(new Object[]{"ACTIVE", 3L, 300L}));
        when(files.sumByModule(any())).thenReturn(List.<Object[]>of(new Object[]{"STUDENT", 3L, 300L}));
        when(files.sumByMonth(any(), any())).thenReturn(List.<Object[]>of(new Object[]{2026, 10, 2L, 200L},
                new Object[]{2026, 9, 1L, 100L}));
        when(files.topUploaders(any(), any())).thenReturn(List.<Object[]>of(new Object[]{5L, 3L, 300L}));
        when(files.topStudents(any(), any())).thenReturn(List.<Object[]>of(new Object[]{12L, 3L, 300L}));
        when(files.distinctReferences(any())).thenReturn(List.<Object[]>of(new Object[]{"STUDENT", 13L}));
        when(files.count(any(Specification.class))).thenReturn(1L);
        when(settings.quota(any(), any())).thenReturn(Optional.empty());
        when(blobStore.usage()).thenReturn(new StorageUsage("LOCAL", 100L << 30, 1L << 30, -1));
        when(files.sumSize(any())).thenReturn(300L);

        var stats = service.stats();

        assertThat(stats.getByStatus()).hasSize(4);
        assertThat(stats.getByMonth()).hasSize(12);
        assertThat(stats.getByMonth().get(11).getKey()).isEqualTo("2026-10");
        assertThat(stats.getByMonth().get(11).getFileCount()).isEqualTo(2);
        assertThat(stats.getByMonth().get(0).getKey()).isEqualTo("2025-11");
        assertThat(stats.getTopStudents().get(0).getQuotaBytes()).isEqualTo(500L * 1024 * 1024);
        assertThat(stats.getFilesWithDeletedReference()).isEqualTo(1);
        assertThat(stats.getStorage().isLowDisk()).isTrue();
        assertThat(stats.getStorage().getGlobalQuotaBytes()).isEqualTo(50L << 30);
    }

    private FileModuleSettingEntity studentSetting() {
        return FileModuleSettingEntity.builder().moduleName("STUDENT").maxFileSizeBytes(20L << 20)
                .maxFilesPerRef(30).trashRetentionDays(30).countToStudentQuota(1).isActive(1).build();
    }

    private static FileModuleSettingDto dto(String module, long max, List<String> ext) {
        return FileModuleSettingDto.builder().moduleName(module).maxFileSizeBytes(max).maxFilesPerRef(30)
                .allowedExtensions(ext).trashRetentionDays(14).countToStudentQuota(true).active(true).build();
    }

    @Test
    void updateModuleSettings_validatesAndAuditsDiff_andEvictsCache() {
        FileModuleSettingEntity e = studentSetting();
        when(moduleRepo.findAll()).thenReturn(List.of(e));
        when(moduleRepo.findAll(any(Sort.class))).thenReturn(List.of(e));

        assertThatThrownBy(() -> service.updateModuleSettings(new FileModuleSettingsUpdateRequest(
                List.of(dto("LEAD", 1000, null))))).extracting("errorCode").isEqualTo("FILE_SETTING_INVALID");
        assertThatThrownBy(() -> service.updateModuleSettings(new FileModuleSettingsUpdateRequest(
                List.of(dto("STUDENT", 60L << 20, null))))).extracting("errorCode").isEqualTo("FILE_SETTING_INVALID");
        assertThatThrownBy(() -> service.updateModuleSettings(new FileModuleSettingsUpdateRequest(
                List.of(dto("STUDENT", 1000, List.of("exe")))))).extracting("errorCode")
                .isEqualTo("FILE_SETTING_INVALID");

        var result = service.updateModuleSettings(new FileModuleSettingsUpdateRequest(
                List.of(dto("STUDENT", 10L << 20, List.of("PDF", "jpg", "pdf")))));

        assertThat(e.getAllowedExtensions()).isEqualTo("pdf,jpg");
        assertThat(e.getTrashRetentionDays()).isEqualTo(14);
        assertThat(e.getUpdatedBy()).isEqualTo("user1");
        assertThat(result).hasSize(1);
        verify(settings).evict();
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(ev.capture());
        assertThat(ev.getValue().getAction()).isEqualTo(AuditActions.FILE_SETTINGS_CHANGED);
    }

    @Test
    void updateQuotas_rulesForGlobalAndScopes() {
        FileQuotaEntity global = FileQuotaEntity.builder().id(1L).scopeType("GLOBAL").maxBytes(10L).warnPercent(80)
                .build();
        FileQuotaEntity student = FileQuotaEntity.builder().id(2L).scopeType("STUDENT").scopeId(12L).maxBytes(5L)
                .warnPercent(80).build();
        when(quotaRepo.findAll()).thenReturn(new ArrayList<>(List.of(global, student)));

        assertThatThrownBy(() -> service.updateQuotas(new FileQuotasUpdateRequest(List.of(), List.of(1L))))
                .extracting("errorCode").isEqualTo("FILE_QUOTA_INVALID");
        assertThatThrownBy(() -> service.updateQuotas(new FileQuotasUpdateRequest(List.of(
                FileQuotaDto.builder().scopeType("STUDENT").maxBytes(1L).build()), List.of())))
                .extracting("errorCode").isEqualTo("FILE_QUOTA_INVALID");
        assertThatThrownBy(() -> service.updateQuotas(new FileQuotasUpdateRequest(List.of(
                FileQuotaDto.builder().scopeType("GLOBAL").scopeId(3L).maxBytes(1L).build()), List.of())))
                .extracting("errorCode").isEqualTo("FILE_QUOTA_INVALID");
        when(students.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> service.updateQuotas(new FileQuotasUpdateRequest(List.of(
                FileQuotaDto.builder().scopeType("STUDENT").scopeId(99L).maxBytes(1L).build()), List.of())))
                .extracting("errorCode").isEqualTo("FILE_QUOTA_INVALID");

        when(users.existsById(5L)).thenReturn(true);
        service.updateQuotas(new FileQuotasUpdateRequest(List.of(
                FileQuotaDto.builder().scopeType("GLOBAL").maxBytes(20L).warnPercent(90).build(),
                FileQuotaDto.builder().scopeType("USER").scopeId(5L).maxBytes(7L).build()), List.of(2L)));

        assertThat(global.getMaxBytes()).isEqualTo(20L);
        verify(quotaRepo).delete(student);
        ArgumentCaptor<Iterable<FileQuotaEntity>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(quotaRepo).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(FileQuotaEntity::getScopeType).containsExactly("GLOBAL", "USER");
        verify(settings).evict();
    }

    @Test
    void quotas_listIncludesUsageAndLabels() {
        when(quotaRepo.findAll()).thenReturn(new ArrayList<>(List.of(
                FileQuotaEntity.builder().id(2L).scopeType("STUDENT").scopeId(12L).maxBytes(5L).warnPercent(80).build(),
                FileQuotaEntity.builder().id(1L).scopeType("GLOBAL").maxBytes(10L).warnPercent(80).build())));
        when(students.findAllById(any())).thenReturn(List.of(StudentEntity.builder().id(12L).studentCode("HS12")
                .fullName("A").build()));
        when(files.sumSize(any())).thenReturn(9L);
        when(files.sumSizeByOwnerStudent(eq(12L), any())).thenReturn(4L);
        var list = service.quotas();
        assertThat(list).extracting(FileQuotaDto::getScopeType).containsExactly("GLOBAL", "STUDENT");
        assertThat(list.get(0).getUsedBytes()).isEqualTo(9L);
        assertThat(list.get(1).getScopeLabel()).isEqualTo("HS12 - A");
        assertThat(list.get(1).getUsedBytes()).isEqualTo(4L);
    }

    @Test
    void daysUntilPurge_roundsUpAndClampsToZero() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        FileEntity f = file(1, "DELETED");
        f.setPurgeAfter(now.minusHours(1));
        assertThat(FileAdminServiceImpl.daysUntilPurge(f, now)).isZero();
        f.setPurgeAfter(now.plusHours(1));
        assertThat(FileAdminServiceImpl.daysUntilPurge(f, now)).isEqualTo(1);
        f.setStatus("ACTIVE");
        assertThat(FileAdminServiceImpl.daysUntilPurge(f, now)).isNull();
    }
}
