package com.education.base.service.file;

import com.education.base.common.file.FileModuleDescriptor;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.config.FileStorageProperties;
import com.education.base.dto.request.FileScanResolveRequest;
import com.education.base.entity.FileEntity;
import com.education.base.entity.FileScanItemEntity;
import com.education.base.entity.FileScanRunEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.FileAdminRepository;
import com.education.base.repository.FileScanItemRepository;
import com.education.base.repository.FileScanRunRepository;
import com.education.base.security.Permissions;
import com.education.base.service.AuditService;
import com.education.base.storage.BlobEntry;
import com.education.base.storage.BlobStore;
import com.education.base.support.TestSecurityContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileMaintenanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T08:00:00Z");

    private final FileScanRunRepository runs = mock(FileScanRunRepository.class);
    private final FileScanItemRepository items = mock(FileScanItemRepository.class);
    private final FileAdminRepository files = mock(FileAdminRepository.class);
    private final BlobStore blobStore = mock(BlobStore.class);
    private final FileLifecycleService lifecycle = mock(FileLifecycleService.class);
    private final AuditService audit = mock(AuditService.class);
    private final FileModuleDescriptor studentModule = mock(FileModuleDescriptor.class);
    private final TaskExecutor sync = Runnable::run;
    private final List<FileScanItemEntity> savedItems = new ArrayList<>();
    private FileMaintenanceService service;
    private FileScanRunEntity run;

    @BeforeEach
    void setUp() {
        when(studentModule.code()).thenReturn("STUDENT");
        service = new FileMaintenanceService(runs, items, files, blobStore, lifecycle, audit,
                new FileStorageProperties(), List.of(studentModule), sync,
                Clock.fixed(NOW, ZoneId.of("Asia/Bangkok")));
        when(runs.findByStatus("RUNNING")).thenReturn(List.of());
        when(runs.saveAndFlush(any())).thenAnswer(inv -> {
            run = inv.getArgument(0);
            run.setId(7L);
            return run;
        });
        when(runs.findById(7L)).thenAnswer(inv -> Optional.ofNullable(run));
        when(items.saveAll(any())).thenAnswer(inv -> {
            Iterable<FileScanItemEntity> it = inv.getArgument(0);
            it.forEach(savedItems::add);
            return List.of();
        });
        TestSecurityContexts.loginAdmin(1);
    }

    @AfterEach
    void tearDown() {
        TestSecurityContexts.clear();
    }

    private static FileEntity file(long id, String path, long size, String status, Long ref) {
        return FileEntity.builder().id(id).filePath(path).fileSize(size).status(status).moduleName("STUDENT")
                .referenceId(ref).originalName("f" + id + ".pdf").build();
    }

    @Test
    void orphanScan_reportsMissingOrphanMismatchAndRefState_neverDeletes() throws Exception {
        when(files.findByStatusInAndIdGreaterThanOrderByIdAsc(any(), eq(0L), any())).thenReturn(List.of(
                file(1, "STUDENT/2026/10/a.pdf", 100, "ACTIVE", 12L),
                file(2, "STUDENT\\2026\\10\\b.pdf", 50, "ACTIVE", 13L),
                file(3, "STUDENT/2026/10/missing.pdf", 10, "DELETED", 12L)));
        when(files.findByStatusInAndIdGreaterThanOrderByIdAsc(any(), eq(3L), any())).thenReturn(List.of());
        Instant old = NOW.minusSeconds(3 * 86400);
        when(blobStore.list("")).thenReturn(Stream.of(
                new BlobEntry("STUDENT/2026/10/a.pdf", 100, old),
                new BlobEntry("STUDENT/2026/10/b.pdf", 99, old),
                new BlobEntry("STUDENT/2026/10/orphan.pdf", 5, old),
                new BlobEntry("STUDENT/2026/10/fresh.pdf", 5, NOW.minusSeconds(60))));
        when(studentModule.describeReference(12L)).thenReturn(
                new FileReferenceInfo("STUDENT", 12L, "HS12", "/students/list?id=12", "ACTIVE"));
        when(studentModule.describeReference(13L)).thenReturn(
                new FileReferenceInfo("STUDENT", 13L, "HS13", null, "DELETED"));

        var dto = service.startOrphanScan();

        assertThat(dto.getStatus()).isEqualTo("RUNNING");
        assertThat(run.getStatus()).isEqualTo("DONE");
        assertThat(run.getDbRowsChecked()).isEqualTo(3);
        assertThat(run.getBlobsChecked()).isEqualTo(4);
        assertThat(run.getOrphanBlobCount()).isEqualTo(1);
        assertThat(run.getMissingBlobCount()).isEqualTo(1);
        assertThat(run.getMismatchCount()).isEqualTo(1);
        assertThat(savedItems).extracting(FileScanItemEntity::getItemType)
                .containsExactlyInAnyOrder("SIZE_MISMATCH", "ORPHAN_BLOB", "MISSING_BLOB", "REF_DELETED");
        verify(blobStore, never()).delete(any());
        verify(blobStore, never()).moveToQuarantine(any());
        verify(lifecycle, never()).softDelete(any(), any());
    }

    @Test
    void orphanScan_failureMarksRunFailed() throws Exception {
        when(files.findByStatusInAndIdGreaterThanOrderByIdAsc(any(), anyLong(), any())).thenReturn(List.of());
        when(blobStore.list("")).thenThrow(new java.io.IOException("disk gone"));
        service.startOrphanScan();
        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getDetail()).contains("disk gone");
    }

    @Test
    void secondRunWhileRunning_isRejected() {
        when(runs.findByStatus("RUNNING")).thenReturn(List.of(FileScanRunEntity.builder().id(3L)
                .status("RUNNING").startedAt(LocalDateTime.ofInstant(NOW, ZoneId.of("Asia/Bangkok"))).build()));
        assertThatThrownBy(() -> service.startOrphanScan())
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("FILE_SCAN_RUNNING");
    }

    @Test
    void uniqueIndexRace_isRejectedAsRunning() {
        doThrow(new DataIntegrityViolationException("UQ_FILE_SCAN_RUNS_RUNNING")).when(runs).saveAndFlush(any());
        assertThatThrownBy(() -> service.startChecksumBackfill())
                .extracting("errorCode").isEqualTo("FILE_SCAN_RUNNING");
    }

    @Test
    void staleRunningRun_isMarkedFailedAndNewRunStarts() {
        FileScanRunEntity stale = FileScanRunEntity.builder().id(3L).status("RUNNING")
                .startedAt(LocalDateTime.ofInstant(NOW, ZoneId.of("Asia/Bangkok")).minusHours(7)).build();
        when(runs.findByStatus("RUNNING")).thenReturn(List.of(stale));
        when(files.findByStatusInAndChecksumSha256IsNullAndIdGreaterThanOrderByIdAsc(any(), anyLong(), any()))
                .thenReturn(List.of());
        service.startChecksumBackfill();
        assertThat(stale.getStatus()).isEqualTo("FAILED");
        assertThat(run.getStatus()).isEqualTo("DONE");
    }

    @Test
    void checksumBackfill_fillsHashAndReportsMismatchAndMissing() throws Exception {
        byte[] content = "abc".getBytes(StandardCharsets.UTF_8);
        when(files.findByStatusInAndChecksumSha256IsNullAndIdGreaterThanOrderByIdAsc(any(), eq(0L), any()))
                .thenReturn(List.of(file(1, "k1", 3, "ACTIVE", null), file(2, "k2", 9, "ACTIVE", null),
                        file(3, "k3", 1, "DELETED", null)));
        when(files.findByStatusInAndChecksumSha256IsNullAndIdGreaterThanOrderByIdAsc(any(), eq(3L), any()))
                .thenReturn(List.of());
        when(blobStore.exists("k1")).thenReturn(true);
        when(blobStore.exists("k2")).thenReturn(true);
        when(blobStore.exists("k3")).thenReturn(false);
        when(blobStore.open("k1")).thenReturn(new ByteArrayInputStream(content));
        when(blobStore.open("k2")).thenReturn(new ByteArrayInputStream(content));
        when(files.fillChecksum(eq(1L), any())).thenReturn(1);

        service.startChecksumBackfill();

        verify(files).fillChecksum(1L, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        verify(files, never()).fillChecksum(eq(2L), any());
        assertThat(savedItems).extracting(FileScanItemEntity::getItemType)
                .containsExactlyInAnyOrder("SIZE_MISMATCH", "MISSING_BLOB");
        assertThat(run.getDetail()).contains("1 file");
    }

    private FileScanItemEntity pending(String type) {
        FileScanItemEntity item = FileScanItemEntity.builder().id(50L).runId(7L).itemType(type).fileId(1L)
                .storageKey("STUDENT/x.pdf").build();
        when(items.findById(50L)).thenReturn(Optional.of(item));
        when(items.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return item;
    }

    @Test
    void resolve_markMissing_softDeletesActiveFile() {
        pending("MISSING_BLOB");
        when(files.findById(1L)).thenReturn(Optional.of(file(1, "STUDENT/x.pdf", 1, "ACTIVE", 12L)));
        var dto = service.resolveItem(7L, 50L, new FileScanResolveRequest("MARK_MISSING", "ok"));
        assertThat(dto.getResolution()).isEqualTo("MARKED_MISSING");
        verify(lifecycle).softDelete(eq(1L), eq("Mất file (đối soát #7)"));
    }

    @Test
    void resolve_actionNotAllowedForType_isValidationError() {
        pending("SIZE_MISMATCH");
        assertThatThrownBy(() -> service.resolveItem(7L, 50L, new FileScanResolveRequest("DELETE_BLOB", null)))
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void resolve_wrongRun_isNotFound_andResolvedTwice_isRejected() {
        FileScanItemEntity item = pending("ORPHAN_BLOB");
        assertThatThrownBy(() -> service.resolveItem(8L, 50L, new FileScanResolveRequest("IGNORE", null)))
                .extracting("errorCode").isEqualTo("FILE_SCAN_ITEM_NOT_FOUND");
        item.setResolution("IGNORED");
        assertThatThrownBy(() -> service.resolveItem(7L, 50L, new FileScanResolveRequest("IGNORE", null)))
                .extracting("errorCode").isEqualTo("FILE_SCAN_ITEM_RESOLVED");
    }

    @Test
    void resolve_deleteBlob_requiresPurgePermission() throws Exception {
        pending("ORPHAN_BLOB");
        TestSecurityContexts.login(2, List.of("ROLE_OPS"), Set.of(Permissions.FILE_MANAGER_MAINTAIN));
        assertThatThrownBy(() -> service.resolveItem(7L, 50L, new FileScanResolveRequest("DELETE_BLOB", null)))
                .isInstanceOf(ForbiddenException.class);
        verify(blobStore, never()).delete(any());

        TestSecurityContexts.login(2, List.of("ROLE_OPS"),
                Set.of(Permissions.FILE_MANAGER_MAINTAIN, Permissions.FILE_MANAGER_PURGE));
        var dto = service.resolveItem(7L, 50L, new FileScanResolveRequest("DELETE_BLOB", null));
        assertThat(dto.getResolution()).isEqualTo("BLOB_DELETED");
        verify(blobStore).delete("STUDENT/x.pdf");
    }

    @Test
    void resolve_quarantine_refusesWhenBlobNowReferenced() throws Exception {
        pending("ORPHAN_BLOB");
        when(files.countLiveByFilePath("STUDENT/x.pdf", "STUDENT\\x.pdf")).thenReturn(1L);
        assertThatThrownBy(() -> service.resolveItem(7L, 50L, new FileScanResolveRequest("QUARANTINE", null)))
                .extracting("errorCode").isEqualTo("FILE_INVALID_STATE");
        verify(blobStore, never()).moveToQuarantine(any());
    }

    @Test
    void resolve_quarantine_movesBlobAndAudits() throws Exception {
        pending("ORPHAN_BLOB");
        service.resolveItem(7L, 50L, new FileScanResolveRequest("QUARANTINE", null));
        verify(blobStore).moveToQuarantine("STUDENT/x.pdf");
        ArgumentCaptor<com.education.base.audit.AuditEvent> ev =
                ArgumentCaptor.forClass(com.education.base.audit.AuditEvent.class);
        verify(audit).record(ev.capture());
        assertThat(ev.getValue().getAction()).isEqualTo("FILE_SCAN_ITEM_RESOLVED");
        assertThat(ev.getValue().getResourceId()).isEqualTo("7");
    }

    @Test
    void getRun_unknown_isNotFound() {
        when(runs.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getRun(99L)).extracting("errorCode").isEqualTo("FILE_SCAN_RUN_NOT_FOUND");
    }

    @Test
    void normalizeKey_handlesBackslashAndLeadingSlash() {
        assertThat(FileMaintenanceService.normalizeKey("\\STUDENT\\a.pdf")).isEqualTo("STUDENT/a.pdf");
    }
}
