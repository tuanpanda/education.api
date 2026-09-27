package com.education.base.repository;

import com.education.base.entity.FileEntity;
import com.education.base.repository.custom.FileRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository của {@code SYS_ATTACHED_FILES}: CRUD chuẩn hóa qua Spring Data JPA,
 * kết hợp truy vấn danh sách file qua Standalone Procedure ({@link FileRepositoryCustom}).
 */
@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long>, FileRepositoryCustom {
}
