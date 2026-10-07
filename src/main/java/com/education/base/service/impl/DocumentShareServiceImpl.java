package com.education.base.service.impl;

import com.education.base.audit.AuditActions;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.document.DocErrorCodes;
import com.education.base.common.document.DocumentConstants;
import com.education.base.dto.request.DocShareRequest;
import com.education.base.dto.request.DocSharesUpdateRequest;
import com.education.base.dto.response.DocShareDto;
import com.education.base.dto.response.DocShareTargetsDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.DocFolderEntity;
import com.education.base.entity.DocItemEntity;
import com.education.base.entity.DocShareEntity;
import com.education.base.entity.RoleEntity;
import com.education.base.common.DomainConstants;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.DocItemRepository;
import com.education.base.repository.DocShareRepository;
import com.education.base.repository.RoleRepository;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AuditService;
import com.education.base.service.DocumentAccessService;
import com.education.base.service.DocumentShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentShareServiceImpl implements DocumentShareService {

    static final String MSG_FOLDER_NOT_FOUND = "Không tìm thấy thư mục.";
    static final String MSG_ITEM_NOT_FOUND = "Không tìm thấy tài liệu.";
    static final String MSG_FORBIDDEN = "Bạn không có quyền chia sẻ mục này.";
    static final String MSG_CLASS_FORBIDDEN = "Bạn chỉ được chia sẻ cho lớp mình dạy.";

    private final DocumentAccessService accessService;
    private final DocShareRepository shareRepository;
    private final DocItemRepository itemRepository;
    private final RoleRepository roleRepository;
    private final ClassRepository classRepository;
    private final AuditService auditService;

    // ---- API -------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<DocShareDto> getFolderShares(Long folderId) {
        DocumentAccessService.Context ctx = accessService.current();
        DocFolderEntity folder = requireVisibleFolder(ctx, folderId);
        return describeFolderShares(ctx, folder, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DocShareDto> putFolderShares(Long folderId, DocSharesUpdateRequest request) {
        DocumentAccessService.Context ctx = accessService.current();
        DocFolderEntity folder = requireVisibleFolder(ctx, folderId);
        if (!ctx.canShareFolder(folderId)) {
            throw new ForbiddenException(DocErrorCodes.DOC_FORBIDDEN, MSG_FORBIDDEN);
        }
        List<DocShareEntity> existing = shareRepository.findByFolderIdOrderByIdAsc(folderId);
        List<DocShareEntity> next = buildShares(ctx, request, existing, folderId, null);
        replace(existing, next, () -> shareRepository.deleteByFolderId(folderId));
        auditService.success(AuditActions.DOC_SHARES_CHANGED, AuditActions.RESOURCE_DOC_FOLDER, folderId,
                Map.of("count", next.size()));
        return describeFolderShares(ctx, folder, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocShareDto> getItemShares(Long itemId) {
        DocumentAccessService.Context ctx = accessService.current();
        DocItemEntity item = requireVisibleItem(ctx, itemId);
        return describeItemShares(ctx, item, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DocShareDto> putItemShares(Long itemId, DocSharesUpdateRequest request) {
        DocumentAccessService.Context ctx = accessService.current();
        DocItemEntity item = requireVisibleItem(ctx, itemId);
        if (!ctx.canShare(item)) {
            throw new ForbiddenException(DocErrorCodes.DOC_FORBIDDEN, MSG_FORBIDDEN);
        }
        List<DocShareEntity> existing = shareRepository.findByItemIdOrderByIdAsc(itemId);
        List<DocShareEntity> next = buildShares(ctx, request, existing, null, itemId);
        replace(existing, next, () -> shareRepository.deleteByItemId(itemId));
        auditService.success(AuditActions.DOC_SHARES_CHANGED, AuditActions.RESOURCE_DOC_ITEM, itemId,
                Map.of("count", next.size()));
        return describeItemShares(ctx, item, true);
    }

    @Override
    @Transactional(readOnly = true)
    public DocShareTargetsDto getShareTargets() {
        DocumentAccessService.Context ctx = accessService.current();
        if (ctx.canShareAnyTarget()) {
            List<DocShareTargetsDto.Option> roles = roleRepository.findAll().stream()
                    .filter(r -> Objects.equals(r.getIsDeleted(), PersistenceFlags.NOT_DELETED))
                    .filter(r -> !"INACTIVE".equalsIgnoreCase(r.getStatus()))
                    .filter(r -> !"ROLE_STUDENT".equals(r.getRoleCode()) && !"ROLE_PARENT".equals(r.getRoleCode()))
                    .sorted(Comparator.comparing(RoleEntity::getRoleName, Comparator.nullsLast(String::compareTo)))
                    .map(r -> new DocShareTargetsDto.Option(r.getId(), r.getRoleCode(), r.getRoleName()))
                    .toList();
            List<DocShareTargetsDto.Option> classes = classRepository.findAll().stream()
                    .filter(c -> Objects.equals(c.getIsDeleted(), PersistenceFlags.NOT_DELETED))
                    .filter(c -> !DomainConstants.CLASS_STATUS_CLOSED.equals(c.getStatus())
                            && !DomainConstants.CLASS_STATUS_CANCELLED.equals(c.getStatus()))
                    .sorted(Comparator.comparing(ClassEntity::getClassCode, Comparator.nullsLast(String::compareTo)))
                    .map(this::classOption)
                    .toList();
            return DocShareTargetsDto.builder().allStaffAllowed(true).roles(roles).classes(classes).build();
        }
        Set<Long> taught = ctx.getTaughtClassIds();
        List<DocShareTargetsDto.Option> classes = taught.isEmpty() ? List.of()
                : classRepository.findByIdInAndIsDeletedOrderByClassCode(taught, PersistenceFlags.NOT_DELETED)
                .stream().map(this::classOption).toList();
        return DocShareTargetsDto.builder().allStaffAllowed(false).roles(List.of()).classes(classes).build();
    }

    // ---- Dùng chung với service khác -------------------------------------------------------------

    @Override
    public List<DocShareDto> describeFolderShares(DocumentAccessService.Context ctx, DocFolderEntity folder,
                                                  boolean includeInherited) {
        List<DocShareEntity> all = new ArrayList<>(shareRepository.findByFolderIdOrderByIdAsc(folder.getId()));
        if (includeInherited) {
            List<DocFolderEntity> chain = ctx.chain(folder);
            List<Long> ancestors = chain.stream().map(DocFolderEntity::getId)
                    .filter(id -> !id.equals(folder.getId())).toList();
            all.addAll(sortedByChain(ancestors, folderShares(ancestors)));
        }
        return toDtos(ctx, all, folder.getId(), null);
    }

    @Override
    public List<DocShareDto> describeItemShares(DocumentAccessService.Context ctx, DocItemEntity item,
                                                boolean includeInherited) {
        List<DocShareEntity> all = new ArrayList<>(shareRepository.findByItemIdOrderByIdAsc(item.getId()));
        if (includeInherited && item.getFolderId() != null) {
            List<Long> chain = ctx.chain(ctx.folder(item.getFolderId())).stream().map(DocFolderEntity::getId).toList();
            all.addAll(sortedByChain(chain, folderShares(chain)));
        }
        return toDtos(ctx, all, null, item.getId());
    }

    @Override
    public Map<Long, List<DocShareEntity>> directItemShares(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        return shareRepository.findByItemIdIn(itemIds).stream()
                .collect(Collectors.groupingBy(DocShareEntity::getItemId, LinkedHashMap::new, Collectors.toList()));
    }

    @Override
    public Map<Long, List<DocShareEntity>> folderShares(Collection<Long> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return Map.of();
        }
        return shareRepository.findByFolderIdIn(folderIds).stream()
                .collect(Collectors.groupingBy(DocShareEntity::getFolderId, LinkedHashMap::new, Collectors.toList()));
    }

    @Override
    public List<DocShareDto> toDtos(DocumentAccessService.Context ctx, List<DocShareEntity> shares) {
        return toDtos(ctx, shares, null, null);
    }

    @Override
    public boolean visibleToStudents(DocumentAccessService.Context ctx, DocItemEntity item,
                                     List<DocShareEntity> direct, Map<Long, List<DocShareEntity>> folderShares) {
        LocalDateTime now = LocalDateTime.now();
        List<DocShareEntity> all = new ArrayList<>(direct == null ? List.of() : direct);
        if (item.getFolderId() != null && folderShares != null) {
            for (DocFolderEntity f : ctx.chain(ctx.folder(item.getFolderId()))) {
                all.addAll(folderShares.getOrDefault(f.getId(), List.of()));
            }
        }
        return all.stream().anyMatch(s -> DocumentConstants.TARGET_CLASS.equals(s.getTargetType())
                && Objects.equals(s.getVisibleToStudents(), 1) && s.isActiveAt(now));
    }

    // ---- Nội bộ ------------------------------------------------------------------------------------

    private List<DocShareEntity> sortedByChain(List<Long> chain, Map<Long, List<DocShareEntity>> byFolder) {
        List<DocShareEntity> out = new ArrayList<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            out.addAll(byFolder.getOrDefault(chain.get(i), List.of()));
        }
        return out;
    }

    private List<DocShareDto> toDtos(DocumentAccessService.Context ctx, List<DocShareEntity> shares,
                                     Long ownFolderId, Long ownItemId) {
        if (shares.isEmpty()) {
            return List.of();
        }
        Set<Long> roleIds = new HashSet<>();
        Set<Long> classIds = new HashSet<>();
        for (DocShareEntity s : shares) {
            if (s.getRoleId() != null) {
                roleIds.add(s.getRoleId());
            }
            if (s.getClassId() != null) {
                classIds.add(s.getClassId());
            }
        }
        Map<Long, String> roleLabels = new HashMap<>();
        if (!roleIds.isEmpty()) {
            roleRepository.findAllById(roleIds).forEach(r -> roleLabels.put(r.getId(), r.getRoleName()));
        }
        Map<Long, String> classLabels = new HashMap<>();
        if (!classIds.isEmpty()) {
            classRepository.findAllById(classIds).forEach(c -> classLabels.put(c.getId(), classLabel(c)));
        }
        List<DocShareDto> out = new ArrayList<>(shares.size());
        for (DocShareEntity s : shares) {
            boolean inherited = s.getFolderId() != null
                    && (ownItemId != null || !Objects.equals(s.getFolderId(), ownFolderId));
            DocFolderEntity from = inherited ? ctx.folder(s.getFolderId()) : null;
            String label = switch (s.getTargetType()) {
                case DocumentConstants.TARGET_ROLE -> roleLabels.get(s.getRoleId());
                case DocumentConstants.TARGET_CLASS -> classLabels.get(s.getClassId());
                default -> null;
            };
            out.add(DocShareDto.builder()
                    .id(s.getId())
                    .folderId(s.getFolderId())
                    .itemId(s.getItemId())
                    .targetType(s.getTargetType())
                    .targetId(s.targetId())
                    .targetLabel(label)
                    .visibleToStudents(Objects.equals(s.getVisibleToStudents(), 1))
                    .expiresAt(s.getExpiresAt())
                    .inherited(inherited)
                    .inheritedFromFolderId(inherited ? s.getFolderId() : null)
                    .inheritedFromFolderName(from == null ? null : from.getName())
                    .createdAt(s.getCreatedAt())
                    .createdBy(s.getCreatedBy())
                    .build());
        }
        return out;
    }

    private List<DocShareEntity> buildShares(DocumentAccessService.Context ctx, DocSharesUpdateRequest request,
                                             List<DocShareEntity> existing, Long folderId, Long itemId) {
        List<DocShareRequest> reqs = request == null || request.getShares() == null ? List.of() : request.getShares();
        if (reqs.size() > DocumentConstants.MAX_SHARES_PER_TARGET) {
            throw new OracleBusinessException(DocErrorCodes.DOC_SHARE_INVALID, "Tối đa 50 đích chia sẻ.");
        }
        Map<String, DocShareEntity> existingByKey = new HashMap<>();
        for (DocShareEntity e : existing) {
            existingByKey.put(key(e.getTargetType(), e.targetId()), e);
        }
        LocalDateTime now = LocalDateTime.now();
        Set<String> seen = new HashSet<>();
        Set<Long> roleIds = new HashSet<>();
        Set<Long> classIds = new HashSet<>();
        String username = SecurityUtils.currentUsername();
        List<DocShareEntity> out = new ArrayList<>();
        for (DocShareRequest r : reqs) {
            String type = r.getTargetType();
            if (type == null || !type.matches(DocumentConstants.TARGET_TYPE_PATTERN) || !r.isTargetConsistent()) {
                throw new OracleBusinessException(DocErrorCodes.DOC_SHARE_INVALID, "Đích chia sẻ không hợp lệ.");
            }
            String k = key(type, r.getTargetId());
            if (!seen.add(k)) {
                throw new OracleBusinessException(DocErrorCodes.DOC_SHARE_INVALID, "Đích chia sẻ bị trùng.");
            }
            DocShareEntity prev = existingByKey.get(k);
            boolean unchangedExpiry = prev != null && Objects.equals(prev.getExpiresAt(), r.getExpiresAt());
            if (r.getExpiresAt() != null && !r.getExpiresAt().isAfter(now) && !unchangedExpiry) {
                throw new OracleBusinessException(DocErrorCodes.DOC_SHARE_INVALID,
                        "Hạn chia sẻ phải ở tương lai.");
            }
            if (!ctx.canShareAnyTarget()) {
                boolean taughtClass = DocumentConstants.TARGET_CLASS.equals(type)
                        && ctx.getTaughtClassIds().contains(r.getTargetId());
                boolean keptAsIs = prev != null && unchangedExpiry
                        && Objects.equals(prev.getVisibleToStudents(), r.isVisibleToStudents() ? 1 : 0);
                if (!taughtClass && !keptAsIs) {
                    throw new ForbiddenException(DocErrorCodes.DOC_SHARE_CLASS_FORBIDDEN, MSG_CLASS_FORBIDDEN);
                }
            }
            if (DocumentConstants.TARGET_ROLE.equals(type)) {
                roleIds.add(r.getTargetId());
            } else if (DocumentConstants.TARGET_CLASS.equals(type)) {
                classIds.add(r.getTargetId());
            }
            out.add(DocShareEntity.builder()
                    .folderId(folderId)
                    .itemId(itemId)
                    .targetType(type)
                    .roleId(DocumentConstants.TARGET_ROLE.equals(type) ? r.getTargetId() : null)
                    .classId(DocumentConstants.TARGET_CLASS.equals(type) ? r.getTargetId() : null)
                    .visibleToStudents(r.isVisibleToStudents() ? 1 : 0)
                    .expiresAt(r.getExpiresAt())
                    .createdAt(prev != null ? prev.getCreatedAt() : null)
                    .createdBy(prev != null ? prev.getCreatedBy() : username)
                    .updatedBy(prev != null ? username : null)
                    .build());
        }
        requireTargetsExist(roleIds, classIds);
        return out;
    }

    private void requireTargetsExist(Set<Long> roleIds, Set<Long> classIds) {
        if (!roleIds.isEmpty()) {
            long found = roleRepository.findByIdInAndIsDeleted(roleIds, PersistenceFlags.NOT_DELETED).size();
            if (found != roleIds.size()) {
                throw new OracleBusinessException(DocErrorCodes.DOC_SHARE_TARGET_NOT_FOUND,
                        "Không tìm thấy vai trò được chia sẻ.");
            }
        }
        if (!classIds.isEmpty()) {
            long found = classRepository.findByIdInAndIsDeletedOrderByClassCode(classIds,
                    PersistenceFlags.NOT_DELETED).size();
            if (found != classIds.size()) {
                throw new OracleBusinessException(DocErrorCodes.DOC_SHARE_TARGET_NOT_FOUND,
                        "Không tìm thấy lớp được chia sẻ.");
            }
        }
    }

    private void replace(List<DocShareEntity> existing, List<DocShareEntity> next, Runnable deleteAll) {
        if (!existing.isEmpty()) {
            deleteAll.run();
        }
        if (!next.isEmpty()) {
            shareRepository.saveAll(next);
        }
    }

    private DocFolderEntity requireVisibleFolder(DocumentAccessService.Context ctx, Long folderId) {
        if (!ctx.folderVisible(folderId)) {
            throw new OracleBusinessException(DocErrorCodes.DOC_FOLDER_NOT_FOUND, MSG_FOLDER_NOT_FOUND);
        }
        return ctx.folder(folderId);
    }

    private DocItemEntity requireVisibleItem(DocumentAccessService.Context ctx, Long itemId) {
        DocItemEntity item = itemId == null ? null
                : itemRepository.findByIdAndIsDeleted(itemId, PersistenceFlags.NOT_DELETED).orElse(null);
        if (item == null || !ctx.itemVisible(item)) {
            throw new OracleBusinessException(DocErrorCodes.DOC_ITEM_NOT_FOUND, MSG_ITEM_NOT_FOUND);
        }
        return item;
    }

    private DocShareTargetsDto.Option classOption(ClassEntity c) {
        return new DocShareTargetsDto.Option(c.getId(), c.getClassCode(), classLabel(c));
    }

    static String classLabel(ClassEntity c) {
        String code = c.getClassCode() == null ? "" : c.getClassCode();
        return c.getClassName() == null || c.getClassName().isBlank() ? code : code + " - " + c.getClassName();
    }

    private static String key(String type, Long targetId) {
        return type + ":" + (targetId == null ? "" : targetId);
    }
}