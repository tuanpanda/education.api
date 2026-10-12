package com.education.base.repository;

import com.education.base.entity.DocFolderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocFolderRepository extends JpaRepository<DocFolderEntity, Long> {

    @Query(value = "SELECT SEQ_DOC_FOLDERS.NEXTVAL FROM DUAL", nativeQuery = true)
    Long nextId();

    List<DocFolderEntity> findByIsDeleted(Integer isDeleted);

    Optional<DocFolderEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    @Query("""
            SELECT COUNT(f) FROM DocFolderEntity f
             WHERE f.isDeleted = 0
               AND COALESCE(f.parentId, 0) = :parentKey
               AND UPPER(f.name) = UPPER(:name)
               AND (:excludeId IS NULL OR f.id <> :excludeId)
            """)
    long countSiblingName(@Param("parentKey") Long parentKey,
                          @Param("name") String name,
                          @Param("excludeId") Long excludeId);

    long countByParentIdAndIsDeleted(Long parentId, Integer isDeleted);

    @Query("SELECT f FROM DocFolderEntity f WHERE f.isDeleted = 0 AND f.treePath LIKE CONCAT(:prefix, '%')")
    List<DocFolderEntity> findSubtree(@Param("prefix") String prefix);
}