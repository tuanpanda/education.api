package com.education.base.service;

import com.education.base.common.PersistenceFlags;
import com.education.base.entity.DocFolderEntity;
import com.education.base.entity.DocItemEntity;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.repository.DocFolderRepository;
import com.education.base.repository.DocShareRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import com.education.base.security.SecurityUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Quy tắc "thấy" / "ghi" của kho tài liệu (docs/files-api.md mục 7).
 * <p>
 * Một {@link Context} chụp lại: quyền, vai trò, lớp dạy thật ({@code taught(U)} - GVCN hoặc có buổi dạy chưa huỷ,
 * không dùng nhánh "không giới hạn" của {@code TeachingAssignmentGuard}), toàn bộ thư mục chưa xoá, các thư mục /
 * tài liệu có chia sẻ trực tiếp còn hạn áp dụng cho người dùng.
 */
@Service
@RequiredArgsConstructor
public class DocumentAccessService {

    static final String SESSION_STATUS_CANCELLED = "CANCELLED";
    /** Giá trị giữ chỗ cho mệnh đề IN rỗng. */
    static final List<Long> NONE = List.of(-1L);

    private final DocFolderRepository folderRepository;
    private final DocShareRepository shareRepository;
    private final ClassRepository classRepository;
    private final ClassSessionRepository classSessionRepository;

    @Transactional(readOnly = true)
    public Context current() {
        return forUser(SecurityUtils.requireCurrentUser());
    }

    @Transactional(readOnly = true)
    public Context forUser(AuthUserPrincipal user) {
        Map<Long, DocFolderEntity> folders = new HashMap<>();
        for (DocFolderEntity f : folderRepository.findByIsDeleted(PersistenceFlags.NOT_DELETED)) {
            folders.put(f.getId(), f);
        }
        if (user == null || !user.isStaff()) {
            return new Context(user, false, false, false, false, false, false, Set.of(), Set.of(),
                    folders, Set.of(), Set.of());
        }
        boolean manage = user.hasPermission(Permissions.DOC_LIBRARY_MANAGE);
        Set<Long> roleIds = user.getRoles() == null || user.getRoles().isEmpty()
                ? Set.of() : new HashSet<>(shareRepository.findRoleIdsByCodes(user.getRoles()));
        Set<Long> taught = taughtClassIds(user.getId());
        Set<Long> sharedFolders = Set.of();
        Set<Long> sharedItems = Set.of();
        if (!manage) {
            LocalDateTime now = LocalDateTime.now();
            sharedFolders = new HashSet<>(shareRepository.findSharedFolderIds(orNone(roleIds), orNone(taught), now));
            sharedItems = new HashSet<>(shareRepository.findSharedItemIds(orNone(roleIds), orNone(taught), now));
        }
        return new Context(user,
                user.hasPermission(Permissions.DOC_LIBRARY_VIEW),
                manage,
                user.hasPermission(Permissions.DOC_LIBRARY_UPLOAD),
                user.hasPermission(Permissions.DOC_LIBRARY_UPDATE),
                user.hasPermission(Permissions.DOC_LIBRARY_DELETE),
                user.hasPermission(Permissions.DOC_LIBRARY_SHARE),
                roleIds, taught, folders, sharedFolders, sharedItems);
    }

