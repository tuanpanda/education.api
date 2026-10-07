package com.education.base.service.file;

import com.education.base.audit.AuditActions;
import com.education.base.audit.AuditEvent;
import com.education.base.audit.AuditResult;
import com.education.base.common.file.FileConstants;
import com.education.base.config.FileStorageProperties;
import com.education.base.entity.FileEntity;
import com.education.base.repository.FileAdminRepository;
import com.education.base.service.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.LongFunction;

/**
 * Job dọn rác hằng ngày (mặc định 02:30, {@code app.storage.jobs.retention-cron}; tắt bằng
 * {@code app.storage.jobs.enabled=false}):
 * <ul>
 *   <li>file {@code DELETED} quá {@code PURGE_AFTER} -> {@link FileLifecycleService#purge} (xoá blob, giữ dòng PURGED);</li>
 *   <li>file {@code PENDING} quá {@code app.storage.retention.pending-hours} -> purge.</li>
 * </ul>
 * Mỗi file xử lý độc lập (lỗi một file không dừng job); ghi {@code FILE_PURGED} với actor {@code SYSTEM}.
 * Chạy 1 instance nên chưa cần ShedLock.
 * <p>
 * Luồng B.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.storage.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileRetentionJob {

    static final String SYSTEM = "SYSTEM";
    static final int BATCH = 200;
    /** Chặn vòng lặp vô hạn khi purge liên tục lỗi. */
    static final int MAX_PER_RUN = 10_000;

    private final FileAdminRepository fileRepository;
    private final FileLifecycleService lifecycleService;
    private final AuditService auditService;
    private final FileStorageProperties storageProperties;
    private final Clock clock;

    @Autowired
    public FileRetentionJob(FileAdminRepository fileRepository, FileLifecycleService lifecycleService,
                            AuditService auditService, FileStorageProperties storageProperties) {
        this(fileRepository, lifecycleService, auditService, storageProperties, Clock.systemDefaultZone());
    }

    FileRetentionJob(FileAdminRepository fileRepository, FileLifecycleService lifecycleService,
                     AuditService auditService, FileStorageProperties storageProperties, Clock clock) {
        this.fileRepository = fileRepository;
        this.lifecycleService = lifecycleService;
        this.auditService = auditService;
        this.storageProperties = storageProperties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.storage.jobs.retention-cron:0 30 2 * * *}")
    public void scheduled() {
        Result r = runOnce();
        log.info("FileRetentionJob: purged {} file (trash {}, pending {}), failed {}",
                r.purged(), r.trash(), r.pending(), r.failed());
    }

    /** Một lượt dọn rác; trả số file đã xoá vĩnh viễn / lỗi. */
    public Result runOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        int[] trash = purgeAll(FileConstants.Status.DELETED, "TRASH_EXPIRED",
                after -> fileRepository.findIdsDueForPurge(FileConstants.Status.DELETED, now, after,
                        PageRequest.of(0, BATCH)));
        LocalDateTime pendingCutoff = now.minusHours(storageProperties.getRetention().getPendingHours());
        int[] pending = purgeAll(FileConstants.Status.PENDING, "PENDING_EXPIRED",
                after -> fileRepository.findPendingIdsOlderThan(FileConstants.Status.PENDING, pendingCutoff,
                        after, PageRequest.of(0, BATCH)));
        return new Result(trash[0] + pending[0], trash[0], pending[0], trash[1] + pending[1]);
    }

    private int[] purgeAll(String status, String reason, LongFunction<List<Long>> fetchAfter) {
        int ok = 0;
        int failed = 0;
        long afterId = 0;
        while (ok + failed < MAX_PER_RUN) {
            List<Long> ids = fetchAfter.apply(afterId);
            if (ids.isEmpty()) {
                break;
            }
            for (Long id : ids) {
                afterId = Math.max(afterId, id);
                try {
                    FileEntity purged = lifecycleService.purge(id, reason);
                    ok++;
                    auditService.record(AuditEvent.builder()
                            .action(AuditActions.FILE_PURGED)
                            .resource(AuditActions.RESOURCE_FILE, id)
                            .actor(null, SYSTEM)
                            .actorUserType(SYSTEM)
                            .detail("reason", reason)
                            .detail("fromStatus", status)
                            .detail("module", purged == null ? null : purged.getModuleName())
                            .detail("size", purged == null ? null : purged.getFileSize())
                            .build());
                } catch (RuntimeException ex) {
                    failed++;
                    log.warn("FileRetentionJob: không xoá được file #{} ({}): {}", id, status, ex.getMessage());
                    auditService.record(AuditEvent.builder()
                            .action(AuditActions.FILE_PURGED)
                            .result(AuditResult.FAILURE)
                            .resource(AuditActions.RESOURCE_FILE, id)
                            .actor(null, SYSTEM)
                            .actorUserType(SYSTEM)
                            .detail("reason", reason)
                            .detail("fromStatus", status)
                            .detail("error", ex.getMessage())
                            .build());
                }
            }
        }
        return new int[]{ok, failed};
    }

    public record Result(int purged, int trash, int pending, int failed) {
    }
}
