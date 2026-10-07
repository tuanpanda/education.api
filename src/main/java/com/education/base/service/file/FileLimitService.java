package com.education.base.service.file;

import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileErrorCodes;
import com.education.base.common.file.FileModuleDescriptor;
import com.education.base.common.file.SafeFileType;
import com.education.base.config.FileStorageProperties;
import com.education.base.dto.response.FileModuleSettingDto;
import com.education.base.dto.response.FileQuotaDto;
import com.education.base.dto.response.FileUploadPolicyDto;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.FileRepository;
import com.education.base.storage.BlobStore;
import com.education.base.storage.StorageUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Giới hạn upload theo module + quota + đĩa trống (QĐ-F4, F8).
 * <p>
 * Nguồn: {@link FileSettingsSource} (Luồng B, bảng V19_2) nếu có bean, cache 60 giây; thiếu bean / thiếu dòng thì
 * dùng mặc định {@code app.storage.defaults.*} / {@code app.storage.quota.*} (+ mặc định riêng theo module ở đây).
 */
@Slf4j
@Service
public class FileLimitService {

    static final long CACHE_TTL_MILLIS = 60_000L;
    private static final long MB = 1024L * 1024L;

    /** Trạng thái có blob trên đĩa (tính quota). */
    static final List<String> STORED_STATUSES = List.of(
            FileConstants.Status.ACTIVE, FileConstants.Status.PENDING, FileConstants.Status.DELETED);
    /** Trạng thái tính số file / tổng trên một bản ghi. */
    static final List<String> LIVE_STATUSES = List.of(FileConstants.Status.ACTIVE, FileConstants.Status.PENDING);

    private final FileStorageProperties properties;
    private final ObjectProvider<FileSettingsSource> settingsSource;
    private final FileRepository fileRepository;
    private final BlobStore blobStore;
    private final FileModuleRegistry registry;
    private final Clock clock;
    private final Map<String, Cached<?>> cache = new ConcurrentHashMap<>();

    public FileLimitService(FileStorageProperties properties, ObjectProvider<FileSettingsSource> settingsSource,
                            FileRepository fileRepository, BlobStore blobStore, FileModuleRegistry registry) {
        this(properties, settingsSource, fileRepository, blobStore, registry, Clock.systemDefaultZone());
    }

    FileLimitService(FileStorageProperties properties, ObjectProvider<FileSettingsSource> settingsSource,
                     FileRepository fileRepository, BlobStore blobStore, FileModuleRegistry registry, Clock clock) {
        this.properties = properties;
        this.settingsSource = settingsSource;
        this.fileRepository = fileRepository;
        this.blobStore = blobStore;
        this.registry = registry;
        this.clock = clock;
    }

    /** Giới hạn hiệu lực của một module (DB > mặc định). */
    public record ModuleLimits(String moduleName, long maxFileSizeBytes, Integer maxFilesPerRef,
                               Long maxTotalBytesPerRef, List<String> allowedExtensions, int trashRetentionDays,
                               boolean countToStudentQuota, boolean active) {
    }

    public ModuleLimits limits(String descriptorCode) {
        String code = FileModuleRegistry.normalize(descriptorCode);
        Optional<FileModuleSettingDto> db = cached("module:" + code, () -> source().flatMap(s -> s.moduleSetting(code)));
        ModuleLimits fallback = defaults(code);
        if (db.isEmpty()) {
            return fallback;
        }
        FileModuleSettingDto s = db.get();
        List<String> exts = s.getAllowedExtensions() == null || s.getAllowedExtensions().isEmpty()
                ? fallback.allowedExtensions()
                : s.getAllowedExtensions().stream().map(e -> e.toLowerCase(Locale.ROOT))
                .filter(SafeFileType.allExtensions()::contains).toList();
        return new ModuleLimits(code,
                s.getMaxFileSizeBytes() != null ? s.getMaxFileSizeBytes() : fallback.maxFileSizeBytes(),
                s.getMaxFilesPerRef(), s.getMaxTotalBytesPerRef(), exts,
                s.getTrashRetentionDays() != null ? s.getTrashRetentionDays() : fallback.trashRetentionDays(),
                s.isCountToStudentQuota(), s.isActive());
    }

    /** Ném {@code FILE_MODULE_DISABLED} khi module đang tắt. */
    public void requireActive(String descriptorCode) {
        if (!limits(descriptorCode).active()) {
            throw new OracleBusinessException(FileErrorCodes.FILE_MODULE_DISABLED,
                    "Chức năng file của module " + descriptorCode + " đang tạm tắt.");
        }
    }

