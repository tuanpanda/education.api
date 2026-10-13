package com.education.base.service.impl;

import com.education.base.audit.AuditActions;
import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileErrorCodes;
import com.education.base.common.file.FileModuleDescriptor;
import com.education.base.common.file.SafeFileType;
import com.education.base.entity.FileEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.FileRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AuditService;
import com.education.base.service.FileStorageService;
import com.education.base.service.file.FileLimitService;
import com.education.base.service.file.FileModuleRegistry;
import com.education.base.storage.BlobInfo;
import com.education.base.storage.BlobStore;
import com.education.base.storage.StorageKeyStrategy;
import com.education.base.storage.VirusScanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Triển khai {@link FileStorageService} trên {@link BlobStore} (Luồng A).
 * <p>
 * Luồng lưu: module có descriptor (fail-closed) -> bản ghi tham chiếu dùng được -> file rỗng -> giới hạn dung lượng /
 * đuôi của module -> đuôi + magic bytes ({@link SafeFileType}) -> số file / tổng / quota / đĩa trống -> ghi blob nguyên
 * tử + SHA-256 -> quét virus -> metadata (người upload, học sinh sở hữu, checksum) -> audit. Blob được xoá nếu lưu
 * metadata lỗi hoặc transaction bao ngoài rollback (sửa S7).
 */
