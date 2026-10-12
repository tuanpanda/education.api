package com.education.base.service;

import com.education.base.entity.DocFolderEntity;
import com.education.base.entity.DocItemEntity;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dữ liệu dựng sẵn cho test kho tài liệu. */
public final class DocTestData {

    private DocTestData() {
    }

    public static AuthUserPrincipal staff(long id, String role, String... perms) {
        AuthUserPrincipal.AuthUserPrincipalBuilder b = AuthUserPrincipal.builder()
                .id(id).username("u" + id).role(role);
        for (String p : perms) {
            b.permission(p);
        }
        return b.build();
    }

    public static AuthUserPrincipal admin() {
        return staff(1L, Permissions.ADMIN_ROLE);
    }

    public static DocFolderEntity folder(long id, Long parentId, String path, Long owner) {
        DocFolderEntity f = DocFolderEntity.builder().id(id).parentId(parentId).name("F" + id).treePath(path)
                .depth((int) path.chars().filter(c -> c == '/').count() - 1).ownerUserId(owner)
                .sortOrder(0).isDeleted(0).build();
        f.setNewEntity(false);
        return f;
    }

    public static DocItemEntity item(long id, Long folderId, long owner) {
        DocItemEntity i = DocItemEntity.builder().id(id).folderId(folderId).fileId(100 + id).title("T" + id)
                .ownerUserId(owner).versionNo(1).isDeleted(0).build();
        i.setNewEntity(false);
        return i;
    }

    public static Map<Long, DocFolderEntity> folders(DocFolderEntity... fs) {
        Map<Long, DocFolderEntity> m = new HashMap<>();
        for (DocFolderEntity f : fs) {
            m.put(f.getId(), f);
        }
        return m;
    }

    /** Context: quyền theo cờ, chia sẻ trực tiếp cho người dùng. */
    public static DocumentAccessService.Context ctx(AuthUserPrincipal u, boolean manage, boolean upload,
                                                    boolean update, boolean delete, boolean share,
                                                    Set<Long> taught, Map<Long, DocFolderEntity> folders,
                                                    Set<Long> sharedFolders, Set<Long> sharedItems) {
        return new DocumentAccessService.Context(u, true, manage, upload, update, delete, share, Set.of(), taught,
                folders, sharedFolders, sharedItems);
    }

    public static List<Long> ids(Long... ids) {
        return List.of(ids);
    }
}