    /**
     * Kiểm tra giới hạn trước khi ghi blob (thứ tự theo hợp đồng mục 4.2): module tắt -> dung lượng file ->
     * đuôi -> số file / tổng trên bản ghi -> quota học sinh / toàn hệ thống -> đĩa trống.
     *
     * @param storedModule {@code MODULE_NAME} sẽ lưu (đếm theo bản ghi).
     */
    public void check(String descriptorCode, String storedModule, Long referenceId, long size, String ext,
                      Long ownerStudentId) {
        checkSizeAndType(descriptorCode, size, ext);
        checkCapacity(descriptorCode, storedModule, referenceId, size, ownerStudentId);
    }

    /** Module tắt -> dung lượng file -> đuôi thuộc allow-list của module. */
    public void checkSizeAndType(String descriptorCode, long size, String ext) {
        ModuleLimits l = limits(descriptorCode);
        if (!l.active()) {
            throw new OracleBusinessException(FileErrorCodes.FILE_MODULE_DISABLED,
                    "Chức năng file của module " + descriptorCode + " đang tạm tắt.");
        }
        if (size > l.maxFileSizeBytes()) {
            throw new OracleBusinessException(FileErrorCodes.FILE_TOO_LARGE,
                    "File vượt quá " + human(l.maxFileSizeBytes()) + " (file của bạn: " + human(size) + ").");
        }
        if (ext == null || !l.allowedExtensions().contains(ext.toLowerCase(Locale.ROOT))) {
            throw new OracleBusinessException(FileErrorCodes.FILE_TYPE_NOT_ALLOWED,
                    (ext == null ? "File không có đuôi" : "Định dạng ." + ext + " không được phép")
                            + ". Chỉ nhận: " + String.join(", ", l.allowedExtensions()) + ".");
        }
    }

    /** Số file / tổng trên bản ghi -> quota học sinh / toàn hệ thống -> đĩa trống. */
    public void checkCapacity(String descriptorCode, String storedModule, Long referenceId, long size,
                              Long ownerStudentId) {
        ModuleLimits l = limits(descriptorCode);
        if (referenceId != null) {
            if (l.maxFilesPerRef() != null && fileRepository.countByModuleNameAndReferenceIdAndStatusIn(
                    storedModule, referenceId, LIVE_STATUSES) >= l.maxFilesPerRef()) {
                throw new OracleBusinessException(FileErrorCodes.FILE_LIMIT_COUNT,
                        "Mỗi bản ghi chỉ được tối đa " + l.maxFilesPerRef() + " file.");
            }
            if (l.maxTotalBytesPerRef() != null && fileRepository.sumSizeByRef(storedModule, referenceId,
                    LIVE_STATUSES) + size > l.maxTotalBytesPerRef()) {
                throw new OracleBusinessException(FileErrorCodes.FILE_LIMIT_TOTAL,
                        "Tổng dung lượng file của bản ghi vượt quá " + human(l.maxTotalBytesPerRef()) + ".");
            }
        }
        if (l.countToStudentQuota() && ownerStudentId != null) {
            long max = studentQuota(ownerStudentId);
            if (fileRepository.sumSizeByOwnerStudent(ownerStudentId, STORED_STATUSES) + size > max) {
                throw new OracleBusinessException(FileErrorCodes.FILE_QUOTA_EXCEEDED,
                        "Học sinh đã vượt hạn mức lưu trữ " + human(max) + ".");
            }
        }
        long global = globalQuota();
        if (fileRepository.sumSizeByStatusIn(STORED_STATUSES) + size > global) {
            throw new OracleBusinessException(FileErrorCodes.FILE_QUOTA_EXCEEDED,
                    "Hệ thống đã vượt hạn mức lưu trữ " + human(global) + ".");
        }
        StorageUsage usage;
        try {
            usage = blobStore.usage();
        } catch (IOException e) {
            log.warn("Không đọc được dung lượng kho lưu trữ: {}", e.getMessage());
            return;
        }
        if (usage.isBelow(properties.getMinFreeDisk().toBytes() + size)) {
            throw new OracleBusinessException(FileErrorCodes.STORAGE_LOW_DISK,
                    "Kho lưu trữ sắp đầy, tạm thời không nhận file mới. Vui lòng báo quản trị viên.");
        }
    }

