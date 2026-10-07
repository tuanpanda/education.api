package com.education.base.service.file;

import com.education.base.dto.response.FileModuleSettingDto;
import com.education.base.dto.response.FileQuotaDto;
import com.education.base.entity.FileModuleSettingEntity;
import com.education.base.entity.FileQuotaEntity;
import com.education.base.repository.FileModuleSettingRepository;
import com.education.base.repository.FileQuotaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@link FileSettingsSource} đọc bảng V19_2 ({@code SYS_FILE_MODULE_SETTINGS}, {@code SYS_FILE_QUOTAS}) với cache
 * 60 giây (QĐ-F8). Màn quản trị gọi {@link #evict()} sau khi lưu. Lỗi đọc DB -> giữ bản cache cũ (nếu có) hoặc
 * trả rỗng để Luồng A rơi về giá trị yml.
 * <p>
 * Luồng B.
 */
@Slf4j
@Component
public class DbFileSettingsSource implements FileSettingsSource {

    static final Duration TTL = Duration.ofSeconds(60);

    private final FileModuleSettingRepository moduleRepository;
    private final FileQuotaRepository quotaRepository;
    private final Clock clock;

    private volatile Snapshot snapshot;

    @Autowired
    public DbFileSettingsSource(FileModuleSettingRepository moduleRepository, FileQuotaRepository quotaRepository) {
        this(moduleRepository, quotaRepository, Clock.systemDefaultZone());
    }

    DbFileSettingsSource(FileModuleSettingRepository moduleRepository, FileQuotaRepository quotaRepository,
                         Clock clock) {
        this.moduleRepository = moduleRepository;
        this.quotaRepository = quotaRepository;
        this.clock = clock;
    }

    @Override
    public Optional<FileModuleSettingDto> moduleSetting(String moduleName) {
        if (moduleName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(current().modules().get(moduleName.trim().toUpperCase(Locale.ROOT)));
    }

    @Override
    public List<FileModuleSettingDto> moduleSettings() {
        return List.copyOf(current().modules().values());
    }

    @Override
    public Optional<FileQuotaDto> quota(String scopeType, Long scopeId) {
        if (scopeType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(current().quotas().get(key(scopeType.trim().toUpperCase(Locale.ROOT), scopeId)));
    }

    /** Xoá cache (gọi sau khi admin lưu cấu hình). */
    public void evict() {
        snapshot = null;
    }

    private Snapshot current() {
        Snapshot s = snapshot;
        Instant now = clock.instant();
        if (s != null && now.isBefore(s.loadedAt().plus(TTL))) {
            return s;
        }
        synchronized (this) {
            s = snapshot;
            if (s != null && now.isBefore(s.loadedAt().plus(TTL))) {
                return s;
            }
            try {
                Map<String, FileModuleSettingDto> modules = new LinkedHashMap<>();
                moduleRepository.findAll().stream()
                        .sorted(Comparator.comparing(FileModuleSettingEntity::getModuleName))
                        .forEach(e -> modules.put(e.getModuleName(), toDto(e)));
                Map<String, FileQuotaDto> quotas = new LinkedHashMap<>();
                quotaRepository.findAll().forEach(e -> quotas.put(key(e.getScopeType(), e.getScopeId()), toDto(e)));
                s = new Snapshot(Map.copyOf(modules), Map.copyOf(quotas), now);
            } catch (RuntimeException ex) {
                log.warn("Không đọc được cấu hình file từ DB (dùng {}): {}",
                        s != null ? "bản cache cũ" : "mặc định yml", ex.getMessage());
                s = s != null ? new Snapshot(s.modules(), s.quotas(), now)
                        : new Snapshot(Map.of(), Map.of(), now.minus(TTL).plusSeconds(5));
            }
            snapshot = s;
            return s;
        }
    }

    private static String key(String scopeType, Long scopeId) {
        return scopeType + ":" + (scopeId == null ? "" : scopeId);
    }

    public static FileModuleSettingDto toDto(FileModuleSettingEntity e) {
        return FileModuleSettingDto.builder()
                .moduleName(e.getModuleName())
                .maxFileSizeBytes(e.getMaxFileSizeBytes())
                .maxFilesPerRef(e.getMaxFilesPerRef())
                .maxTotalBytesPerRef(e.getMaxTotalBytesPerRef())
                .allowedExtensions(parseExtensions(e.getAllowedExtensions()))
                .trashRetentionDays(e.getTrashRetentionDays())
                .countToStudentQuota(Integer.valueOf(1).equals(e.getCountToStudentQuota()))
                .active(Integer.valueOf(1).equals(e.getIsActive()))
                .updatedAt(e.getUpdatedAt() != null ? e.getUpdatedAt() : e.getCreatedAt())
                .updatedBy(e.getUpdatedBy() != null ? e.getUpdatedBy() : e.getCreatedBy())
                .build();
    }

    public static FileQuotaDto toDto(FileQuotaEntity e) {
        return FileQuotaDto.builder()
                .id(e.getId())
                .scopeType(e.getScopeType())
                .scopeId(e.getScopeId())
                .maxBytes(e.getMaxBytes())
                .warnPercent(e.getWarnPercent())
                .note(e.getNote())
                .updatedAt(e.getUpdatedAt() != null ? e.getUpdatedAt() : e.getCreatedAt())
                .updatedBy(e.getUpdatedBy() != null ? e.getUpdatedBy() : e.getCreatedBy())
                .build();
    }

    /** CSV -> danh sách chữ thường; NULL/rỗng -> NULL (theo SafeFileType). */
    public static List<String> parseExtensions(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        List<String> list = Arrays.stream(csv.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        return list.isEmpty() ? null : list;
    }

    private record Snapshot(Map<String, FileModuleSettingDto> modules, Map<String, FileQuotaDto> quotas,
                            Instant loadedAt) {
    }
}
