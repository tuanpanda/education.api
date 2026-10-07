package com.education.base.service.impl;

import com.education.base.audit.AuditActions;
import com.education.base.audit.AuditEvent;
import com.education.base.common.file.FileAdminErrorCodes;
import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileErrorCodes;
import com.education.base.common.file.FileModuleDescriptor;
import com.education.base.common.file.FileReferenceInfo;
import com.education.base.common.file.SafeFileType;
import com.education.base.config.FileStorageProperties;
import com.education.base.config.FilesProperties;
import com.education.base.dto.request.AuditLogFilterRequest;
import com.education.base.dto.request.FileBulkRequest;
import com.education.base.dto.request.FileFilterRequest;
import com.education.base.dto.request.FileModuleSettingsUpdateRequest;
import com.education.base.dto.request.FilePurgeRequest;
import com.education.base.dto.request.FileQuotasUpdateRequest;
import com.education.base.dto.response.AuditLogResponseDto;
import com.education.base.dto.response.FileAdminDto;
import com.education.base.dto.response.FileBulkResultDto;
import com.education.base.dto.response.FileDetailDto;
import com.education.base.dto.response.FileModuleSettingDto;
import com.education.base.dto.response.FileQuotaDto;
import com.education.base.dto.response.FileStatBucketDto;
import com.education.base.dto.response.FileStatsDto;
import com.education.base.dto.response.FileTopUserDto;
import com.education.base.dto.response.FileUsageDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StorageUsageDto;
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
import com.education.base.repository.spec.AuditLogSpecifications;
import com.education.base.repository.spec.FileSpecifications;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AuditService;
import com.education.base.service.FileAdminService;
import com.education.base.service.file.DbFileSettingsSource;
import com.education.base.service.file.FileLifecycleService;
import com.education.base.storage.BlobStore;
import com.education.base.storage.StorageUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cài đặt {@link FileAdminService} (Luồng B).
 */
@Slf4j
@Service
public class FileAdminServiceImpl implements FileAdminService {

    static final String ADMIN_BASE = "/api/v1/admin/files/";
    static final int AUDIT_TRAIL_SIZE = 20;
    static final int TOP_N = 10;
    /** Trạng thái còn giữ blob trên đĩa (tính dung lượng / quota). */
    static final List<String> STORED = List.of(FileConstants.Status.ACTIVE, FileConstants.Status.PENDING,
            FileConstants.Status.DELETED);
    static final List<String> ACTIVE_ONLY = List.of(FileConstants.Status.ACTIVE);
    static final Map<String, String> STATUS_LABELS = Map.of(
            FileConstants.Status.ACTIVE, "Đang dùng",
            FileConstants.Status.PENDING, "Chờ gắn",
            FileConstants.Status.DELETED, "Thùng rác",
            FileConstants.Status.PURGED, "Đã xoá vĩnh viễn");

    private final FileAdminRepository fileRepository;
    private final FileModuleSettingRepository moduleSettingRepository;
    private final FileQuotaRepository quotaRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final FileLifecycleService lifecycleService;
    private final BlobStore blobStore;
    private final AuditService auditService;
    private final DbFileSettingsSource settingsSource;
    private final FileStorageProperties storageProperties;
    private final FilesProperties filesProperties;
    private final Map<String, FileModuleDescriptor> descriptors;
    private final long multipartMaxBytes;
    private final Clock clock;

    @Autowired
    public FileAdminServiceImpl(FileAdminRepository fileRepository,
                                FileModuleSettingRepository moduleSettingRepository,
                                FileQuotaRepository quotaRepository,
                                StudentRepository studentRepository,
                                UserRepository userRepository,
                                AuditLogRepository auditLogRepository,
                                FileLifecycleService lifecycleService,
                                BlobStore blobStore,
                                AuditService auditService,
                                DbFileSettingsSource settingsSource,
                                FileStorageProperties storageProperties,
                                FilesProperties filesProperties,
                                List<FileModuleDescriptor> descriptors,
                                @Value("${spring.servlet.multipart.max-file-size:50MB}") DataSize multipartMax) {
        this(fileRepository, moduleSettingRepository, quotaRepository, studentRepository, userRepository,
                auditLogRepository, lifecycleService, blobStore, auditService, settingsSource, storageProperties,
                filesProperties, descriptors, multipartMax, Clock.systemDefaultZone());
    }