    public long studentQuota(Long studentId) {
        return cached("quota:STUDENT:" + studentId,
                () -> source().flatMap(s -> s.quota(FileConstants.QuotaScope.STUDENT, studentId)))
                .map(FileQuotaDto::getMaxBytes)
                .or(() -> cached("quota:STUDENT_DEFAULT",
                        () -> source().flatMap(s -> s.quota(FileConstants.QuotaScope.STUDENT_DEFAULT, null)))
                        .map(FileQuotaDto::getMaxBytes))
                .orElse(properties.getQuota().getStudentDefault().toBytes());
    }

    public long globalQuota() {
        return cached("quota:GLOBAL", () -> source().flatMap(s -> s.quota(FileConstants.QuotaScope.GLOBAL, null)))
                .map(FileQuotaDto::getMaxBytes)
                .orElse(properties.getQuota().getGlobal().toBytes());
    }

    /** Chính sách upload cho frontend: một module hoặc mọi module đang bật có descriptor. */
    public List<FileUploadPolicyDto> policies(String module) {
        List<FileModuleDescriptor> targets = new ArrayList<>();
        if (module == null || module.isBlank()) {
            targets.addAll(registry.all());
        } else {
            targets.add(registry.require(module));
        }
        List<FileUploadPolicyDto> out = new ArrayList<>();
        List<String> inline = SafeFileType.inlineExtensions();
        for (FileModuleDescriptor d : targets) {
            ModuleLimits l = limits(d.code());
            if (!l.active() && (module == null || module.isBlank())) {
                continue;
            }
            out.add(FileUploadPolicyDto.builder()
                    .moduleName(d.code())
                    .maxFileSizeBytes(l.maxFileSizeBytes())
                    .maxFilesPerRef(l.maxFilesPerRef())
                    .maxTotalBytesPerRef(l.maxTotalBytesPerRef())
                    .allowedExtensions(l.allowedExtensions())
                    .inlineExtensions(inline.stream().filter(l.allowedExtensions()::contains).toList())
                    .referenceRequired(d.referenceRequired())
                    .build());
        }
        return out;
    }

    /** Xoá cache (Luồng B gọi sau khi admin lưu cấu hình, nếu muốn hiệu lực ngay). */
    public void evictCache() {
        cache.clear();
    }

    // ---------------------------------------------------------------------------------------------

    ModuleLimits defaults(String code) {
        FileStorageProperties.Defaults d = properties.getDefaults();
        int trash = properties.getRetention().getTrashDays();
        List<String> all = SafeFileType.allExtensions();
        return switch (code) {
            case FileConstants.Module.STUDENT -> new ModuleLimits(code, 20 * MB, 30,
                    d.getMaxTotalPerRef().toBytes(), all, trash, true, true);
            case FileConstants.Module.STUDENT_IMPORT -> new ModuleLimits(code, 5 * MB, null, null,
                    List.of("xlsx"), trash, false, true);
            case FileConstants.Module.LIBRARY -> new ModuleLimits(code, 50 * MB, null, null, all, trash, false, true);
            default -> new ModuleLimits(code, d.getMaxFileSize().toBytes(), d.getMaxFilesPerRef(),
                    d.getMaxTotalPerRef().toBytes(), all, trash, false, true);
        };
    }

    private Optional<FileSettingsSource> source() {
        return Optional.ofNullable(settingsSource.getIfAvailable());
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> cached(String key, Supplier<Optional<T>> loader) {
        long now = clock.millis();
        Cached<?> c = cache.get(key);
        if (c != null && now - c.loadedAt() < CACHE_TTL_MILLIS) {
            return (Optional<T>) c.value();
        }
        Optional<T> value;
        try {
            value = loader.get();
        } catch (RuntimeException e) {
            // Bảng V19_2 chưa có / lỗi DB: dùng mặc định yml, không chặn upload.
            log.warn("Không đọc được cấu hình file ({}): {}", key, e.getMessage());
            value = Optional.empty();
        }
        cache.put(key, new Cached<>(value, now));
        return value;
    }

    private record Cached<T>(Optional<T> value, long loadedAt) {
    }

    static String human(long bytes) {
        if (bytes >= 1024L * MB && bytes % (1024L * MB) == 0) {
            return bytes / (1024L * MB) + " GB";
        }
        if (bytes >= MB) {
            return (bytes % MB == 0 ? String.valueOf(bytes / MB) : String.format(Locale.ROOT, "%.1f", bytes / (double) MB))
                    + " MB";
        }
        return bytes + " B";
    }
}
