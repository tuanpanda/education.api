package com.education.base.service.impl;

import com.education.base.audit.AuditActions;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.document.DocErrorCodes;
import com.education.base.common.document.DocumentConstants;
import com.education.base.dto.request.DocItemCreateRequest;
import com.education.base.dto.request.DocItemFilterRequest;
import com.education.base.dto.request.DocItemUpdateRequest;
import com.education.base.dto.response.DocItemDto;
import com.education.base.dto.response.DocPermissionsDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.entity.DocFolderEntity;
import com.education.base.entity.DocItemEntity;
import com.education.base.entity.DocShareEntity;
import com.education.base.entity.FileEntity;
import com.education.base.entity.UserEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapper;
import com.education.base.repository.DocItemRepository;
import com.education.base.repository.DocShareRepository;
import com.education.base.repository.UserRepository;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AuditService;
import com.education.base.service.DocumentAccessService;
import com.education.base.service.DocumentItemService;
import com.education.base.service.DocumentShareService;
import com.education.base.service.FileStorageService;
import com.education.base.service.file.FileLifecycleService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class DocumentItemServiceImpl implements DocumentItemService {

    static final String MSG_NOT_FOUND = "Không tìm thấy tài liệu.";
    static final String MSG_FOLDER_NOT_FOUND = "Không tìm thấy thư mục.";
    static final String MSG_FORBIDDEN = "Bạn không có quyền thao tác trên tài liệu này.";
    static final String MSG_CREATE_FORBIDDEN = "Bạn không có quyền tạo tài liệu trong thư mục này.";
    static final String REASON_REPLACED = "DOC_ITEM_FILE_REPLACED";
    static final String REASON_DELETED = "DOC_ITEM_DELETED";
    static final int ORACLE_IN_LIMIT = 900;
    static final int MAX_TAG_SUGGESTIONS = 20;

    private final DocumentAccessService accessService;
    private final DocumentShareService shareService;
    private final DocItemRepository itemRepository;
    private final DocShareRepository shareRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final FileLifecycleService fileLifecycleService;
    private final FileMapper fileMapper;
    private final AuditService auditService;

    // ---- Tìm kiếm ---------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DocItemDto> search(DocItemFilterRequest filter) {
        DocItemFilterRequest f = filter == null ? new DocItemFilterRequest() : filter;
        DocumentAccessService.Context ctx = accessService.current();
        int pageNo = Math.max(f.getPageNo(), 1);
        int pageSize = Math.min(Math.max(f.getPageSize(), 1), DocumentConstants.MAX_PAGE_SIZE);
        if (f.getFolderId() != null && ctx.folder(f.getFolderId()) == null) {
            throw new OracleBusinessException(DocErrorCodes.DOC_FOLDER_NOT_FOUND, MSG_FOLDER_NOT_FOUND);
        }
        Specification<DocItemEntity> spec = buildSpec(ctx, f);
        if (spec == null) {
            return PageResponse.of(List.of(), pageNo, pageSize, 0);
        }
        Sort.Direction dir = "desc".equalsIgnoreCase(f.getSortDir()) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortProp = switch (f.getSortBy() == null ? "title" : f.getSortBy()) {
            case "createdAt" -> "createdAt";
            case "updatedAt" -> "updatedAt";
            case "fileSize" -> "file.fileSize";
            default -> "title";
        };
        PageRequest pageable = PageRequest.of(pageNo - 1, pageSize,
                Sort.by(new Sort.Order(dir, sortProp), Sort.Order.desc("id")));
        Page<DocItemEntity> page = itemRepository.findAll(spec, pageable);
        return PageResponse.of(toDtos(ctx, page.getContent(), false), pageNo, pageSize, page.getTotalElements());
    }

    /** {@code null} = chắc chắn không có kết quả. */
    Specification<DocItemEntity> buildSpec(DocumentAccessService.Context ctx, DocItemFilterRequest f) {
        if (!ctx.isStaff()) {
            return null;
        }
        Set<Long> folderScope = null;
        boolean rootOnly = false;
        if (f.getFolderId() == null) {
            rootOnly = !f.isIncludeSubfolders();
        } else if (f.isIncludeSubfolders()) {
            folderScope = ctx.subtreeIds(f.getFolderId());
        } else {
            folderScope = Set.of(f.getFolderId());
        }
        Set<Long> classItemIds = null;
        Set<Long> classFolderIds = null;
        if (f.getClassId() != null) {
            classItemIds = new HashSet<>();
            classFolderIds = new HashSet<>();
            for (DocShareEntity s : shareRepository.findActiveClassShares(f.getClassId(), LocalDateTime.now())) {
                if (s.getItemId() != null) {
                    classItemIds.add(s.getItemId());
                } else if (s.getFolderId() != null) {
                    classFolderIds.addAll(ctx.subtreeIds(s.getFolderId()));
                }
            }
            if (classItemIds.isEmpty() && classFolderIds.isEmpty()) {
                return null;
            }
        }
        Set<Long> visibleFolders = ctx.isManage() ? null : ctx.visibleFolderIds();
        Long userId = ctx.userId();
        final Set<Long> scope = folderScope;
        final boolean root = rootOnly;
        final Set<Long> cItems = classItemIds;
        final Set<Long> cFolders = classFolderIds;
        String q = f.getQ() == null || f.getQ().isBlank() ? null : "%" + escapeLike(f.getQ().trim().toLowerCase(Locale.ROOT)) + "%";
        String tag = normalizeTag(f.getTag());
        String ext = f.getExt() == null || f.getExt().isBlank() ? null : f.getExt().trim().toLowerCase(Locale.ROOT);
        boolean mine = f.isMine();
        Set<Long> sharedItems = ctx.getSharedItemIds();
        return (rootEntity, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(rootEntity.get("isDeleted"), PersistenceFlags.NOT_DELETED));
            if (visibleFolders != null) {
                List<Predicate> any = new ArrayList<>();
                any.add(cb.equal(rootEntity.get("ownerUserId"), userId));
                any.addAll(inChunks(rootEntity.get("id"), sharedItems));
                any.addAll(inChunks(rootEntity.get("folderId"), visibleFolders));
                ps.add(cb.or(any.toArray(Predicate[]::new)));
            }
            if (root) {
                ps.add(cb.isNull(rootEntity.get("folderId")));
            } else if (scope != null) {
                ps.add(orOf(cb, inChunks(rootEntity.get("folderId"), scope)));
            }
            if (cItems != null) {
                List<Predicate> any = new ArrayList<>(inChunks(rootEntity.get("id"), cItems));
                any.addAll(inChunks(rootEntity.get("folderId"), cFolders));
                ps.add(orOf(cb, any));
            }
            if (mine) {
                ps.add(cb.equal(rootEntity.get("ownerUserId"), userId));
            }
            if (tag != null) {
                ps.add(cb.like(rootEntity.get("tags"), "%," + escapeLike(tag) + ",%", '\\'));
            }
            if (q != null || ext != null) {
                Join<DocItemEntity, FileEntity> file = rootEntity.join("file", JoinType.LEFT);
                if (q != null) {
                    ps.add(cb.or(
                            cb.like(cb.lower(rootEntity.get("title")), q, '\\'),
                            cb.like(cb.lower(rootEntity.get("description")), q, '\\'),
                            cb.like(cb.lower(file.get("originalName")), q, '\\')));
                }
                if (ext != null) {
                    ps.add(cb.equal(cb.lower(file.get("fileExt")), ext));
                }
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    // ---- CRUD -----------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public DocItemDto getItem(Long id) {
        DocumentAccessService.Context ctx = accessService.current();
        return toDtos(ctx, List.of(requireVisible(ctx, id)), true).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocItemDto create(MultipartFile file, DocItemCreateRequest request) {
        DocItemCreateRequest req = request == null ? new DocItemCreateRequest() : request;
        DocumentAccessService.Context ctx = accessService.current();
        requireCanCreateIn(ctx, req.getFolderId());
        Long id = itemRepository.nextId();
        FileEntity stored = fileStorageService.storeFile(file, DocumentConstants.FILE_MODULE, id);
        String title = blankToNull(req.getTitle());
        if (title == null) {
            title = defaultTitle(stored.getOriginalName());
        }
        String username = SecurityUtils.currentUsername();
        DocItemEntity item = DocItemEntity.builder()
                .id(id)
                .folderId(req.getFolderId())
                .fileId(stored.getId())
                .file(stored)
                .title(title)
                .description(blankToNull(req.getDescription()))
                .tags(encodeTags(req.getTags()))
                .versionNo(1)
                .versionNote(blankToNull(req.getVersionNote()))
                .ownerUserId(ctx.userId())
                .isDeleted(PersistenceFlags.NOT_DELETED)
                .createdBy(username)
                .build();
        item = itemRepository.save(item);
        item.setFile(stored);
        auditService.success(AuditActions.DOC_ITEM_CREATED, AuditActions.RESOURCE_DOC_ITEM, id,
                Map.of("fileId", stored.getId(), "folderId", String.valueOf(req.getFolderId())));
        return toDtos(ctx, List.of(item), true).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocItemDto update(Long id, DocItemUpdateRequest request) {
        DocumentAccessService.Context ctx = accessService.current();
        DocItemEntity item = requireVisible(ctx, id);
        if (!ctx.canEdit(item)) {
            throw new ForbiddenException(DocErrorCodes.DOC_FORBIDDEN, MSG_FORBIDDEN);
        }
        boolean moved = !Objects.equals(item.getFolderId(), request.getFolderId());
        if (moved) {
            requireCanCreateIn(ctx, request.getFolderId());
        }
        item.setFolderId(request.getFolderId());
        item.setTitle(request.getTitle().trim());
        item.setDescription(blankToNull(request.getDescription()));
        if (request.getTags() != null) {
            item.setTags(encodeTags(request.getTags()));
        }
        item.setUpdatedBy(SecurityUtils.currentUsername());
        item = itemRepository.save(item);
        auditService.success(moved ? AuditActions.DOC_ITEM_MOVED : AuditActions.DOC_ITEM_UPDATED,
                AuditActions.RESOURCE_DOC_ITEM, id, Map.of("folderId", String.valueOf(item.getFolderId())));
        return toDtos(ctx, List.of(item), true).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocItemDto replaceFile(Long id, MultipartFile file, String versionNote) {
        DocumentAccessService.Context ctx = accessService.current();
        DocItemEntity item = requireVisible(ctx, id);
        if (!ctx.canEdit(item)) {
            throw new ForbiddenException(DocErrorCodes.DOC_FORBIDDEN, MSG_FORBIDDEN);
        }
        if (versionNote != null && versionNote.length() > DocumentConstants.VERSION_NOTE_MAX) {
            throw new OracleBusinessException("VALIDATION_ERROR",
                    "Ghi chú phiên bản không được vượt quá 500 ký tự");
        }
        Long oldFileId = item.getFileId();
        FileEntity stored = fileStorageService.storeFile(file, DocumentConstants.FILE_MODULE, id);
        item.setFileId(stored.getId());
        item.setFile(stored);
        item.setVersionNo((item.getVersionNo() == null ? 1 : item.getVersionNo()) + 1);
        item.setVersionNote(blankToNull(versionNote));
        item.setUpdatedBy(SecurityUtils.currentUsername());
        item = itemRepository.save(item);
        item.setFile(stored);
        if (oldFileId != null) {
            fileLifecycleService.softDelete(oldFileId, REASON_REPLACED);
        }
        auditService.success(AuditActions.DOC_ITEM_FILE_REPLACED, AuditActions.RESOURCE_DOC_ITEM, id,
                Map.of("oldFileId", String.valueOf(oldFileId), "fileId", stored.getId(),
                        "versionNo", item.getVersionNo()));
        return toDtos(ctx, List.of(item), true).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DocumentAccessService.Context ctx = accessService.current();
        DocItemEntity item = requireVisible(ctx, id);
        if (!ctx.canDelete(item)) {
            throw new ForbiddenException(DocErrorCodes.DOC_FORBIDDEN, MSG_FORBIDDEN);
        }
        String username = SecurityUtils.currentUsername();
        item.setIsDeleted(PersistenceFlags.DELETED);
        item.setDeletedAt(LocalDateTime.now());
        item.setDeletedBy(username);
        item.setUpdatedBy(username);
        itemRepository.save(item);
        shareRepository.deleteByItemId(id);
        if (item.getFileId() != null) {
            fileLifecycleService.softDelete(item.getFileId(), REASON_DELETED);
        }
        auditService.success(AuditActions.DOC_ITEM_DELETED, AuditActions.RESOURCE_DOC_ITEM, id,
                Map.of("fileId", String.valueOf(item.getFileId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> suggestTags(String prefix) {
        DocumentAccessService.Context ctx = accessService.current();
        DocItemFilterRequest f = DocItemFilterRequest.builder().includeSubfolders(true).build();
        Specification<DocItemEntity> spec = buildSpec(ctx, f);
        if (spec == null) {
            return List.of();
        }
        Specification<DocItemEntity> withTags = spec.and((r, q, cb) -> cb.isNotNull(r.get("tags")));
        String p = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        TreeSet<String> out = new TreeSet<>();
        for (DocItemEntity i : itemRepository.findAll(withTags)) {
            for (String t : decodeTags(i.getTags())) {
                if (t.startsWith(p)) {
                    out.add(t);
                }
            }
        }
        return out.stream().limit(MAX_TAG_SUGGESTIONS).toList();
    }

    // ---- DTO ------------------------------------------------------------------------------------

    List<DocItemDto> toDtos(DocumentAccessService.Context ctx, List<DocItemEntity> items, boolean detail) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> ids = items.stream().map(DocItemEntity::getId).toList();
        Map<Long, List<DocShareEntity>> direct = shareService.directItemShares(ids);
        Set<Long> folderIds = new LinkedHashSet<>();
        for (DocItemEntity i : items) {
            DocFolderEntity folder = ctx.folder(i.getFolderId());
            ctx.chain(folder).forEach(a -> folderIds.add(a.getId()));
        }
        Map<Long, List<DocShareEntity>> folderShares = shareService.folderShares(folderIds);
        Set<Long> ownerIds = new HashSet<>();
        items.forEach(i -> ownerIds.add(i.getOwnerUserId()));
        Map<Long, UserEntity> owners = new HashMap<>();
        ownerIds.remove(null);
        if (!ownerIds.isEmpty()) {
            userRepository.findAllById(ownerIds).forEach(u -> owners.put(u.getId(), u));
        }
        List<DocItemDto> out = new ArrayList<>(items.size());
        for (DocItemEntity i : items) {
            DocFolderEntity folder = ctx.folder(i.getFolderId());
            UserEntity owner = owners.get(i.getOwnerUserId());
            List<DocShareEntity> d = direct.getOrDefault(i.getId(), List.of());
            out.add(DocItemDto.builder()
                    .id(i.getId())
                    .folderId(i.getFolderId())
                    .folderName(folder == null ? null : folder.getName())
                    .title(i.getTitle())
                    .description(i.getDescription())
                    .tags(decodeTags(i.getTags()))
                    .versionNo(i.getVersionNo())
                    .versionNote(i.getVersionNote())
                    .file(i.getFile() == null ? null : fileMapper.toDto(i.getFile()))
                    .ownerUserId(i.getOwnerUserId())
                    .ownerUsername(owner == null ? null : owner.getUsername())
                    .ownerFullName(owner == null ? null : owner.getFullName())
                    .shares(detail ? shareService.describeItemShares(ctx, i, true) : shareService.toDtos(ctx, d))
                    .visibleToStudents(shareService.visibleToStudents(ctx, i, d, folderShares))
                    .permissions(DocPermissionsDto.builder()
                            .canEdit(ctx.canEdit(i))
                            .canDelete(ctx.canDelete(i))
                            .canShare(ctx.canShare(i))
                            .canUpload(false)
                            .build())
                    .createdAt(i.getCreatedAt())
                    .createdBy(i.getCreatedBy())
                    .updatedAt(i.getUpdatedAt())
                    .updatedBy(i.getUpdatedBy())
                    .build());
        }
        return out;
    }

    // ---- nội bộ ---------------------------------------------------------------------------------

    private DocItemEntity requireVisible(DocumentAccessService.Context ctx, Long id) {
        DocItemEntity item = id == null ? null
                : itemRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED).orElse(null);
        if (item == null || !ctx.itemVisible(item)) {
            throw new OracleBusinessException(DocErrorCodes.DOC_ITEM_NOT_FOUND, MSG_NOT_FOUND);
        }
        return item;
    }

    private void requireCanCreateIn(DocumentAccessService.Context ctx, Long folderId) {
        if (folderId != null && (ctx.folder(folderId) == null || !ctx.folderVisible(folderId))) {
            throw new OracleBusinessException(DocErrorCodes.DOC_FOLDER_NOT_FOUND, MSG_FOLDER_NOT_FOUND);
        }
        if (!ctx.canCreateIn(folderId)) {
            throw new ForbiddenException(DocErrorCodes.DOC_FORBIDDEN, MSG_CREATE_FORBIDDEN);
        }
    }

    private static List<Predicate> inChunks(jakarta.persistence.criteria.Path<Object> path, Collection<Long> ids) {
        List<Predicate> out = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        List<Long> list = new ArrayList<>(ids);
        for (int i = 0; i < list.size(); i += ORACLE_IN_LIMIT) {
            out.add(path.in(list.subList(i, Math.min(list.size(), i + ORACLE_IN_LIMIT))));
        }
        return out;
    }

    private static Predicate orOf(jakarta.persistence.criteria.CriteriaBuilder cb, List<Predicate> ps) {
        return ps.isEmpty() ? cb.disjunction() : cb.or(ps.toArray(Predicate[]::new));
    }

    static String normalizeTag(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().toLowerCase(Locale.ROOT).replace(",", " ").replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? null : t;
    }

    static String encodeTags(List<String> tags) {
        if (tags == null) {
            return null;
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String raw : tags) {
            String t = normalizeTag(raw);
            if (t != null) {
                if (t.length() > DocumentConstants.TAG_MAX) {
                    throw new OracleBusinessException("VALIDATION_ERROR", "Mỗi thẻ tối đa 50 ký tự");
                }
                set.add(t);
            }
        }
        if (set.size() > DocumentConstants.MAX_TAGS_PER_ITEM) {
            throw new OracleBusinessException("VALIDATION_ERROR", "Tối đa 10 thẻ");
        }
        if (set.isEmpty()) {
            return null;
        }
        String encoded = "," + String.join(",", set) + ",";
        if (encoded.length() > DocumentConstants.ITEM_TAGS_MAX) {
            throw new OracleBusinessException("VALIDATION_ERROR", "Tổng độ dài thẻ vượt quá 500 ký tự");
        }
        return encoded;
    }

    static List<String> decodeTags(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String t : encoded.split(",")) {
            if (!t.isBlank()) {
                out.add(t);
            }
        }
        return out;
    }

    static String defaultTitle(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "Tài liệu";
        }
        String name = originalName.trim();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base.length() > DocumentConstants.ITEM_TITLE_MAX
                ? base.substring(0, DocumentConstants.ITEM_TITLE_MAX) : base;
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}