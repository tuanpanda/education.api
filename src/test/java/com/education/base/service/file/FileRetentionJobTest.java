package com.education.base.service.file;

import com.education.base.audit.AuditActions;
import com.education.base.audit.AuditEvent;
import com.education.base.audit.AuditResult;
import com.education.base.config.FileStorageProperties;
import com.education.base.entity.FileEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.FileAdminRepository;
import com.education.base.service.AuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileRetentionJobTest {

    private final FileAdminRepository repo = mock(FileAdminRepository.class);
    private final FileLifecycleService lifecycle = mock(FileLifecycleService.class);
    private final AuditService audit = mock(AuditService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-07T19:30:00Z"), ZoneId.of("Asia/Bangkok"));
    private final FileRetentionJob job = new FileRetentionJob(repo, lifecycle, audit, new FileStorageProperties(), clock);

    @Test
    void purgesExpiredTrashAndStalePending_continuesOnError_auditsAsSystem() {
        LocalDateTime now = LocalDateTime.now(clock);
        when(repo.findIdsDueForPurge(eq("DELETED"), eq(now), eq(0L), any())).thenReturn(List.of(1L, 2L));
        when(repo.findIdsDueForPurge(eq("DELETED"), eq(now), eq(2L), any())).thenReturn(List.of());
        when(repo.findPendingIdsOlderThan(eq("PENDING"), eq(now.minusHours(24)), eq(0L), any()))
                .thenReturn(List.of(5L));
        when(repo.findPendingIdsOlderThan(eq("PENDING"), any(), eq(5L), any())).thenReturn(List.of());
        when(lifecycle.purge(eq(1L), any())).thenReturn(FileEntity.builder().id(1L).moduleName("STUDENT")
                .fileSize(10L).build());
        when(lifecycle.purge(eq(2L), any())).thenThrow(new OracleBusinessException("FILE_INVALID_STATE", "x"));
        when(lifecycle.purge(eq(5L), any())).thenReturn(FileEntity.builder().id(5L).build());

        FileRetentionJob.Result result = job.runOnce();

        assertThat(result.purged()).isEqualTo(2);
        assertThat(result.trash()).isEqualTo(1);
        assertThat(result.pending()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(3)).record(events.capture());
        assertThat(events.getAllValues()).allSatisfy(e -> {
            assertThat(e.getAction()).isEqualTo(AuditActions.FILE_PURGED);
            assertThat(e.getActorUsername()).isEqualTo("SYSTEM");
        });
        assertThat(events.getAllValues()).filteredOn(e -> e.getResult() == AuditResult.FAILURE).hasSize(1);
    }

    @Test
    void nothingDue_noCalls() {
        when(repo.findIdsDueForPurge(any(), any(), anyLong(), any())).thenReturn(List.of());
        when(repo.findPendingIdsOlderThan(any(), any(), anyLong(), any())).thenReturn(List.of());
        assertThat(job.runOnce().purged()).isZero();
        verify(lifecycle, times(0)).purge(any(), any());
    }
}