    FileAdminServiceImpl(FileAdminRepository fileRepository, FileModuleSettingRepository moduleSettingRepository,
                         FileQuotaRepository quotaRepository, StudentRepository studentRepository,
                         UserRepository userRepository, AuditLogRepository auditLogRepository,
                         FileLifecycleService lifecycleService, BlobStore blobStore, AuditService auditService,
                         DbFileSettingsSource settingsSource, FileStorageProperties storageProperties,
                         FilesProperties filesProperties, List<FileModuleDescriptor> descriptors,
                         DataSize multipartMax, Clock clock) {
        this.fileRepository = fileRepository;
        this.moduleSettingRepository = moduleSettingRepository;
        this.quotaRepository = quotaRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.lifecycleService = lifecycleService;
        this.blobStore = blobStore;
        this.auditService = auditService;
        this.settingsSource = settingsSource;
        this.storageProperties = storageProperties;
        this.filesProperties = filesProperties;
        this.descriptors = descriptors == null ? Map.of() : descriptors.stream()
                .collect(Collectors.toMap(d -> d.code().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        this.multipartMaxBytes = multipartMax == null ? DataSize.ofMegabytes(50).toBytes() : multipartMax.toBytes();
        this.clock = clock;
    }

    // ---- Tìm kiếm / chi tiết ------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FileAdminDto> search(FileFilterRequest filter) {
        FileFilterRequest criteria = filter == null ? new FileFilterRequest() : filter;
        Map<String, Set<Long>> refs = null;
        String refState = criteria.getRefState() == null ? null : criteria.getRefState().trim().toUpperCase(Locale.ROOT);
        if (refState != null && !refState.isEmpty() && !FileConstants.RefState.NONE.equals(refState)) {
            refs = referencesInState(refState, criteria);
        }
        Sort.Direction dir = "asc".equalsIgnoreCase(criteria.getSortDir()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortBy = criteria.getSortBy() == null ? "createdAt" : criteria.getSortBy();
        Page<FileEntity> page = fileRepository.findAll(FileSpecifications.fromFilter(criteria, refs),
                PageRequest.of(criteria.getPageNo() - 1, criteria.getPageSize(),
                        Sort.by(new Sort.Order(dir, sortBy), Sort.Order.desc("id"))));
        return PageResponse.of(toDtos(page.getContent()), criteria.getPageNo(), criteria.getPageSize(),
                page.getTotalElements());
    }

    /** (module -> referenceIds) có trạng thái bản ghi {@code refState}, giới hạn theo module/status của bộ lọc. */
    private Map<String, Set<Long>> referencesInState(String refState, FileFilterRequest criteria) {
        List<String> statuses = criteria.getStatus() == null
                || FileFilterRequest.STATUS_ALL.equalsIgnoreCase(criteria.getStatus())
                ? FileConstants.Status.ALL : List.of(criteria.getStatus().trim().toUpperCase(Locale.ROOT));
        Map<String, Set<Long>> out = new HashMap<>();
        Map<String, FileReferenceInfo> cache = new HashMap<>();
        for (Object[] row : fileRepository.distinctReferences(statuses)) {
            String module = (String) row[0];
            Long refId = ((Number) row[1]).longValue();
            if (criteria.getModule() != null && !criteria.getModule().equalsIgnoreCase(module)) {
                continue;
            }
            FileReferenceInfo info = cache.computeIfAbsent(module + ":" + refId, k -> describe(module, refId));
            if (refState.equals(info.state())) {
                out.computeIfAbsent(module, k -> new LinkedHashSet<>()).add(refId);
            }
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public FileDetailDto detail(Long id) {
        FileEntity file = requireFile(id);
        Boolean exists = null;
        if (FileConstants.Status.PURGED.equals(file.getStatus())) {
            exists = Boolean.FALSE;
        } else if (file.getFilePath() != null) {
            try {
                exists = blobStore.exists(file.getFilePath());
            } catch (RuntimeException ex) {
                log.debug("blobStore.exists({}) lỗi: {}", id, ex.getMessage());
            }
        }
        AuditLogFilterRequest auditFilter = new AuditLogFilterRequest();
        auditFilter.setResourceType(AuditActions.RESOURCE_FILE);
        auditFilter.setResourceId(String.valueOf(id));
        List<AuditLogResponseDto> trail = auditLogRepository.findAll(AuditLogSpecifications.fromFilter(auditFilter),
                        PageRequest.of(0, AUDIT_TRAIL_SIZE,
                                Sort.by(Sort.Order.desc("eventTime"), Sort.Order.desc("id"))))
                .getContent().stream().map(AuditLogQueryServiceImpl::toDto).toList();
        return FileDetailDto.builder()
                .file(toDtos(List.of(file)).get(0))
                .storageKey(file.getFilePath())
                .storedName(file.getStoredName())
                .blobExists(exists)
                .scanDetail(file.getScanDetail())
                .scannedAt(file.getScannedAt())
                .auditTrail(trail)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public FileContent open(Long id, boolean download) {
        FileEntity file = requireFile(id);
        if (FileConstants.Status.PURGED.equals(file.getStatus())) {
            throw new OracleBusinessException(FileAdminErrorCodes.FILE_INVALID_STATE,
                    "File đã bị xoá vĩnh viễn, không còn nội dung.");
        }
        Resource resource;
        try {
            if (file.getFilePath() == null || !blobStore.exists(file.getFilePath())) {
                throw blobMissing();
            }
            resource = blobStore.resource(file.getFilePath());
        } catch (NoSuchFileException ex) {
            throw blobMissing();
        } catch (IOException ex) {
            throw new OracleBusinessException(FileErrorCodes.FILE_STORAGE_ERROR, "Không đọc được file.", ex);
        }
        Optional<SafeFileType> type = SafeFileType.forStoredFile(file.getContentType(), file.getOriginalName());
        String contentType = type.map(SafeFileType::responseContentType).orElse(SafeFileType.OCTET_STREAM);
        boolean inline = !download && type.map(SafeFileType::isInlineSafe).orElse(false);
        if (download || filesProperties.getAudit().isViews()) {
            auditService.record(AuditEvent.builder()
                    .action(download ? AuditActions.FILE_DOWNLOADED : AuditActions.FILE_VIEWED)
                    .resource(AuditActions.RESOURCE_FILE, id)
                    .detail("via", "ADMIN")
                    .detail("module", file.getModuleName())
                    .detail("refId", file.getReferenceId())
                    .detail("status", file.getStatus())
                    .detail("size", file.getFileSize())
                    .build());
        }
        return new FileContent(file, resource, contentType, inline);
    }

    private static OracleBusinessException blobMissing() {
        return new OracleBusinessException(FileErrorCodes.FILE_BLOB_MISSING,
                "Không tìm thấy nội dung file trên kho lưu trữ.");
    }

    // ---- Xoá / khôi phục / xoá vĩnh viễn ---------------------------------------------------------------

    @Override
    public FileAdminDto delete(Long id, String reason) {
        FileEntity before = requireFile(id);
        if (!FileConstants.Status.ACTIVE.equals(before.getStatus())) {
            throw invalidState("Chỉ xoá được file đang dùng.");
        }
        FileEntity after = lifecycleService.softDelete(id, trimReason(reason));
        auditService.record(AuditEvent.builder()
                .action(AuditActions.FILE_DELETED)
                .resource(AuditActions.RESOURCE_FILE, id)
                .detail("via", "ADMIN")
                .detail("module", before.getModuleName())
                .detail("refId", before.getReferenceId())
                .detail("reason", trimReason(reason))
                .build());
        return toDtos(List.of(after == null ? requireFile(id) : after)).get(0);
    }

    @Override
    public FileBulkResultDto bulkDelete(FileBulkRequest request) {
        List<Long> ids = bulkIds(request);
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new OracleBusinessException("VALIDATION_ERROR", "Vui lòng nhập lý do xoá");
        }
        return bulk(ids, id -> delete(id, request.getReason()));
    }

    @Override
    public FileAdminDto restore(Long id) {
        FileEntity before = requireFile(id);
        if (!FileConstants.Status.DELETED.equals(before.getStatus())) {
            throw invalidState("Chỉ khôi phục được file trong thùng rác.");
        }
        FileEntity after = lifecycleService.restore(id);
        auditService.record(AuditEvent.builder()
                .action(AuditActions.FILE_RESTORED)
                .resource(AuditActions.RESOURCE_FILE, id)
                .detail("module", before.getModuleName())
                .detail("refId", before.getReferenceId())
                .build());
        return toDtos(List.of(after == null ? requireFile(id) : after)).get(0);
    }

    @Override
    public FileBulkResultDto bulkRestore(FileBulkRequest request) {
        return bulk(bulkIds(request), this::restore);
    }

    @Override
    public FileAdminDto purge(Long id, FilePurgeRequest request) {
        FileEntity before = requireFile(id);
        if (!FileConstants.Status.DELETED.equals(before.getStatus())) {
            throw invalidState("Chỉ xoá vĩnh viễn được file đã ở thùng rác.");
        }
        if (request.getConfirmName() == null || !request.getConfirmName().equals(before.getOriginalName())) {
            throw new OracleBusinessException(FileAdminErrorCodes.FILE_PURGE_CONFIRM_MISMATCH,
                    "Tên file xác nhận không khớp.");
        }
        FileEntity after = lifecycleService.purge(id, trimReason(request.getReason()));
        auditService.record(AuditEvent.builder()
                .action(AuditActions.FILE_PURGED)
                .resource(AuditActions.RESOURCE_FILE, id)
                .detail("via", "ADMIN")
                .detail("module", before.getModuleName())
                .detail("refId", before.getReferenceId())
                .detail("size", before.getFileSize())
                .detail("reason", trimReason(request.getReason()))
                .build());
        return toDtos(List.of(after == null ? requireFile(id) : after)).get(0);
    }

    private List<Long> bulkIds(FileBulkRequest request) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            throw new OracleBusinessException("VALIDATION_ERROR", "Vui lòng chọn ít nhất một file");
        }
        List<Long> ids = request.getIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.size() > FileConstants.MAX_BULK_IDS) {
            throw new OracleBusinessException(FileAdminErrorCodes.FILE_BULK_TOO_MANY, "Tối đa 100 file mỗi lần");
        }
        return ids;
    }

    /** Từng file độc lập (mỗi lần gọi lifecycle là một transaction riêng), không rollback cả lô. */
    private FileBulkResultDto bulk(List<Long> ids, Function<Long, FileAdminDto> action) {
        List<Long> ok = new ArrayList<>();
        List<FileBulkResultDto.Failure> failed = new ArrayList<>();
        for (Long id : ids) {
            try {
                action.apply(id);
                ok.add(id);
            } catch (OracleBusinessException ex) {
                failed.add(new FileBulkResultDto.Failure(id, ex.getErrorCode(), ex.getMessage()));
            } catch (RuntimeException ex) {
                log.warn("Thao tác hàng loạt file #{} lỗi", id, ex);
                failed.add(new FileBulkResultDto.Failure(id, FileErrorCodes.FILE_STORAGE_ERROR,
                        "Lỗi xử lý file."));
            }
        }
        return FileBulkResultDto.builder().succeeded(ok).failed(failed).build();
    }

    // ---- Thống kê ----------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public FileStatsDto stats() {
        List<FileStatBucketDto> byStatus = new ArrayList<>();
        Map<String, Object[]> statusRows = new HashMap<>();
        for (Object[] r : fileRepository.sumByStatus()) {
            statusRows.put((String) r[0], r);
        }
        for (String s : FileConstants.Status.ALL) {
            Object[] r = statusRows.get(s);
            byStatus.add(bucket(s, STATUS_LABELS.get(s), r == null ? 0 : num(r[1]), r == null ? 0 : num(r[2])));
        }
        List<FileStatBucketDto> byModule = fileRepository.sumByModule(ACTIVE_ONLY).stream()
                .map(r -> bucket((String) r[0], null, num(r[1]), num(r[2]))).toList();

        YearMonth current = YearMonth.now(clock);
        YearMonth first = current.minusMonths(11);
        Map<YearMonth, long[]> months = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            months.put(first.plusMonths(i), new long[2]);
        }
        for (Object[] r : fileRepository.sumByMonth(first.atDay(1).atStartOfDay(), STORED)) {
            YearMonth ym = YearMonth.of((int) num(r[0]), (int) num(r[1]));
            long[] v = months.get(ym);
            if (v != null) {
                v[0] += num(r[2]);
                v[1] += num(r[3]);
            }
        }
        List<FileStatBucketDto> byMonth = months.entrySet().stream()
                .map(e -> bucket(e.getKey().toString(), null, e.getValue()[0], e.getValue()[1])).toList();

