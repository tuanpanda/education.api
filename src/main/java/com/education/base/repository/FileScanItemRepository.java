package com.education.base.repository;

import com.education.base.entity.FileScanItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/** {@code SYS_FILE_SCAN_ITEMS} - Luồng B. */
@Repository
public interface FileScanItemRepository extends JpaRepository<FileScanItemEntity, Long>,
        JpaSpecificationExecutor<FileScanItemEntity> {

    long countByRunIdAndResolutionIsNull(Long runId);
}
