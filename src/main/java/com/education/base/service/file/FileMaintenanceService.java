package com.education.base.service.file;

import com.education.base.audit.AuditActions;
import com.education.base.audit.AuditEvent;
import com.education.base.common.file.FileAdminErrorCodes;
import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileModuleDescriptor;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.config.FileStorageProperties;
import com.education.base.config.SchedulingConfig;
import com.education.base.dto.request.FileScanResolveRequest;
import com.education.base.dto.response.FileScanItemDto;
import com.education.base.dto.response.FileScanRunDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.entity.FileEntity;
import com.education.base.entity.FileScanItemEntity;
import com.education.base.entity.FileScanRunEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.FileAdminRepository;
import com.education.base.repository.FileScanItemRepository;
import com.education.base.repository.FileScanRunRepository;
import com.education.base.security.Permissions;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AuditService;
import com.education.base.storage.BlobEntry;
import com.education.base.storage.BlobStore;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Đối soát DB {@code SYS_ATTACHED_FILES} &lt;-&gt; kho blob và tính checksum cho file cũ. Chạy nền trên executor
 * 1 luồng, tối đa một đợt {@code RUNNING} (unique index {@code UQ_FILE_SCAN_RUNS_RUNNING}).
 * <p>
 * <b>Chỉ báo cáo, không bao giờ tự xoá</b>: mọi thay đổi blob / metadata chỉ xảy ra khi admin xử lý từng mục
 * ({@link #resolveItem}).
 * <p>
 * Luồng B.
 */
@Slf4j
@Service
public class FileMaintenanceService {

    static final List<String> LIVE_STATUSES = List.of(FileConstants.Status.ACTIVE, FileConstants.Status.PENDING,
            FileConstants.Status.DELETED);
    static final int BATCH = 500;
    /** Đợt RUNNING quá lâu (app chết giữa chừng) được đánh dấu FAILED để không khoá vĩnh viễn. */
    static final Duration STALE_RUN = Duration.ofHours(6);
    static final String MISSING_REASON = "Mất file (đối soát #%d)";

    private final FileScanRunRepository runRepository;
    private final FileScanItemRepository itemRepository;
    private final FileAdminRepository fileRepository;
    private final BlobStore blobStore;
    private final FileLifecycleService lifecycleService;
    private final AuditService auditService;
    private final FileStorageProperties storageProperties;
    private final Map<String, FileModuleDescriptor> descriptors;
    private final TaskExecutor executor;
    private final Clock clock;

    @Autowired
    public FileMaintenanceService(FileScanRunRepository runRepository, FileScanItemRepository itemRepository,
                                  FileAdminRepository fileRepository, BlobStore blobStore,
                                  FileLifecycleService lifecycleService, AuditService auditService,
                                  FileStorageProperties storageProperties, List<FileModuleDescriptor> descriptors,
                                  @Qualifier(SchedulingConfig.FILE_MAINTENANCE_EXECUTOR) TaskExecutor executor) {
        this(runRepository, itemRepository, fileRepository, blobStore, lifecycleService, auditService,
                storageProperties, descriptors, executor, Clock.systemDefaultZone());
    }

    FileMaintenanceService(FileScanRunRepository runRepository, FileScanItemRepository itemRepository,
                           FileAdminRepository fileRepository, BlobStore blobStore,
                           FileLifecycleService lifecycleService, AuditService auditService,
                           FileStorageProperties storageProperties, List<FileModuleDescriptor> descriptors,
                           TaskExecutor executor, Clock clock) {
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        this.fileRepository = fileRepository;
        this.blobStore = blobStore;
        this.lifecycleService = lifecycleService;
        this.auditService = auditService;
        this.storageProperties = storageProperties;
        this.descriptors = descriptors == null ? Map.of() : descriptors.stream()
                .collect(Collectors.toMap(d -> d.code().toUpperCase(), Function.identity(), (a, b) -> a));
        this.executor = executor;
        this.clock = clock;
    }

    // ---- Khởi chạy ---------------------------------------------------------------------------------

    public FileScanRunDto startOrphanScan() {
        FileScanRunEntity run = createRun(FileConstants.Scan.RUN_ORPHAN);
        auditService.record(AuditEvent.builder()
                .action(AuditActions.FILE_SCAN_STARTED)
                .resource(AuditActions.RESOURCE_FILE_SCAN_RUN, run.getId())
                .detail("runType", run.getRunType())
                .build());
        Long runId = run.getId();
        FileScanRunDto dto = toDto(run, 0);
        executor.execute(() -> runOrphanScan(runId));
        return dto;
    }

    public FileScanRunDto startChecksumBackfill() {
        FileScanRunEntity run = createRun(FileConstants.Scan.RUN_CHECKSUM);
        auditService.record(AuditEvent.builder()
                .action(AuditActions.FILE_CHECKSUM_BACKFILL_STARTED)
                .resource(AuditActions.RESOURCE_FILE_SCAN_RUN, run.getId())
                .detail("runType", run.getRunType())
                .build());
        Long runId = run.getId();
        FileScanRunDto dto = toDto(run, 0);
        executor.execute(() -> runChecksumBackfill(runId));
        return dto;
    }

    private FileScanRunEntity createRun(String type) {
        LocalDateTime now = now();
        for (FileScanRunEntity running : runRepository.findByStatus(FileConstants.Scan.RUN_RUNNING)) {
            if (running.getStartedAt() != null && running.getStartedAt().isBefore(now.minus(STALE_RUN))) {
                running.setStatus(FileConstants.Scan.RUN_FAILED);
                running.setFinishedAt(now);
                running.setDetail(truncate("Đợt quét bị treo quá " + STALE_RUN.toHours() + " giờ - tự đánh dấu FAILED",
                        4000));
                runRepository.save(running);
            } else {
                throw scanRunning();
            }
        }
        try {
            return runRepository.saveAndFlush(FileScanRunEntity.builder()
                    .runType(type)
                    .status(FileConstants.Scan.RUN_RUNNING)
                    .startedAt(now)
                    .startedBy(SecurityUtils.currentUsername())
                    .build());
        } catch (DataIntegrityViolationException ex) {
            throw scanRunning();
        }
    }

    private static OracleBusinessException scanRunning() {
        return new OracleBusinessException(FileAdminErrorCodes.FILE_SCAN_RUNNING,
                "Đang có một đợt quét chạy, vui lòng đợi hoàn tất.");
    }

    // ---- Đối soát mồ côi -----------------------------------------------------------------------------

    /** Chạy đồng bộ (gọi từ executor). Không ném lỗi ra ngoài: lỗi -> đợt FAILED. */
    void runOrphanScan(Long runId) {
        FileScanRunEntity run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            return;
        }
        try {
            Map<String, FileEntity> byKey = new HashMap<>();
            long dbRows = 0;
            long afterId = 0;
            while (true) {
                List<FileEntity> page = fileRepository.findByStatusInAndIdGreaterThanOrderByIdAsc(
                        LIVE_STATUSES, afterId, PageRequest.of(0, BATCH));
                if (page.isEmpty()) {
                    break;
                }
                for (FileEntity f : page) {
                    dbRows++;
                    if (f.getFilePath() != null) {
                        byKey.put(normalizeKey(f.getFilePath()), f);
                    }
                }
                afterId = page.get(page.size() - 1).getId();
            }

            List<FileScanItemEntity> items = new ArrayList<>();
            Map<String, Boolean> seen = new HashMap<>();
            LocalDateTime graceCutoff = now().minusHours(storageProperties.getRetention().getOrphanGraceHours());
            long blobs = 0;
            long orphan = 0;
            long mismatch = 0;
            try (Stream<BlobEntry> stream = blobStore.list("")) {
                for (BlobEntry blob : (Iterable<BlobEntry>) stream::iterator) {
                    blobs++;
                    String key = normalizeKey(blob.key());
                    FileEntity file = byKey.get(key);
                    LocalDateTime mtime = blob.lastModified() == null ? null
                            : LocalDateTime.ofInstant(blob.lastModified(), ZoneId.systemDefault());
                    if (file != null) {
                        seen.put(key, Boolean.TRUE);
                        if (file.getFileSize() != null && file.getFileSize() != blob.size()) {
                            mismatch++;
                            items.add(item(runId, FileConstants.Scan.ITEM_SIZE_MISMATCH, file, blob.key(),
                                    blob.size(), mtime));
                        }
                    } else if (mtime == null || mtime.isBefore(graceCutoff)) {
                        orphan++;
                        items.add(item(runId, FileConstants.Scan.ITEM_ORPHAN_BLOB, null, blob.key(), blob.size(),
                                mtime));
                    }
                    flushIfFull(items);
                }
            }

            long missing = 0;
            for (Map.Entry<String, FileEntity> e : byKey.entrySet()) {
                if (!seen.containsKey(e.getKey())) {
                    missing++;
                    items.add(item(runId, FileConstants.Scan.ITEM_MISSING_BLOB, e.getValue(),
                            e.getValue().getFilePath(), null, null));
                    flushIfFull(items);
                }
            }

            // Tham chiếu nghiệp vụ đã xoá / không tồn tại (chỉ file ACTIVE; chỉ báo cáo).
            Map<String, FileReferenceInfo> refCache = new HashMap<>();
            for (FileEntity f : byKey.values()) {
                if (!FileConstants.Status.ACTIVE.equals(f.getStatus()) || f.getReferenceId() == null) {
                    continue;
                }
                FileReferenceInfo info = refCache.computeIfAbsent(f.getModuleName() + ":" + f.getReferenceId(),
                        k -> describe(f.getModuleName(), f.getReferenceId()));
                if (info == null) {
                    continue;
                }
                if (FileConstants.RefState.DELETED.equals(info.state())) {
                    items.add(item(runId, FileConstants.Scan.ITEM_REF_DELETED, f, f.getFilePath(), null, null));
                } else if (FileConstants.RefState.NOT_FOUND.equals(info.state())) {
                    items.add(item(runId, FileConstants.Scan.ITEM_REF_NOT_FOUND, f, f.getFilePath(), null, null));
                }
                flushIfFull(items);
            }
            if (!items.isEmpty()) {
                itemRepository.saveAll(items);
            }

            run.setDbRowsChecked(dbRows);
            run.setBlobsChecked(blobs);
            run.setMissingBlobCount(missing);
            run.setOrphanBlobCount(orphan);
            run.setMismatchCount(mismatch);
            finish(run, FileConstants.Scan.RUN_DONE, null);
        } catch (Exception ex) {
            log.error("Đối soát file #{} thất bại", runId, ex);
            finish(run, FileConstants.Scan.RUN_FAILED, "Lỗi: " + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
        }
    }

    // ---- Tính checksum file cũ -------------------------------------------------------------------------

    void runChecksumBackfill(Long runId) {
        FileScanRunEntity run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            return;
        }
        try {
            long rows = 0;
            long blobs = 0;
            long missing = 0;
            long mismatch = 0;
            long filled = 0;
            long afterId = 0;
            List<String> statuses = List.of(FileConstants.Status.ACTIVE, FileConstants.Status.DELETED);
            List<FileScanItemEntity> items = new ArrayList<>();
            while (true) {
                List<FileEntity> page = fileRepository
                        .findByStatusInAndChecksumSha256IsNullAndIdGreaterThanOrderByIdAsc(
                                statuses, afterId, PageRequest.of(0, BATCH));
                if (page.isEmpty()) {
                    break;
                }
                for (FileEntity f : page) {
                    rows++;
                    String key = f.getFilePath();
                    if (key == null || !blobStore.exists(key)) {
                        missing++;
                        items.add(item(runId, FileConstants.Scan.ITEM_MISSING_BLOB, f, key, null, null));
                        continue;
                    }
                    blobs++;
                    Digest digest = sha256(key);
                    if (f.getFileSize() != null && f.getFileSize() != digest.size()) {
                        mismatch++;
                        items.add(item(runId, FileConstants.Scan.ITEM_SIZE_MISMATCH, f, key, digest.size(), null));
                        continue;
                    }
                    filled += fileRepository.fillChecksum(f.getId(), digest.hex());
                    flushIfFull(items);
                }
                afterId = page.get(page.size() - 1).getId();
            }
            if (!items.isEmpty()) {
                itemRepository.saveAll(items);
            }
            run.setDbRowsChecked(rows);
            run.setBlobsChecked(blobs);
            run.setMissingBlobCount(missing);
            run.setMismatchCount(mismatch);
            finish(run, FileConstants.Scan.RUN_DONE, "Đã tính checksum cho " + filled + " file");
        } catch (Exception ex) {
            log.error("Tính checksum #{} thất bại", runId, ex);
            finish(run, FileConstants.Scan.RUN_FAILED, "Lỗi: " + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
        }
    }

    private Digest sha256(String key) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        long size = 0;
        try (InputStream in = new DigestInputStream(blobStore.open(key), md)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                size += n;
            }
        }
        return new Digest(HexFormat.of().formatHex(md.digest()), size);
    }

    private record Digest(String hex, long size) {
    }

    // ---- Xử lý mục -----------------------------------------------------------------------------------

    public FileScanItemDto resolveItem(Long runId, Long itemId, FileScanResolveRequest request) {
        FileScanItemEntity item = itemRepository.findById(itemId)
                .filter(i -> i.getRunId().equals(runId))
                .orElseThrow(() -> new OracleBusinessException(FileAdminErrorCodes.FILE_SCAN_ITEM_NOT_FOUND,
                        "Không tìm thấy mục đối soát."));
        if (item.getResolution() != null) {
            throw new OracleBusinessException(FileAdminErrorCodes.FILE_SCAN_ITEM_RESOLVED, "Mục này đã được xử lý.");
        }
        String action = request.getAction();
        if (!allowedActions(item.getItemType()).contains(action)) {
            throw new OracleBusinessException("VALIDATION_ERROR",
                    "Hành động " + action + " không áp dụng cho loại " + item.getItemType() + ".");
        }
        String resolution;
        try {
            switch (action) {
                case "MARK_MISSING" -> {
                    FileEntity file = item.getFileId() == null ? null
                            : fileRepository.findById(item.getFileId()).orElse(null);
                    if (file != null && FileConstants.Status.ACTIVE.equals(file.getStatus())) {
                        lifecycleService.softDelete(file.getId(), String.format(MISSING_REASON, runId));
                    }
                    resolution = FileConstants.Scan.RESOLUTION_MARKED_MISSING;
                }
                case "QUARANTINE" -> {
                    assertStillOrphan(item.getStorageKey());
                    blobStore.moveToQuarantine(item.getStorageKey());
                    resolution = FileConstants.Scan.RESOLUTION_QUARANTINED;
                }
                case "DELETE_BLOB" -> {
                    if (!SecurityUtils.currentUserHasPermission(Permissions.FILE_MANAGER_PURGE)) {
                        throw new ForbiddenException("FORBIDDEN", "Cần quyền xoá vĩnh viễn để xoá blob.");
                    }
                    assertStillOrphan(item.getStorageKey());
                    blobStore.delete(item.getStorageKey());
                    resolution = FileConstants.Scan.RESOLUTION_BLOB_DELETED;
                }
                default -> resolution = FileConstants.Scan.RESOLUTION_IGNORED;
            }
        } catch (IOException ex) {
            throw new OracleBusinessException("FILE_STORAGE_ERROR", "Lỗi thao tác kho lưu trữ: " + ex.getMessage(), ex);
        }
        item.setResolution(resolution);
        item.setResolvedAt(now());
        item.setResolvedBy(SecurityUtils.currentUsername());
        item.setNote(truncate(request.getNote(), 500));
        FileScanItemEntity saved = itemRepository.save(item);
        auditService.record(AuditEvent.builder()
                .action(AuditActions.FILE_SCAN_ITEM_RESOLVED)
                .resource(AuditActions.RESOURCE_FILE_SCAN_RUN, runId)
                .detail("itemId", itemId)
                .detail("itemType", item.getItemType())
                .detail("action", action)
                .detail("fileId", item.getFileId())
                .detail("storageKey", item.getStorageKey())
                .build());
        return toItemDto(saved, item.getFileId() == null ? null
                : fileRepository.findById(item.getFileId()).orElse(null));
    }

    static List<String> allowedActions(String itemType) {
        return switch (itemType) {
            case FileConstants.Scan.ITEM_MISSING_BLOB -> List.of("MARK_MISSING", "IGNORE");
            case FileConstants.Scan.ITEM_ORPHAN_BLOB -> List.of("QUARANTINE", "DELETE_BLOB", "IGNORE");
            default -> List.of("IGNORE");
        };
    }

    /** Chặn race: blob vừa được một dòng metadata mới nhận sau lúc quét -> không động vào. */
    private void assertStillOrphan(String key) {
        if (key == null || fileRepository.countLiveByFilePath(key, key.replace('/', '\\')) > 0) {
            throw new OracleBusinessException(FileAdminErrorCodes.FILE_INVALID_STATE,
                    "Blob này hiện đã được một file sử dụng - hãy chạy đối soát lại.");
        }
    }

    // ---- Đọc -----------------------------------------------------------------------------------------

    public PageResponse<FileScanRunDto> listRuns(int pageNo, int pageSize) {
        Page<FileScanRunEntity> page = runRepository.findAllByOrderByStartedAtDescIdDesc(
                PageRequest.of(pageNo - 1, pageSize));
        return PageResponse.of(page.getContent().stream()
                        .map(r -> toDto(r, itemRepository.countByRunIdAndResolutionIsNull(r.getId())))
                        .toList(),
                pageNo, pageSize, page.getTotalElements());
    }

    public FileScanRunDto getRun(Long runId) {
        FileScanRunEntity run = requireRun(runId);
        return toDto(run, itemRepository.countByRunIdAndResolutionIsNull(runId));
    }

    public PageResponse<FileScanItemDto> listItems(Long runId, String itemType, Boolean resolved,
                                                   int pageNo, int pageSize) {
        requireRun(runId);
        Specification<FileScanItemEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("runId"), runId));
            if (itemType != null && !itemType.isBlank()) {
                p.add(cb.equal(root.get("itemType"), itemType.trim().toUpperCase()));
            }
            if (resolved != null) {
                p.add(resolved ? cb.isNotNull(root.get("resolution")) : cb.isNull(root.get("resolution")));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<FileScanItemEntity> page = itemRepository.findAll(spec,
                PageRequest.of(pageNo - 1, pageSize, Sort.by("id")));
        List<Long> fileIds = page.getContent().stream().map(FileScanItemEntity::getFileId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, FileEntity> files = fileIds.isEmpty() ? Map.of()
                : fileRepository.findAllById(fileIds).stream()
                .collect(Collectors.toMap(FileEntity::getId, Function.identity()));
        return PageResponse.of(page.getContent().stream()
                        .map(i -> toItemDto(i, i.getFileId() == null ? null : files.get(i.getFileId())))
                        .toList(),
                pageNo, pageSize, page.getTotalElements());
    }

    private FileScanRunEntity requireRun(Long runId) {
        return runRepository.findById(runId).orElseThrow(() -> new OracleBusinessException(
                FileAdminErrorCodes.FILE_SCAN_RUN_NOT_FOUND, "Không tìm thấy đợt quét."));
    }

    // ---- Tiện ích ------------------------------------------------------------------------------------

    private void flushIfFull(List<FileScanItemEntity> items) {
        if (items.size() >= BATCH) {
            itemRepository.saveAll(items);
            items.clear();
        }
    }

    private void finish(FileScanRunEntity run, String status, String detail) {
        run.setStatus(status);
        run.setFinishedAt(now());
        run.setDetail(truncate(detail, 4000));
        try {
            runRepository.save(run);
        } catch (RuntimeException ex) {
            log.error("Không cập nhật được trạng thái đợt quét #{}", run.getId(), ex);
        }
    }

    private FileReferenceInfo describe(String module, Long referenceId) {
        FileModuleDescriptor d = module == null ? null : descriptors.get(module.toUpperCase());
        if (d == null) {
            return null;
        }
        try {
            return d.describeReference(referenceId);
        } catch (RuntimeException ex) {
            log.debug("describeReference {}#{} lỗi: {}", module, referenceId, ex.getMessage());
            return null;
        }
    }

    /** Dữ liệu cũ có thể lưu FILE_PATH bằng '\' (Windows); so khớp bằng '/'. */
    static String normalizeKey(String key) {
        String k = key.replace('\\', '/');
        while (k.startsWith("/")) {
            k = k.substring(1);
        }
        return k;
    }

    private static FileScanItemEntity item(Long runId, String type, FileEntity file, String key, Long blobSize,
                                           LocalDateTime mtime) {
        return FileScanItemEntity.builder()
                .runId(runId)
                .itemType(type)
                .fileId(file == null ? null : file.getId())
                .storageKey(truncate(key, 500))
                .dbSize(file == null ? null : file.getFileSize())
                .blobSize(blobSize)
                .blobMtime(mtime)
                .build();
    }

    static FileScanRunDto toDto(FileScanRunEntity r, long unresolved) {
        return FileScanRunDto.builder()
                .id(r.getId())
                .runType(r.getRunType())
                .status(r.getStatus())
                .startedAt(r.getStartedAt())
                .finishedAt(r.getFinishedAt())
                .startedBy(r.getStartedBy())
                .dbRowsChecked(nz(r.getDbRowsChecked()))
                .blobsChecked(nz(r.getBlobsChecked()))
                .missingBlobCount(nz(r.getMissingBlobCount()))
                .orphanBlobCount(nz(r.getOrphanBlobCount()))
                .mismatchCount(nz(r.getMismatchCount()))
                .unresolvedCount(unresolved)
                .detail(r.getDetail())
                .build();
    }

    static FileScanItemDto toItemDto(FileScanItemEntity i, FileEntity f) {
        return FileScanItemDto.builder()
                .id(i.getId())
                .runId(i.getRunId())
                .itemType(i.getItemType())
                .fileId(i.getFileId())
                .originalName(f == null ? null : f.getOriginalName())
                .moduleName(f == null ? null : f.getModuleName())
                .referenceId(f == null ? null : f.getReferenceId())
                .storageKey(i.getStorageKey())
                .dbSize(i.getDbSize())
                .blobSize(i.getBlobSize())
                .blobModifiedAt(i.getBlobMtime())
                .resolution(i.getResolution())
                .resolvedAt(i.getResolvedAt())
                .resolvedBy(i.getResolvedBy())
                .build();
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