        List<Object[]> uploaderRows = fileRepository.topUploaders(STORED, PageRequest.of(0, TOP_N));
        Map<Long, UserEntity> users = userRepository.findAllById(ids(uploaderRows)).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity(), (a, b) -> a));
        List<FileTopUserDto> topUploaders = uploaderRows.stream().map(r -> {
            Long uid = num(r[0]);
            UserEntity u = users.get(uid);
            return FileTopUserDto.builder().id(uid).code(u == null ? null : u.getUsername())
                    .fullName(u == null ? null : u.getFullName()).fileCount(num(r[1])).totalBytes(num(r[2])).build();
        }).toList();

        List<Object[]> studentRows = fileRepository.topStudents(STORED, PageRequest.of(0, TOP_N));
        Map<Long, StudentEntity> students = studentRepository.findAllById(ids(studentRows)).stream()
                .collect(Collectors.toMap(StudentEntity::getId, Function.identity(), (a, b) -> a));
        Long studentDefault = quotaBytes(FileConstants.QuotaScope.STUDENT_DEFAULT, null)
                .orElse(storageProperties.getQuota().getStudentDefault().toBytes());
        List<FileTopUserDto> topStudents = studentRows.stream().map(r -> {
            Long sid = num(r[0]);
            StudentEntity s = students.get(sid);
            return FileTopUserDto.builder().id(sid).code(s == null ? null : s.getStudentCode())
                    .fullName(s == null ? null : s.getFullName()).fileCount(num(r[1])).totalBytes(num(r[2]))
                    .quotaBytes(quotaBytes(FileConstants.QuotaScope.STUDENT, sid).orElse(studentDefault))
                    .build();
        }).toList();

        long deletedRefs = 0;
        Map<String, FileReferenceInfo> cache = new HashMap<>();
        Set<String> badRefs = new HashSet<>();
        for (Object[] r : fileRepository.distinctReferences(ACTIVE_ONLY)) {
            String module = (String) r[0];
            Long refId = num(r[1]);
            FileReferenceInfo info = cache.computeIfAbsent(module + ":" + refId, k -> describe(module, refId));
            if (FileConstants.RefState.DELETED.equals(info.state())
                    || FileConstants.RefState.NOT_FOUND.equals(info.state())) {
                badRefs.add(module + ":" + refId);
            }
        }
        if (!badRefs.isEmpty()) {
            Map<String, Set<Long>> byMod = new HashMap<>();
            badRefs.forEach(k -> {
                int i = k.lastIndexOf(':');
                byMod.computeIfAbsent(k.substring(0, i), x -> new HashSet<>()).add(Long.valueOf(k.substring(i + 1)));
            });
            FileFilterRequest f = new FileFilterRequest();
            deletedRefs = fileRepository.count(FileSpecifications.fromFilter(f, byMod));
        }

        return FileStatsDto.builder()
                .byStatus(byStatus)
                .byModule(byModule)
                .byMonth(byMonth)
                .topUploaders(topUploaders)
                .topStudents(topStudents)
                .storage(storage())
                .filesWithoutChecksum(fileRepository.countByStatusAndChecksumSha256IsNull(FileConstants.Status.ACTIVE))
                .filesWithDeletedReference(deletedRefs)
                .generatedAt(LocalDateTime.now(clock))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public StorageUsageDto storage() {
        StorageUsage usage;
        try {
            usage = blobStore.usage();
        } catch (IOException | RuntimeException ex) {
            log.warn("Không đọc được dung lượng kho lưu trữ: {}", ex.getMessage());
            usage = new StorageUsage(safeBackend(), -1, -1, -1);
        }
        long minFree = storageProperties.getMinFreeDisk().toBytes();
        long used = fileRepository.sumSize(STORED);
        Optional<FileQuotaDto> global = settingsSource.quota(FileConstants.QuotaScope.GLOBAL, null);
        Long globalBytes = global.map(FileQuotaDto::getMaxBytes)
                .orElse(storageProperties.getQuota().getGlobal().toBytes());
        Integer warn = global.map(FileQuotaDto::getWarnPercent).orElse(storageProperties.getQuota().getWarnPercent());
        Double percent = globalBytes == null || globalBytes <= 0 ? null
                : Math.round(used * 1000.0 / globalBytes) / 10.0;
        return StorageUsageDto.builder()
                .backend(usage.backend())
                .totalBytes(usage.totalBytes())
                .usableBytes(usage.usableBytes())
                .minFreeBytes(minFree)
                .lowDisk(usage.isBelow(minFree))
                .usedBytes(used)
                .globalQuotaBytes(globalBytes)
                .globalQuotaPercent(percent)
                .warnPercent(warn)
                .build();
    }

    private String safeBackend() {
        try {
            return blobStore.backend();
        } catch (RuntimeException ex) {
            return FileConstants.StorageBackend.LOCAL;
        }
    }

    private Optional<Long> quotaBytes(String scope, Long scopeId) {
        return settingsSource.quota(scope, scopeId).map(FileQuotaDto::getMaxBytes);
    }

    // ---- Cấu hình module -----------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<FileModuleSettingDto> moduleSettings() {
        return moduleSettingRepository.findAll(Sort.by("moduleName")).stream().map(DbFileSettingsSource::toDto)
                .toList();
    }

    @Override
    @Transactional
    public List<FileModuleSettingDto> updateModuleSettings(FileModuleSettingsUpdateRequest request) {
        List<FileModuleSettingDto> items = request == null || request.getSettings() == null
                ? List.of() : request.getSettings();
        Set<String> seen = new HashSet<>();
        Map<String, FileModuleSettingEntity> existing = moduleSettingRepository.findAll().stream()
                .collect(Collectors.toMap(FileModuleSettingEntity::getModuleName, Function.identity()));
        List<Map<String, Object>> changes = new ArrayList<>();
        String actor = SecurityUtils.currentUsername();
        for (FileModuleSettingDto dto : items) {
            String module = dto.getModuleName().trim().toUpperCase(Locale.ROOT);
            if (!seen.add(module)) {
                throw settingInvalid("Module " + module + " bị lặp trong yêu cầu.");
            }
            FileModuleSettingEntity e = existing.get(module);
            if (e == null) {
                throw settingInvalid("Module " + module + " không tồn tại.");
            }
            if (dto.getMaxFileSizeBytes() > multipartMaxBytes) {
                throw settingInvalid("Giới hạn mỗi file của " + module + " vượt giới hạn upload toàn hệ thống ("
                        + multipartMaxBytes + " byte).");
            }
            if (dto.getMaxTotalBytesPerRef() != null && dto.getMaxTotalBytesPerRef() < dto.getMaxFileSizeBytes()) {
                throw settingInvalid("Tổng dung lượng mỗi bản ghi của " + module
                        + " phải lớn hơn hoặc bằng giới hạn mỗi file.");
            }
            String csv = normalizeExtensions(module, dto.getAllowedExtensions());
            FileModuleSettingDto before = DbFileSettingsSource.toDto(e);
            e.setMaxFileSizeBytes(dto.getMaxFileSizeBytes());
            e.setMaxFilesPerRef(dto.getMaxFilesPerRef());
            e.setMaxTotalBytesPerRef(dto.getMaxTotalBytesPerRef());
            e.setAllowedExtensions(csv);
            e.setTrashRetentionDays(dto.getTrashRetentionDays());
            e.setCountToStudentQuota(dto.isCountToStudentQuota() ? 1 : 0);
            e.setIsActive(dto.isActive() ? 1 : 0);
            FileModuleSettingDto after = DbFileSettingsSource.toDto(e);
            Map<String, Object> diff = diff(before, after);
            if (!diff.isEmpty()) {
                e.setUpdatedBy(actor);
                e.setUpdatedAt(LocalDateTime.now(clock));
                moduleSettingRepository.save(e);
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("module", module);
                change.putAll(diff);
                changes.add(change);
            }
        }
        if (!changes.isEmpty()) {
            settingsSource.evict();
            auditService.record(AuditEvent.builder()
                    .action(AuditActions.FILE_SETTINGS_CHANGED)
                    .detail("changes", changes)
                    .build());
        }
        return moduleSettings();
    }

    private String normalizeExtensions(String module, List<String> exts) {
        if (exts == null || exts.isEmpty()) {
            return null;
        }
        Set<String> out = new LinkedHashSet<>();
        for (String raw : exts) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String ext = raw.trim().toLowerCase(Locale.ROOT);
            if (SafeFileType.byExtension(ext).isEmpty()) {
                throw settingInvalid("Đuôi ." + ext + " của " + module + " không thuộc danh sách định dạng an toàn.");
            }
            out.add(ext);
        }
        String csv = String.join(",", out);
        if (csv.length() > 200) {
            throw settingInvalid("Danh sách đuôi của " + module + " quá dài.");
        }
        return csv.isEmpty() ? null : csv;
    }

    private static Map<String, Object> diff(FileModuleSettingDto a, FileModuleSettingDto b) {
        Map<String, Object> d = new LinkedHashMap<>();
        put(d, "maxFileSizeBytes", a.getMaxFileSizeBytes(), b.getMaxFileSizeBytes());
        put(d, "maxFilesPerRef", a.getMaxFilesPerRef(), b.getMaxFilesPerRef());
        put(d, "maxTotalBytesPerRef", a.getMaxTotalBytesPerRef(), b.getMaxTotalBytesPerRef());
        put(d, "allowedExtensions", a.getAllowedExtensions(), b.getAllowedExtensions());
        put(d, "trashRetentionDays", a.getTrashRetentionDays(), b.getTrashRetentionDays());
        put(d, "countToStudentQuota", a.isCountToStudentQuota(), b.isCountToStudentQuota());
        put(d, "active", a.isActive(), b.isActive());
        return d;
    }

    private static void put(Map<String, Object> d, String key, Object oldV, Object newV) {
        if (!Objects.equals(oldV, newV)) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("old", oldV);
            v.put("new", newV);
            d.put(key, v);
        }
    }

    // ---- Quota -------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<FileQuotaDto> quotas() {
        List<FileQuotaEntity> rows = new ArrayList<>(quotaRepository.findAll());
        rows.sort((x, y) -> {
            int c = Integer.compare(scopeOrder(x.getScopeType()), scopeOrder(y.getScopeType()));
            return c != 0 ? c : Long.compare(x.getScopeId() == null ? 0 : x.getScopeId(),
                    y.getScopeId() == null ? 0 : y.getScopeId());
        });
        Map<Long, StudentEntity> students = studentRepository.findAllById(scopeIds(rows, FileConstants.QuotaScope.STUDENT))
                .stream().collect(Collectors.toMap(StudentEntity::getId, Function.identity(), (a, b) -> a));
        Map<Long, UserEntity> users = userRepository.findAllById(scopeIds(rows, FileConstants.QuotaScope.USER))
                .stream().collect(Collectors.toMap(UserEntity::getId, Function.identity(), (a, b) -> a));
        Long totalUsed = null;
        List<FileQuotaDto> out = new ArrayList<>();
        for (FileQuotaEntity e : rows) {
            FileQuotaDto dto = DbFileSettingsSource.toDto(e);
            switch (e.getScopeType()) {
                case FileConstants.QuotaScope.GLOBAL -> {
                    if (totalUsed == null) {
                        totalUsed = fileRepository.sumSize(STORED);
                    }
                    dto.setScopeLabel("Toàn hệ thống");
                    dto.setUsedBytes(totalUsed);
                }
                case FileConstants.QuotaScope.STUDENT_DEFAULT -> dto.setScopeLabel("Mặc định mỗi học sinh");
                case FileConstants.QuotaScope.STUDENT -> {
                    StudentEntity s = students.get(e.getScopeId());
                    dto.setScopeLabel(s == null ? "Học sinh #" + e.getScopeId()
                            : s.getStudentCode() + " - " + s.getFullName());
                    dto.setUsedBytes(fileRepository.sumSizeByOwnerStudent(e.getScopeId(), STORED));
                }
                case FileConstants.QuotaScope.USER -> {
                    UserEntity u = users.get(e.getScopeId());
                    dto.setScopeLabel(u == null ? "Người dùng #" + e.getScopeId()
                            : u.getUsername() + (u.getFullName() == null ? "" : " - " + u.getFullName()));
                    dto.setUsedBytes(fileRepository.sumSizeByUploader(e.getScopeId(), STORED));
                }
                default -> {
                }
            }
            out.add(dto);
        }
        return out;
    }

    @Override
    @Transactional
    public List<FileQuotaDto> updateQuotas(FileQuotasUpdateRequest request) {
        List<FileQuotaDto> items = request == null || request.getQuotas() == null ? List.of() : request.getQuotas();
        List<Long> deleteIds = request == null || request.getDeleteIds() == null ? List.of() : request.getDeleteIds();
        List<FileQuotaEntity> all = quotaRepository.findAll();
        Map<Long, FileQuotaEntity> byId = all.stream().collect(Collectors.toMap(FileQuotaEntity::getId, Function.identity()));
        Map<String, FileQuotaEntity> byScope = new HashMap<>();
        all.forEach(e -> byScope.put(scopeKey(e.getScopeType(), e.getScopeId()), e));
        String actor = SecurityUtils.currentUsername();
        List<Map<String, Object>> changes = new ArrayList<>();

        Set<Long> deleting = new HashSet<>();
        for (Long id : deleteIds) {
            FileQuotaEntity e = byId.get(id);
            if (e == null) {
                throw quotaInvalid("Không tìm thấy quota #" + id + ".");
            }
            if (FileConstants.QuotaScope.GLOBAL.equals(e.getScopeType())
                    || FileConstants.QuotaScope.STUDENT_DEFAULT.equals(e.getScopeType())) {
                throw quotaInvalid("Không được xoá quota " + e.getScopeType() + ".");
            }
            deleting.add(id);
        }

        Set<String> seen = new HashSet<>();
        List<FileQuotaEntity> toSave = new ArrayList<>();
        for (FileQuotaDto dto : items) {
            String scope = dto.getScopeType().trim().toUpperCase(Locale.ROOT);
            boolean needsId = FileConstants.QuotaScope.STUDENT.equals(scope) || FileConstants.QuotaScope.USER.equals(scope);
            if (needsId && dto.getScopeId() == null) {
                throw quotaInvalid("Quota " + scope + " cần chọn đối tượng.");
            }
            if (!needsId && dto.getScopeId() != null) {
                throw quotaInvalid("Quota " + scope + " không có đối tượng.");
            }
            String key = scopeKey(scope, dto.getScopeId());
            if (!seen.add(key)) {
                throw quotaInvalid("Quota " + key + " bị lặp trong yêu cầu.");
            }
            if (FileConstants.QuotaScope.STUDENT.equals(scope) && !studentRepository.existsById(dto.getScopeId())) {
                throw quotaInvalid("Không tìm thấy học sinh #" + dto.getScopeId() + ".");
            }
            if (FileConstants.QuotaScope.USER.equals(scope) && !userRepository.existsById(dto.getScopeId())) {
                throw quotaInvalid("Không tìm thấy người dùng #" + dto.getScopeId() + ".");
            }
            FileQuotaEntity e = byScope.get(key);
            if (dto.getId() != null && (e == null || !e.getId().equals(dto.getId()))) {
                throw quotaInvalid("Quota #" + dto.getId() + " không khớp phạm vi " + key + ".");
            }
            if (e != null && deleting.contains(e.getId())) {
                throw quotaInvalid("Quota #" + e.getId() + " vừa sửa vừa xoá.");
            }
            int warn = dto.getWarnPercent() == null ? 80 : dto.getWarnPercent();
            if (e == null) {
                e = FileQuotaEntity.builder().scopeType(scope).scopeId(dto.getScopeId()).createdBy(actor).build();
                changes.add(quotaChange("CREATE", key, null, dto.getMaxBytes(), null, warn));
            } else if (Objects.equals(e.getMaxBytes(), dto.getMaxBytes()) && Objects.equals(e.getWarnPercent(), warn)
                    && Objects.equals(e.getNote(), dto.getNote())) {
                continue;
            } else {
                changes.add(quotaChange("UPDATE", key, e.getMaxBytes(), dto.getMaxBytes(), e.getWarnPercent(), warn));
                e.setUpdatedBy(actor);
                e.setUpdatedAt(LocalDateTime.now(clock));
            }
            e.setMaxBytes(dto.getMaxBytes());
            e.setWarnPercent(warn);
            e.setNote(dto.getNote());
            toSave.add(e);
        }
        for (Long id : deleting) {
            FileQuotaEntity e = byId.get(id);
            changes.add(quotaChange("DELETE", scopeKey(e.getScopeType(), e.getScopeId()), e.getMaxBytes(), null,
                    e.getWarnPercent(), null));
            quotaRepository.delete(e);
        }
        if (!toSave.isEmpty()) {
            quotaRepository.saveAll(toSave);
        }
        if (!changes.isEmpty()) {
            settingsSource.evict();
            auditService.record(AuditEvent.builder()
                    .action(AuditActions.FILE_QUOTA_CHANGED)
                    .detail("changes", changes)
                    .build());
        }
        return quotas();
    }

    private static Map<String, Object> quotaChange(String op, String scope, Long oldMax, Long newMax,
                                                   Integer oldWarn, Integer newWarn) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("op", op);
        m.put("scope", scope);
        m.put("maxBytes", Map.of("old", String.valueOf(oldMax), "new", String.valueOf(newMax)));
        m.put("warnPercent", Map.of("old", String.valueOf(oldWarn), "new", String.valueOf(newWarn)));
        return m;
    }

    private static int scopeOrder(String scope) {
        return switch (scope) {
            case FileConstants.QuotaScope.GLOBAL -> 0;
            case FileConstants.QuotaScope.STUDENT_DEFAULT -> 1;
            case FileConstants.QuotaScope.STUDENT -> 2;
            default -> 3;
        };
    }

    private static List<Long> scopeIds(Collection<FileQuotaEntity> rows, String scope) {
        return rows.stream().filter(r -> scope.equals(r.getScopeType())).map(FileQuotaEntity::getScopeId)
                .filter(Objects::nonNull).distinct().toList();
    }

    private static String scopeKey(String scope, Long id) {
        return scope + (id == null ? "" : ":" + id);
    }

    // ---- Mapping -----------------------------------------------------------------------------------

    List<FileAdminDto> toDtos(List<FileEntity> files) {
        if (files.isEmpty()) {
            return List.of();
        }
        Map<Long, UserEntity> users = userRepository.findAllById(files.stream().map(FileEntity::getUploadedByUserId)
                        .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity(), (a, b) -> a));
        Map<Long, StudentEntity> students = studentRepository.findAllById(files.stream()
                        .map(FileEntity::getOwnerStudentId).filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(StudentEntity::getId, Function.identity(), (a, b) -> a));
        Map<String, FileReferenceInfo> refCache = new HashMap<>();
        LocalDateTime now = LocalDateTime.now(clock);
        return files.stream().map(f -> {
            FileReferenceInfo ref = refCache.computeIfAbsent(f.getModuleName() + ":" + f.getReferenceId(),
                    k -> describe(f.getModuleName(), f.getReferenceId()));
            UserEntity u = f.getUploadedByUserId() == null ? null : users.get(f.getUploadedByUserId());
            StudentEntity s = f.getOwnerStudentId() == null ? null : students.get(f.getOwnerStudentId());
            return FileAdminDto.builder()
                    .id(f.getId())
                    .originalName(f.getOriginalName())
                    .fileExt(f.getFileExt())
                    .contentType(f.getContentType())
                    .fileSize(f.getFileSize())
                    .moduleName(f.getModuleName())
                    .referenceId(f.getReferenceId())
                    .usage(FileUsageDto.builder().moduleName(ref.moduleName()).referenceId(ref.referenceId())
                            .label(ref.label()).uiRoute(ref.uiRoute()).state(ref.state()).build())
                    .status(f.getStatus())
                    .scanStatus(f.getScanStatus())
                    .uploadedByUserId(f.getUploadedByUserId())
                    .uploadedByUsername(u == null ? null : u.getUsername())
                    .uploadedByFullName(u == null ? null : u.getFullName())
                    .uploadedByType(f.getUploadedByType())
                    .ownerStudentId(f.getOwnerStudentId())
                    .ownerStudentCode(s == null ? null : s.getStudentCode())
                    .ownerStudentName(s == null ? null : s.getFullName())
                    .checksumSha256(f.getChecksumSha256())
                    .storageBackend(f.getStorageBackend())
                    .createdAt(f.getCreatedAt())
                    .deletedAt(f.getDeletedAt())
                    .deletedBy(f.getDeletedBy())
                    .deleteReason(f.getDeleteReason())
                    .purgeAfter(f.getPurgeAfter())
                    .purgedAt(f.getPurgedAt())
                    .daysUntilPurge(daysUntilPurge(f, now))
                    .viewUrl(ADMIN_BASE + f.getId() + "/view")
                    .downloadUrl(ADMIN_BASE + f.getId() + "/download")
                    .build();
        }).toList();
    }

    static Integer daysUntilPurge(FileEntity f, LocalDateTime now) {
        if (f.getPurgeAfter() == null || !(FileConstants.Status.DELETED.equals(f.getStatus())
                || FileConstants.Status.PENDING.equals(f.getStatus()))) {
            return null;
        }
        long seconds = Duration.between(now, f.getPurgeAfter()).getSeconds();
        if (seconds <= 0) {
            return 0;
        }
        return (int) ((seconds + 86_399) / 86_400);
    }

    /** "Nơi sử dụng" qua descriptor của module; không có descriptor / lỗi -> nhãn trống. */
    FileReferenceInfo describe(String module, Long referenceId) {
        if (referenceId == null) {
            return FileReferenceInfo.none(module);
        }
        FileModuleDescriptor d = module == null ? null : descriptors.get(module.toUpperCase(Locale.ROOT));
        if (d == null) {
            return new FileReferenceInfo(module, referenceId, "Module chưa đăng ký", null,
                    FileConstants.RefState.NOT_FOUND);
        }
        try {
            FileReferenceInfo info = d.describeReference(referenceId);
            return info == null ? FileReferenceInfo.notFound(module, referenceId) : info;
        } catch (RuntimeException ex) {
            log.debug("describeReference {}#{} lỗi: {}", module, referenceId, ex.getMessage());
            return FileReferenceInfo.notFound(module, referenceId);
        }
    }

    // ---- Tiện ích ------------------------------------------------------------------------------------

    private FileEntity requireFile(Long id) {
        return fileRepository.findById(id).orElseThrow(() -> new OracleBusinessException(
                FileErrorCodes.FILE_NOT_FOUND, "Không tìm thấy file."));
    }

    private static OracleBusinessException invalidState(String message) {
        return new OracleBusinessException(FileAdminErrorCodes.FILE_INVALID_STATE, message);
    }

    private static OracleBusinessException settingInvalid(String message) {
        return new OracleBusinessException(FileAdminErrorCodes.FILE_SETTING_INVALID, message);
    }

    private static OracleBusinessException quotaInvalid(String message) {
        return new OracleBusinessException(FileAdminErrorCodes.FILE_QUOTA_INVALID, message);
    }

    private static String trimReason(String reason) {
        if (reason == null) {
            return null;
        }
        String r = reason.trim();
        return r.length() <= FileConstants.MAX_REASON_LENGTH ? r : r.substring(0, FileConstants.MAX_REASON_LENGTH);
    }

    private static FileStatBucketDto bucket(String key, String label, long count, long bytes) {
        return FileStatBucketDto.builder().key(key).label(label).fileCount(count).totalBytes(bytes).build();
    }

    private static List<Long> ids(List<Object[]> rows) {
        return rows.stream().map(r -> num(r[0])).toList();
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
