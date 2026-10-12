package com.education.base.repository;

import com.education.base.entity.FileQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@code SYS_FILE_QUOTAS} - Luồng B. */
@Repository
public interface FileQuotaRepository extends JpaRepository<FileQuotaEntity, Long> {
}
