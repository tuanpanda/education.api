package com.education.base.repository;

import com.education.base.entity.DocShareEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface DocShareRepository extends JpaRepository<DocShareEntity, Long> {

    List<DocShareEntity> findByFolderIdOrderByIdAsc(Long folderId);

    List<DocShareEntity> findByItemIdOrderByIdAsc(Long itemId);

    List<DocShareEntity> findByFolderIdIn(Collection<Long> folderIds);

    List<DocShareEntity> findByItemIdIn(Collection<Long> itemIds);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM DocShareEntity s WHERE s.folderId = :folderId")
    int deleteByFolderId(@Param("folderId") Long folderId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM DocShareEntity s WHERE s.itemId = :itemId")
    int deleteByItemId(@Param("itemId") Long itemId);

    /** Chia sẻ thư mục còn hạn áp dụng cho người dùng (ALL_STAFF / vai trò / lớp dạy). */
    @Query("""
            SELECT DISTINCT s.folderId FROM DocShareEntity s
             WHERE s.folderId IS NOT NULL
               AND (s.expiresAt IS NULL OR s.expiresAt > :now)
               AND (s.targetType = 'ALL_STAFF'
                    OR (s.targetType = 'ROLE' AND s.roleId IN :roleIds)
                    OR (s.targetType = 'CLASS' AND s.classId IN :classIds))
            """)
    List<Long> findSharedFolderIds(@Param("roleIds") Collection<Long> roleIds,
                                   @Param("classIds") Collection<Long> classIds,
                                   @Param("now") LocalDateTime now);

    /** Tài liệu có chia sẻ trực tiếp còn hạn áp dụng cho người dùng. */
    @Query("""
            SELECT DISTINCT s.itemId FROM DocShareEntity s
             WHERE s.itemId IS NOT NULL
               AND (s.expiresAt IS NULL OR s.expiresAt > :now)
               AND (s.targetType = 'ALL_STAFF'
                    OR (s.targetType = 'ROLE' AND s.roleId IN :roleIds)
                    OR (s.targetType = 'CLASS' AND s.classId IN :classIds))
            """)
    List<Long> findSharedItemIds(@Param("roleIds") Collection<Long> roleIds,
                                 @Param("classIds") Collection<Long> classIds,
                                 @Param("now") LocalDateTime now);

    /** Chia sẻ CLASS còn hạn cho một lớp (lọc {@code classId}). */
    @Query("""
            SELECT s FROM DocShareEntity s
             WHERE s.targetType = 'CLASS' AND s.classId = :classId
               AND (s.expiresAt IS NULL OR s.expiresAt > :now)
            """)
    List<DocShareEntity> findActiveClassShares(@Param("classId") Long classId, @Param("now") LocalDateTime now);

    @Query("SELECT r.id FROM RoleEntity r WHERE r.isDeleted = 0 AND r.roleCode IN :codes")
    List<Long> findRoleIdsByCodes(@Param("codes") Collection<String> codes);
}