package com.education.base.service.file;

import com.education.base.common.file.FileAdminErrorCodes;
import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileErrorCodes;
import com.education.base.entity.FileEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.FileRepository;
import com.education.base.security.SecurityUtils;
import com.education.base.storage.BlobStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Cài đặt {@link FileLifecycleService}: chỉ kiểm tra trạng thái (quyền ở tầng gọi), đồng bộ {@code IS_DELETED}.
 * Xoá blob khi purge chỉ chạy SAU KHI transaction commit (rollback thì blob còn nguyên).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileLifecycleServiceImpl implements FileLifecycleService {

    static final String SYSTEM_ACTOR = "SYSTEM";

    private final FileRepository fileRepository;
    private final BlobStore blobStore;
    private final FileModuleRegistry registry;
    private final FileLimitService fileLimitService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileEntity softDelete(Long fileId, String reason) {
        FileEntity f = load(fileId);
        String status = statusOf(f);
        if (!FileConstants.Status.ACTIVE.equals(status) && !FileConstants.Status.PENDING.equals(status)) {
            throw invalidState("Chỉ xoá được file đang hoạt động.");
        }
        LocalDateTime now = LocalDateTime.now();
        int days = fileLimitService.limits(FileModuleRegistry.descriptorCode(f)).trashRetentionDays();
        f.setStatus(FileConstants.Status.DELETED);
        f.setIsDeleted(1);
        f.setDeletedAt(now);
        f.setDeletedBy(actor());
        f.setDeleteReason(truncate(reason));
        f.setPurgeAfter(now.plusDays(days));
        f.setUpdatedBy(actor());
        return fileRepository.save(f);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileEntity restore(Long fileId) {
        FileEntity f = load(fileId);
        if (!FileConstants.Status.DELETED.equals(statusOf(f))) {
            throw invalidState("Chỉ khôi phục được file trong thùng rác.");
        }
        if (!blobExists(f.getFilePath())) {
            throw new OracleBusinessException(FileErrorCodes.FILE_BLOB_MISSING,
                    "Nội dung file không còn trên kho lưu trữ, không thể khôi phục.");
        }
        if (f.getReferenceId() != null && registry.forFile(f)
                .map(d -> !d.referenceActive(f.getReferenceId())).orElse(false)) {
            throw new OracleBusinessException(FileErrorCodes.FILE_REFERENCE_INACTIVE,
                    "Bản ghi gắn file đã bị xoá, không thể khôi phục file.");
        }
        f.setStatus(FileConstants.Status.ACTIVE);
        f.setIsDeleted(0);
        f.setDeletedAt(null);
        f.setDeletedBy(null);
        f.setDeleteReason(null);
        f.setPurgeAfter(null);
        f.setUpdatedBy(actor());
        return fileRepository.save(f);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileEntity purge(Long fileId, String reason) {
        FileEntity f = load(fileId);
        String status = statusOf(f);
        if (!FileConstants.Status.DELETED.equals(status) && !FileConstants.Status.PENDING.equals(status)) {
            throw invalidState("Chỉ xoá vĩnh viễn được file đã ở thùng rác.");
        }
        LocalDateTime now = LocalDateTime.now();
        f.setStatus(FileConstants.Status.PURGED);
        f.setIsDeleted(1);
        f.setPurgedAt(now);
        if (f.getDeletedAt() == null) {
            f.setDeletedAt(now);
            f.setDeletedBy(actor());
        }
        if (reason != null && !reason.isBlank()) {
            f.setDeleteReason(truncate(reason));
        }
        f.setUpdatedBy(actor());
        FileEntity saved = fileRepository.save(f);
        String key = f.getFilePath();
        if (key != null && !key.isBlank()) {
            afterCommit(() -> deleteBlobQuietly(fileId, key));
        }
        return saved;
    }

    // ---------------------------------------------------------------------------------------------

    private boolean blobExists(String key) {
        try {
            return key != null && !key.isBlank() && blobStore.exists(key);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private FileEntity load(Long fileId) {
        return fileRepository.findById(fileId).orElseThrow(() -> new OracleBusinessException(
                FileErrorCodes.FILE_NOT_FOUND, "Không tìm thấy thông tin file với ID: " + fileId));
    }

    static String statusOf(FileEntity f) {
        if (f.getStatus() != null) {
            return f.getStatus();
        }
        return f.getIsDeleted() != null && f.getIsDeleted() == 1
                ? FileConstants.Status.DELETED : FileConstants.Status.ACTIVE;
    }

    private static OracleBusinessException invalidState(String message) {
        return new OracleBusinessException(FileAdminErrorCodes.FILE_INVALID_STATE, message);
    }

    private static String actor() {
        String username = SecurityUtils.currentUser().map(u -> u.getUsername()).orElse(null);
        return username == null || username.isBlank() ? SYSTEM_ACTOR : username;
    }

    static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        String r = reason.strip();
        if (r.isEmpty()) {
            return null;
        }
        return r.length() > FileConstants.MAX_REASON_LENGTH ? r.substring(0, FileConstants.MAX_REASON_LENGTH) : r;
    }

    private void deleteBlobQuietly(Long fileId, String key) {
        try {
            blobStore.delete(key);
        } catch (IOException | RuntimeException e) {
            log.error("Không xoá được blob của file id={} (đối soát sẽ báo file mồ côi): {}", fileId, e.getMessage());
        }
    }

    static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