@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final String DEFAULT_FILE_NAME = "unnamed";
    private static final int MAX_NAME_LENGTH = 255;

    private final FileRepository fileRepository;
    private final BlobStore blobStore;
    private final StorageKeyStrategy keyStrategy;
    private final FileModuleRegistry registry;
    private final FileLimitService limitService;
    private final VirusScanner virusScanner;
    private final AuditService auditService;

    public FileStorageServiceImpl(FileRepository fileRepository, BlobStore blobStore, StorageKeyStrategy keyStrategy, FileModuleRegistry registry,
                                  FileLimitService limitService, VirusScanner virusScanner, AuditService auditService) {
        this.fileRepository = fileRepository;
        this.blobStore = blobStore;
        this.keyStrategy = keyStrategy;
        this.registry = registry;
        this.limitService = limitService;
        this.virusScanner = virusScanner;
        this.auditService = auditService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileEntity storeFile(MultipartFile file, String moduleName, Long referenceId) {
        return storeFile(file, moduleName, referenceId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileEntity storeFile(MultipartFile file, String moduleName, Long referenceId, String subFolder) {
        String original = file == null ? null : file.getOriginalFilename();
        return store(new Upload() {
            public long size() {
                return file == null ? 0 : file.getSize();
            }

            public InputStream open() throws IOException {
                return file.getInputStream();
            }
        }, original, moduleName, referenceId, subFolder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileEntity storeBytes(byte[] content, String originalFilename, String contentType,
                                 String moduleName, Long referenceId, String subFolder) {
        return store(new Upload() {
            public long size() {
                return content == null ? 0 : content.length;
            }

            public InputStream open() {
                return new ByteArrayInputStream(content);
            }
        }, originalFilename, moduleName, referenceId, subFolder);
    }

    private interface Upload {
        long size();

        InputStream open() throws IOException;
    }

    private FileEntity store(Upload upload, String originalFilename, String moduleName, Long referenceId,
                             String subFolder) {
        String module = normalizeModule(moduleName);
        String sub = normalizeSubFolder(subFolder);
        String code = FileModuleRegistry.descriptorCode(module, sub);
        String ext = SafeFileType.extensionOfName(originalFilename);
        try {
            FileModuleDescriptor descriptor = registry.require(code);
            limitService.requireActive(code);
            if (referenceId == null && descriptor.referenceRequired()) {
                throw new OracleBusinessException(FileErrorCodes.FILE_REFERENCE_REQUIRED,
                        "Vui lòng chọn bản ghi (referenceId) để đính kèm file cho module " + code + ".");
            }
            if (referenceId != null) {
                descriptor.assertReferenceUsable(referenceId);
            }
            if (upload.size() <= 0) {
                throw new OracleBusinessException(FileErrorCodes.FILE_EMPTY, "File tải lên không được để trống.");
            }
            limitService.checkSizeAndType(code, upload.size(), ext);
            SafeFileType type = SafeFileType.detect(originalFilename, readHeader(upload));
            Long owner = referenceId == null ? null : descriptor.ownerStudentId(referenceId).orElse(null);
            limitService.checkCapacity(code, module, referenceId, upload.size(), owner);
            return writeAndSave(upload, originalFilename, module, sub, referenceId, owner, type, ext);
        } catch (OracleBusinessException e) {
            if (!FileErrorCodes.FILE_INFECTED.equals(e.getErrorCode())) {
                auditRejected(e, code, referenceId, upload.size(), ext);
            }
            throw e;
        }
    }

    private FileEntity writeAndSave(Upload upload, String originalFilename, String module, String sub,
                                    Long referenceId, Long owner, SafeFileType type, String ext) {
        String extension = ext == null ? null : ext.toLowerCase(Locale.ROOT);
        StorageKeyStrategy.StorageKey key = keyStrategy.newKey(module, sub, extension);
        BlobInfo blob;
        try (InputStream in = upload.open()) {
            blob = blobStore.put(key.key(), in, upload.size());
        } catch (IOException | RuntimeException e) {
            log.error("Lỗi khi ghi blob '{}': {}", key.key(), e.getMessage(), e);
            throw new OracleBusinessException(FileErrorCodes.FILE_STORE_ERROR, "Không thể lưu file vào hệ thống.");
        }
        boolean registered = registerRollbackCleanup(key.key());
        try {
            VirusScanner.ScanResult scan = scan(key.key(), originalFilename);
            if (FileConstants.ScanStatus.INFECTED.equals(scan.status())) {
                quarantineQuietly(key.key());
                auditService.denied(AuditActions.FILE_INFECTED, AuditActions.RESOURCE_FILE, null,
                        details(module, referenceId, blob.size(), extension, Map.of("detail",
                                scan.detail() == null ? "" : scan.detail())));
                throw new OracleBusinessException(FileErrorCodes.FILE_INFECTED,
                        "File có dấu hiệu chứa mã độc và đã bị từ chối.");
            }
            AuthUserPrincipal user = SecurityUtils.currentUser().orElse(null);
            FileEntity saved = fileRepository.save(FileEntity.builder()
                    .originalName(cleanOriginalName(originalFilename))
                    .storedName(key.storedName())
                    .filePath(key.key())
                    .contentType(type.mimeType())
                    .fileSize(blob.size())
                    .moduleName(module)
                    .referenceId(referenceId)
                    .isDeleted(0)
                    .status(FileConstants.Status.ACTIVE)
                    .createdBy(user == null ? null : user.getUsername())
                    .uploadedByUserId(user == null ? null : user.getId())
                    .uploadedByType(uploaderType(user))
                    .ownerStudentId(owner)
                    .fileExt(extension)
                    .checksumSha256(blob.checksumSha256())
                    .storageBackend(blob.backend())
                    .scanStatus(scan.status())
                    .scanDetail(scan.detail())
                    .scannedAt(FileConstants.ScanStatus.SKIPPED.equals(scan.status()) ? null : LocalDateTime.now())
                    .build());
            log.info("Đã lưu file id={} module={} key='{}' size={}", saved.getId(), module, key.key(), blob.size());
            auditService.success(AuditActions.FILE_UPLOADED, AuditActions.RESOURCE_FILE, saved.getId(),
                    details(module, referenceId, blob.size(), extension, Map.of()));
            return saved;
        } catch (RuntimeException e) {
            if (!registered) {
                deleteBlobQuietly(key.key());
            }
            throw e;
        }
    }

    /**
     * Đăng ký xoá blob khi transaction không commit. Trả {@code false} nếu không có transaction (caller tự dọn khi lỗi).
     */
    private boolean registerRollbackCleanup(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    log.warn("Transaction không commit - xoá blob '{}'.", key);
                    deleteBlobQuietly(key);
                }
            }
        });
        return true;
    }

    private VirusScanner.ScanResult scan(String key, String displayName) {
        try (InputStream in = blobStore.open(key)) {
            VirusScanner.ScanResult r = virusScanner.scan(in, displayName);
            return r == null ? VirusScanner.ScanResult.skipped() : r;
        } catch (IOException e) {
            log.error("Lỗi quét virus blob '{}': {}", key, e.getMessage());
            return new VirusScanner.ScanResult(FileConstants.ScanStatus.ERROR, truncate(e.getMessage(), 255));
        }
    }

    private byte[] readHeader(Upload upload) {
        try (InputStream in = upload.open()) {
            return in.readNBytes(SafeFileType.HEADER_BYTES);
        } catch (IOException e) {
            log.error("Không đọc được nội dung file upload: {}", e.getMessage(), e);
            throw new OracleBusinessException(FileErrorCodes.FILE_READ_ERROR, "Không đọc được nội dung file tải lên.");
        }
    }

    @Override
    public Resource loadFileAsResource(Long fileId) {
        return loadResource(getFileEntity(fileId));
    }

    @Override
    public Resource loadResource(FileEntity entity) {
        if (FileConstants.Status.PURGED.equals(entity.getStatus())) {
            throw new OracleBusinessException(FileErrorCodes.FILE_NOT_FOUND, "File đã bị xoá vĩnh viễn.");
        }
        String key = entity.getFilePath();
        if (key == null || key.isBlank()) {
            throw new OracleBusinessException(FileErrorCodes.FILE_PATH_ERROR, "Đường dẫn file không hợp lệ.");
        }
        try {
            if (!blobStore.exists(key)) {
                throw new OracleBusinessException(FileErrorCodes.FILE_BLOB_MISSING,
                        "Không tìm thấy nội dung file trên hệ thống lưu trữ.");
            }
            return blobStore.resource(key);
        } catch (IllegalArgumentException e) {
            log.error("Đường dẫn file không hợp lệ cho id={}: {}", entity.getId(), e.getMessage());
            throw new OracleBusinessException(FileErrorCodes.FILE_PATH_ERROR, "Đường dẫn file không hợp lệ.");
        } catch (IOException e) {
            throw new OracleBusinessException(FileErrorCodes.FILE_BLOB_MISSING,
                    "Không tìm thấy nội dung file trên hệ thống lưu trữ.");
        }
    }

    @Override
    public FileEntity getFileEntity(Long fileId) {
        return fileRepository.findById(fileId)
                .filter(FileStorageServiceImpl::isActive)
                .orElseThrow(() -> new OracleBusinessException(
                        FileErrorCodes.FILE_NOT_FOUND, "Không tìm thấy thông tin file với ID: " + fileId));
    }

    static boolean isActive(FileEntity f) {
        if (f.getIsDeleted() != null && f.getIsDeleted() != 0) {
            return false;
        }
        return f.getStatus() == null || FileConstants.Status.ACTIVE.equals(f.getStatus());
    }

    @Override
    public java.util.Map<Long, Long> countActiveFilesByRefs(String moduleName, java.util.Collection<Long> referenceIds) {
        java.util.Map<Long, Long> out = new java.util.HashMap<>();
        if (referenceIds == null || referenceIds.isEmpty()) {
            return out;
        }
        List<Long> ids = new java.util.ArrayList<>(referenceIds);
        for (int i = 0; i < ids.size(); i += 900) { // Oracle IN toi da 1000 phan tu
            for (Object[] row : fileRepository.countActiveByRefs(normalizeModule(moduleName),
                    ids.subList(i, Math.min(i + 900, ids.size())))) {
                out.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
        }
        return out;
    }

    @Override
    public List<FileEntity> getFilesByRef(String moduleName, Long referenceId) {
        return fileRepository.getFilesByRef(normalizeModule(moduleName), referenceId);
    }

    // ---------------------------------------------------------------------------------------------

    private void auditRejected(OracleBusinessException e, String module, Long referenceId, long size, String ext) {
        auditService.failure(AuditActions.FILE_UPLOAD_REJECTED, AuditActions.RESOURCE_FILE, null,
                details(module, referenceId, size, ext, Map.of("reason", rejectReason(e.getErrorCode()),
                        "code", e.getErrorCode())));
    }

    static String rejectReason(String code) {
        if (code == null) {
            return "OTHER";
        }
        return switch (code) {
            case FileErrorCodes.FILE_TYPE_NOT_ALLOWED, FileErrorCodes.FILE_CONTENT_MISMATCH,
                 FileErrorCodes.FILE_TYPE_INVALID -> "TYPE";
            case FileErrorCodes.FILE_TOO_LARGE, FileErrorCodes.FILE_EMPTY -> "SIZE";
            case FileErrorCodes.FILE_LIMIT_COUNT -> "COUNT";
            case FileErrorCodes.FILE_LIMIT_TOTAL -> "TOTAL";
            case FileErrorCodes.FILE_QUOTA_EXCEEDED -> "QUOTA";
            case FileErrorCodes.STORAGE_LOW_DISK -> "DISK";
            case FileErrorCodes.FILE_INFECTED -> "VIRUS";
            case FileErrorCodes.FILE_REFERENCE_REQUIRED, FileErrorCodes.FILE_REFERENCE_NOT_FOUND,
                 FileErrorCodes.FILE_REFERENCE_INACTIVE -> "REFERENCE";
            case FileErrorCodes.INVALID_MODULE, FileErrorCodes.FILE_MODULE_DISABLED -> "MODULE";
            default -> "OTHER";
        };
    }

    private static Map<String, Object> details(String module, Long referenceId, long size, String ext,
                                               Map<String, ?> extra) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("module", module);
        d.put("refId", referenceId);
        d.put("size", size);
        d.put("ext", ext);
        d.putAll(extra);
        return d;
    }

    private static String uploaderType(AuthUserPrincipal user) {
        if (user == null) {
            return FileConstants.UploaderType.SYSTEM;
        }
        return user.isStaff() ? FileConstants.UploaderType.STAFF : FileConstants.UploaderType.STUDENT;
    }

    private void deleteBlobQuietly(String key) {
        try {
            blobStore.delete(key);
        } catch (IOException | RuntimeException e) {
            log.error("Không thể xoá blob '{}' sau khi lưu metadata thất bại: {}", key, e.getMessage(), e);
        }
    }

    private void quarantineQuietly(String key) {
        try {
            blobStore.moveToQuarantine(key);
        } catch (IOException | RuntimeException e) {
            log.error("Không cách ly được blob nhiễm virus '{}', xoá luôn: {}", key, e.getMessage());
            deleteBlobQuietly(key);
        }
    }

    private String normalizeModule(String moduleName) {
        String module = FileModuleRegistry.normalize(moduleName);
        if (!module.matches("[A-Z0-9_]{1,50}")) {
            throw new OracleBusinessException(FileErrorCodes.INVALID_MODULE,
                    "Tên module chỉ được chứa chữ, số và gạch dưới.");
        }
        return module;
    }

    private String normalizeSubFolder(String subFolder) {
        if (subFolder == null || subFolder.isBlank()) {
            return null;
        }
        String folder = subFolder.trim().toUpperCase(Locale.ROOT);
        if (!folder.matches("[A-Z0-9_]{1,50}")) {
            throw new OracleBusinessException(FileErrorCodes.INVALID_MODULE,
                    "Thư mục con chỉ được chứa chữ, số và gạch dưới.");
        }
        return folder;
    }

    /**
     * Tên gốc hiển thị (sửa S8): giữ Unicode (chuẩn hoá NFC), bỏ thành phần thư mục, ký tự điều khiển và {@code / \},
     * cắt tối đa 255 ký tự (giữ đuôi).
     */
    static String cleanOriginalName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return DEFAULT_FILE_NAME;
        }
        String name = originalFilename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder(name.length());
        name.codePoints()
                .filter(cp -> !Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT
                        && cp != '/' && cp != '\\' && cp != '"')
                .forEach(sb::appendCodePoint);
        name = sb.toString().strip();
        if (name.isEmpty() || name.replace(".", "").isBlank()) {
            return DEFAULT_FILE_NAME;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 11 ? name.substring(dot) : "";
            name = name.substring(0, MAX_NAME_LENGTH - ext.length()) + ext;
        }
        return name;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
