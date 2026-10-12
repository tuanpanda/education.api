package com.education.base.repository;

import com.education.base.entity.DocItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocItemRepository extends JpaRepository<DocItemEntity, Long>, JpaSpecificationExecutor<DocItemEntity> {

    @Query(value = "SELECT SEQ_DOC_ITEMS.NEXTVAL FROM DUAL", nativeQuery = true)
    Long nextId();

    Optional<DocItemEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    Optional<DocItemEntity> findFirstByFileIdAndIsDeleted(Long fileId, Integer isDeleted);

    long countByFolderIdAndIsDeleted(Long folderId, Integer isDeleted);

    /** [folderId, count] cho mọi thư mục (folderId NULL = gốc). */
    @Query("SELECT i.folderId, COUNT(i) FROM DocItemEntity i WHERE i.isDeleted = 0 GROUP BY i.folderId")
    List<Object[]> countByFolder();

    /** [id, folderId] các tài liệu của chủ sở hữu. */
    @Query("SELECT i.id, i.folderId FROM DocItemEntity i WHERE i.isDeleted = 0 AND i.ownerUserId = :userId")
    List<Object[]> findIdAndFolderByOwner(@Param("userId") Long userId);

    /** [id, folderId] các tài liệu trong danh sách id. */
    @Query("SELECT i.id, i.folderId FROM DocItemEntity i WHERE i.isDeleted = 0 AND i.id IN :ids")
    List<Object[]> findIdAndFolderByIdIn(@Param("ids") Collection<Long> ids);
}