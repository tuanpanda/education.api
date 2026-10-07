package com.education.base.repository;

import com.education.base.entity.FileEntity;
import com.education.base.repository.custom.FileRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

/**
 * Repository của {@code SYS_ATTACHED_FILES}: CRUD chuẩn hóa qua Spring Data JPA,
 * kết hợp truy vấn danh sách file qua Standalone Procedure ({@link FileRepositoryCustom}).
 * Các truy vấn đếm / tổng dung lượng phục vụ giới hạn & quota (Luồng A, {@code FileLimitService}).
 */
@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long>, FileRepositoryCustom {

    long countByModuleNameAndReferenceIdAndStatusIn(String moduleName, Long referenceId, Collection<String> statuses);

    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM FileEntity f "
            + "WHERE f.moduleName = :module AND f.referenceId = :ref AND f.status IN :statuses")
    long sumSizeByRef(@Param("module") String module, @Param("ref") Long referenceId,
                      @Param("statuses") Collection<String> statuses);

    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM FileEntity f "
            + "WHERE f.ownerStudentId = :studentId AND f.status IN :statuses")
    long sumSizeByOwnerStudent(@Param("studentId") Long studentId, @Param("statuses") Collection<String> statuses);

    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM FileEntity f WHERE f.status IN :statuses")
    long sumSizeByStatusIn(@Param("statuses") Collection<String> statuses);
}