    /** Lớp người dùng là GVCN hoặc dạy ít nhất một buổi chưa huỷ (luôn tính thật). */
    public Set<Long> taughtClassIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>(classRepository.findIdsByTeacherIdAndIsDeleted(
                userId, PersistenceFlags.NOT_DELETED));
        ids.addAll(classSessionRepository.findDistinctClassIdsByTeacherIdAndIsDeletedAndStatusNot(
                userId, PersistenceFlags.NOT_DELETED, SESSION_STATUS_CANCELLED));
        return ids;
    }

    public static Collection<Long> orNone(Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? NONE : ids;
    }

    /** ID tổ tiên (gồm chính nó) theo {@code TREE_PATH} dạng {@code /1/5/}. */
    public static List<Long> pathIds(String treePath) {
        List<Long> ids = new ArrayList<>();
        if (treePath == null) {
            return ids;
        }
        for (String part : treePath.split("/")) {
            if (!part.isBlank()) {
                try {
                    ids.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException ignored) {
                    // bỏ qua phần hỏng
                }
            }
        }
        return ids;
    }

    @Getter
    public static final class Context {
        private final AuthUserPrincipal user;
        private final boolean view;
        private final boolean manage;
        private final boolean upload;
        private final boolean update;
        private final boolean delete;
        private final boolean share;
        private final Set<Long> roleIds;
        private final Set<Long> taughtClassIds;
        private final Map<Long, DocFolderEntity> folders;
        private final Set<Long> sharedFolderIds;
        private final Set<Long> sharedItemIds;
        private final Map<Long, Boolean> folderVisibleCache = new HashMap<>();

        public Context(AuthUserPrincipal user, boolean view, boolean manage, boolean upload, boolean update,
                       boolean delete, boolean share, Set<Long> roleIds, Set<Long> taughtClassIds,
                       Map<Long, DocFolderEntity> folders, Set<Long> sharedFolderIds, Set<Long> sharedItemIds) {
            this.user = user;
            this.view = view;
            this.manage = manage;
            this.upload = upload;
            this.update = update;
            this.delete = delete;
            this.share = share;
            this.roleIds = roleIds;
            this.taughtClassIds = taughtClassIds;
            this.folders = folders;
            this.sharedFolderIds = sharedFolderIds;
            this.sharedItemIds = sharedItemIds;
        }

        public Long userId() {
            return user == null ? null : user.getId();
        }

        public boolean isStaff() {
            return user != null && user.isStaff();
        }

        public DocFolderEntity folder(Long id) {
            return id == null ? null : folders.get(id);
        }

        /** Chuỗi tổ tiên (gốc trước) gồm chính nó, chỉ thư mục chưa xoá. */
        public List<DocFolderEntity> chain(DocFolderEntity f) {
            List<DocFolderEntity> out = new ArrayList<>();
            if (f == null) {
                return out;
            }
            for (Long id : pathIds(f.getTreePath())) {
                DocFolderEntity a = folders.get(id);
                if (a != null) {
                    out.add(a);
                }
            }
            return out;
        }

        /** Thấy thư mục (không tính dạng "chỉ điều hướng"). */
        public boolean folderVisible(Long folderId) {
            if (!isStaff() || folderId == null) {
                return false;
            }
            DocFolderEntity f = folders.get(folderId);
            if (f == null) {
                return false;
            }
            if (manage) {
                return true;
            }
            return folderVisibleCache.computeIfAbsent(folderId, id -> {
                if (Objects.equals(f.getOwnerUserId(), userId())) {
                    return true;
                }
                for (DocFolderEntity a : chain(f)) {
                    if (sharedFolderIds.contains(a.getId())) {
                        return true;
                    }
                }
                return false;
            });
        }

        public boolean itemVisible(DocItemEntity item) {
            if (!isStaff() || item == null || !Objects.equals(item.getIsDeleted(), 0)) {
                return false;
            }
            if (manage || Objects.equals(item.getOwnerUserId(), userId()) || sharedItemIds.contains(item.getId())) {
                return true;
            }
            return item.getFolderId() != null && folderVisible(item.getFolderId());
        }

        public Set<Long> visibleFolderIds() {
            Set<Long> out = new LinkedHashSet<>();
            for (Long id : folders.keySet()) {
                if (folderVisible(id)) {
                    out.add(id);
                }
            }
            return out;
        }

        /** Thư mục con cháu (gồm chính nó). */
        public Set<Long> subtreeIds(Long folderId) {
            DocFolderEntity root = folders.get(folderId);
            Set<Long> out = new LinkedHashSet<>();
            if (root == null) {
                return out;
            }
            String prefix = root.getTreePath();
            for (DocFolderEntity f : folders.values()) {
                if (f.getTreePath() != null && f.getTreePath().startsWith(prefix)) {
                    out.add(f.getId());
                }
            }
            return out;
        }

        public boolean isOwner(DocItemEntity item) {
            return item != null && Objects.equals(item.getOwnerUserId(), userId());
        }

        /** Tạo tài liệu trong thư mục (null = gốc kho, chỉ MANAGE). */
        public boolean canCreateIn(Long folderId) {
            if (manage) {
                return folderId == null || folders.containsKey(folderId);
            }
            return upload && folderId != null && folderVisible(folderId);
        }

        public boolean canEdit(DocItemEntity item) {
            return manage || (update && itemVisible(item)) || (upload && isOwner(item));
        }

        public boolean canDelete(DocItemEntity item) {
            return manage || (delete && itemVisible(item)) || (upload && isOwner(item));
        }

        public boolean canShare(DocItemEntity item) {
            return manage || (share && itemVisible(item)) || (upload && isOwner(item));
        }

        /** Chia sẻ cho mọi đích (không chỉ lớp mình dạy). */
        public boolean canShareAnyTarget() {
            return manage || share;
        }

        public boolean canShareFolder(Long folderId) {
            return (manage || share) && folderVisible(folderId);
        }
    }
}