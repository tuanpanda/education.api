package com.education.base.service.impl;

import com.education.base.audit.AuditActions;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.document.DocErrorCodes;
import com.education.base.common.document.DocumentConstants;
import com.education.base.dto.request.DocFolderUpsertRequest;
import com.education.base.dto.response.DocBreadcrumbDto;
import com.education.base.dto.response.DocFolderDto;
import com.education.base.dto.response.DocFolderTreeNodeDto;
import com.education.base.dto.response.DocPermissionsDto;
import com.education.base.entity.DocFolderEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.DocFolderRepository;
import com.education.base.repository.DocItemRepository;
import com.education.base.repository.DocShareRepository;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AuditService;
import com.education.base.service.DocumentAccessService;
import com.education.base.service.DocumentFolderService;
import com.education.base.service.DocumentShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DocumentFolderServiceImpl implements DocumentFolderService {

    static final String MSG_NOT_FOUND = "Không tìm thấy thư mục.";
    static final String MSG_FORBIDDEN = "Bạn không có quyền quản lý thư mục.";

    private final DocumentAccessService accessService;
    private final DocumentShareService shareService;
    private final DocFolderRepository folderRepository;
    private final DocItemRepository itemRepository;
    private final DocShareRepository shareRepository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<DocFolderTreeNodeDto> getTree() {
        DocumentAccessService.Context ctx = accessService.current();
        Map<Long, Long> counts = visibleItemCounts(ctx);
        Set<Long> shown = shownFolderIds(ctx, counts);

        Map<Long, DocFolderTreeNodeDto> nodes = new LinkedHashMap<>();
        List<DocFolderEntity> sorted = ctx.getFolders().values().stream()
                .filter(f -> shown.contains(f.getId()))
                .sorted(Comparator.comparing(DocFolderEntity::getDepth)
                        .thenComparing(f -> f.getSortOrder() == null ? 0 : f.getSortOrder())
                        .thenComparing(f -> f.getName() == null ? "" : f.getName().toLowerCase()))
                .toList();
        for (DocFolderEntity f : sorted) {
            nodes.put(f.getId(), DocFolderTreeNodeDto.builder()
                    .id(f.getId())
                    .parentId(f.getParentId())
                    .name(f.getName())
                    .sortOrder(f.getSortOrder())
                    .navigationOnly(!ctx.folderVisible(f.getId()))
                    .itemCount(counts.getOrDefault(f.getId(), 0L))
                    .children(new ArrayList<>())
                    .build());
        }
        List<DocFolderTreeNodeDto> roots = new ArrayList<>();
        for (DocFolderTreeNodeDto n : nodes.values()) {
            DocFolderTreeNodeDto parent = n.getParentId() == null ? null : nodes.get(n.getParentId());
            if (parent == null) {
                roots.add(n);
            } else {
                parent.getChildren().add(n);
            }
        }
        return roots;
    }

    @Override
    @Transactional(readOnly = true)
    public DocFolderDto getFolder(Long id) {
        DocumentAccessService.Context ctx = accessService.current();
        DocFolderEntity folder = ctx.folder(id);
        if (folder == null) {
            throw notFound();
        }
        boolean visible = ctx.folderVisible(id);
        if (!visible) {
            Map<Long, Long> counts = visibleItemCounts(ctx);
            if (!shownFolderIds(ctx, counts).contains(id)) {
                throw notFound();
            }
        }
        return toDto(ctx, folder, !visible);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocFolderDto create(DocFolderUpsertRequest request) {
        DocumentAccessService.Context ctx = requireManage();
        DocFolderEntity parent = resolveParent(ctx, request.getParentId());
        String name = request.getName().trim();
        requireUniqueName(request.getParentId(), name, null);
        int depth = parent == null ? 1 : parent.getDepth() + 1;
        if (depth > DocumentConstants.MAX_FOLDER_DEPTH) {
            throw new OracleBusinessException(DocErrorCodes.DOC_FOLDER_TOO_DEEP, "Thư mục vượt quá độ sâu tối đa 8.");
        }
        Long id = folderRepository.nextId();
        DocFolderEntity folder = DocFolderEntity.builder()
                .id(id)
                .parentId(parent == null ? null : parent.getId())
                .name(name)
                .description(blankToNull(request.getDescription()))
                .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .treePath((parent == null ? "/" : parent.getTreePath()) + id + "/")
                .depth(depth)
                .ownerUserId(ctx.userId())
                .isDeleted(PersistenceFlags.NOT_DELETED)
                .createdBy(SecurityUtils.currentUsername())
                .build();
        folder = folderRepository.save(folder);
        ctx.getFolders().put(folder.getId(), folder);
        auditService.success(AuditActions.DOC_FOLDER_CREATED, AuditActions.RESOURCE_DOC_FOLDER, folder.getId(),
                Map.of("name", name, "parentId", String.valueOf(folder.getParentId())));
        return toDto(ctx, folder, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocFolderDto update(Long id, DocFolderUpsertRequest request) {
        DocumentAccessService.Context ctx = requireManage();
        DocFolderEntity folder = ctx.folder(id);
        if (folder == null) {
            throw notFound();
        }
        String name = request.getName().trim();
        Long newParentId = request.getParentId();
        boolean moved = !Objects.equals(folder.getParentId(), newParentId);
        requireUniqueName(newParentId, name, id);
        String username = SecurityUtils.currentUsername();
        if (moved) {
            DocFolderEntity parent = resolveParent(ctx, newParentId);
            if (parent != null && DocumentAccessService.pathIds(parent.getTreePath()).contains(id)) {
                throw new OracleBusinessException(DocErrorCodes.DOC_FOLDER_CYCLE,
                        "Không thể di chuyển thư mục vào chính nó hoặc thư mục con.");
            }
            String oldPath = folder.getTreePath();
            String newPath = (parent == null ? "/" : parent.getTreePath()) + id + "/";
            int newDepth = parent == null ? 1 : parent.getDepth() + 1;
            int delta = newDepth - folder.getDepth();
            List<DocFolderEntity> subtree = folderRepository.findSubtree(oldPath);
            int maxDepth = subtree.stream().mapToInt(DocFolderEntity::getDepth).max().orElse(folder.getDepth());
            if (maxDepth + delta > DocumentConstants.MAX_FOLDER_DEPTH) {
                throw new OracleBusinessException(DocErrorCodes.DOC_FOLDER_TOO_DEEP,
                        "Thư mục vượt quá độ sâu tối đa 8.");
            }
            for (DocFolderEntity f : subtree) {
                if (f.getId().equals(id)) {
                    continue;
                }
                f.setTreePath(newPath + f.getTreePath().substring(oldPath.length()));
                f.setDepth(f.getDepth() + delta);
                f.setUpdatedBy(username);
            }
            if (!subtree.isEmpty()) {
                folderRepository.saveAll(subtree.stream().filter(f -> !f.getId().equals(id)).toList());
            }
            folder.setParentId(newParentId);
            folder.setTreePath(newPath);
            folder.setDepth(newDepth);
        }
        folder.setName(name);
        folder.setDescription(blankToNull(request.getDescription()));
        if (request.getSortOrder() != null) {
            folder.setSortOrder(request.getSortOrder());
        }
        folder.setUpdatedBy(username);
        folder = folderRepository.save(folder);
        auditService.success(moved ? AuditActions.DOC_FOLDER_MOVED : AuditActions.DOC_FOLDER_UPDATED,
                AuditActions.RESOURCE_DOC_FOLDER, id,
                Map.of("name", name, "parentId", String.valueOf(newParentId)));
        return toDto(ctx, folder, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DocumentAccessService.Context ctx = requireManage();
        DocFolderEntity folder = ctx.folder(id);
        if (folder == null) {
            throw notFound();
        }
        if (folderRepository.countByParentIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED) > 0
                || itemRepository.countByFolderIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED) > 0) {
            throw new OracleBusinessException(DocErrorCodes.DOC_FOLDER_NOT_EMPTY,
                    "Chỉ xoá được thư mục rỗng (không còn thư mục con hoặc tài liệu).");
        }
        String username = SecurityUtils.currentUsername();
        folder.setIsDeleted(PersistenceFlags.DELETED);
        folder.setDeletedAt(LocalDateTime.now());
        folder.setDeletedBy(username);
        folder.setUpdatedBy(username);
        folderRepository.save(folder);
        shareRepository.deleteByFolderId(id);
        auditService.success(AuditActions.DOC_FOLDER_DELETED, AuditActions.RESOURCE_DOC_FOLDER, id,
                Map.of("name", folder.getName()));
    }

    // ---- nội bộ --------------------------------------------------------------------------------

    /** Số tài liệu thấy được theo thư mục (key NULL không dùng - gốc). */
    Map<Long, Long> visibleItemCounts(DocumentAccessService.Context ctx) {
        Map<Long, Long> counts = new HashMap<>();
        Set<Long> visibleFolders = ctx.visibleFolderIds();
        for (Object[] row : itemRepository.countByFolder()) {
            Long folderId = (Long) row[0];
            if (folderId != null && visibleFolders.contains(folderId)) {
                counts.put(folderId, ((Number) row[1]).longValue());
            }
        }
        if (!ctx.isManage()) {
            Set<Long> seen = new HashSet<>();
            List<Object[]> extra = new ArrayList<>(itemRepository.findIdAndFolderByOwner(ctx.userId()));
            if (!ctx.getSharedItemIds().isEmpty()) {
                extra.addAll(itemRepository.findIdAndFolderByIdIn(ctx.getSharedItemIds()));
            }
            for (Object[] row : extra) {
                Long itemId = (Long) row[0];
                Long folderId = (Long) row[1];
                if (folderId != null && !visibleFolders.contains(folderId) && seen.add(itemId)) {
                    counts.merge(folderId, 1L, Long::sum);
                }
            }
        }
        return counts;
    }

    /** Thư mục hiện trên cây: thấy được + tổ tiên của thư mục / tài liệu thấy được. */
    Set<Long> shownFolderIds(DocumentAccessService.Context ctx, Map<Long, Long> counts) {
        Set<Long> seeds = new HashSet<>(ctx.visibleFolderIds());
        counts.forEach((folderId, n) -> {
            if (n > 0) {
                seeds.add(folderId);
            }
        });
        Set<Long> shown = new HashSet<>();
        for (Long id : seeds) {
            DocFolderEntity f = ctx.folder(id);
            if (f == null) {
                continue;
            }
            for (DocFolderEntity a : ctx.chain(f)) {
                shown.add(a.getId());
            }
        }
        return shown;
    }

    private DocFolderDto toDto(DocumentAccessService.Context ctx, DocFolderEntity folder, boolean navigationOnly) {
        List<DocBreadcrumbDto> crumbs = ctx.chain(folder).stream()
                .map(f -> new DocBreadcrumbDto(f.getId(), f.getName())).toList();
        Map<Long, Long> counts = visibleItemCounts(ctx);
        Set<Long> shown = ctx.isManage() ? ctx.getFolders().keySet() : shownFolderIds(ctx, counts);
        long childCount = ctx.getFolders().values().stream()
                .filter(f -> Objects.equals(f.getParentId(), folder.getId()) && shown.contains(f.getId()))
                .count();
        return DocFolderDto.builder()
                .id(folder.getId())
                .parentId(folder.getParentId())
                .name(folder.getName())
                .description(folder.getDescription())
                .sortOrder(folder.getSortOrder())
                .breadcrumbs(crumbs)
                .childFolderCount(childCount)
                .itemCount(counts.getOrDefault(folder.getId(), 0L))
                .navigationOnly(navigationOnly)
                .shares(navigationOnly ? List.of() : shareService.describeFolderShares(ctx, folder, true))
                .permissions(DocPermissionsDto.builder()
                        .canEdit(ctx.isManage())
                        .canDelete(ctx.isManage())
                        .canShare(!navigationOnly && ctx.canShareFolder(folder.getId()))
                        .canUpload(!navigationOnly && ctx.canCreateIn(folder.getId()))
                        .build())
                .createdAt(folder.getCreatedAt())
                .createdBy(folder.getCreatedBy())
                .updatedAt(folder.getUpdatedAt())
                .updatedBy(folder.getUpdatedBy())
                .build();
    }

    private DocumentAccessService.Context requireManage() {
        DocumentAccessService.Context ctx = accessService.current();
        if (!ctx.isManage()) {
            throw new ForbiddenException(DocErrorCodes.DOC_FORBIDDEN, MSG_FORBIDDEN);
        }
        return ctx;
    }

    private DocFolderEntity resolveParent(DocumentAccessService.Context ctx, Long parentId) {
        if (parentId == null) {
            return null;
        }
        DocFolderEntity parent = ctx.folder(parentId);
        if (parent == null) {
            throw notFound();
        }
        return parent;
    }

    private void requireUniqueName(Long parentId, String name, Long excludeId) {
        if (folderRepository.countSiblingName(parentId == null ? 0L : parentId, name, excludeId) > 0) {
            throw new OracleBusinessException(DocErrorCodes.DOC_FOLDER_NAME_DUPLICATE,
                    "Đã có thư mục cùng tên trong thư mục cha.");
        }
    }

    private static OracleBusinessException notFound() {
        return new OracleBusinessException(DocErrorCodes.DOC_FOLDER_NOT_FOUND, MSG_NOT_FOUND);
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}