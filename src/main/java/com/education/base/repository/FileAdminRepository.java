package com.education.base.repository;

import com.education.base.entity.FileEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Truy vấn {@code SYS_ATTACHED_FILES} dành cho màn quản trị / job (Luồng B): Specification + thống kê.
 * Tách khỏi {@link FileRepository} (Luồng A) để hai luồng không sửa chung một file.
 */
@Repository
public interface FileAdminRepository extends JpaRepository<FileEntity, Long>, JpaSpecificationExecutor<FileEntity> {

    /** [status, count, sum(size)]. */
    @Query("select f.status, count(f), coalesce(sum(f.fileSize), 0) from FileEntity f group by f.status")
    List<Object[]> sumByStatus();

    /** [module, count, sum(size)] của các trạng thái cho trước. */
    @Query("select f.moduleName, count(f), coalesce(sum(f.fileSize), 0) from FileEntity f "
            + "where f.status in :statuses group by f.moduleName order by f.moduleName")
    List<Object[]> sumByModule(@Param("statuses") Collection<String> statuses);

    /** [year, month, count, sum(size)] kể từ {@code from}. */
    @Query("select year(f.createdAt), month(f.createdAt), count(f), coalesce(sum(f.fileSize), 0) from FileEntity f "
            + "where f.createdAt >= :from and f.status in :statuses "
            + "group by year(f.createdAt), month(f.createdAt)")
    List<Object[]> sumByMonth(@Param("from") LocalDateTime from, @Param("statuses") Collection<String> statuses);

    /** [userId, count, sum(size)] giảm dần theo dung lượng. */
    @Query("select f.uploadedByUserId, count(f), coalesce(sum(f.fileSize), 0) from FileEntity f "
            + "where f.uploadedByUserId is not null and f.status in :statuses "
            + "group by f.uploadedByUserId order by coalesce(sum(f.fileSize), 0) desc")
    List<Object[]> topUploaders(@Param("statuses") Collection<String> statuses, Pageable pageable);

    /** [studentId, count, sum(size)] giảm dần theo dung lượng. */
    @Query("select f.ownerStudentId, count(f), coalesce(sum(f.fileSize), 0) from FileEntity f "
            + "where f.ownerStudentId is not null and f.status in :statuses "
            + "group by f.ownerStudentId order by coalesce(sum(f.fileSize), 0) desc")
    List<Object[]> topStudents(@Param("statuses") Collection<String> statuses, Pageable pageable);

    @Query("select coalesce(sum(f.fileSize), 0) from FileEntity f where f.status in :statuses")
    long sumSize(@Param("statuses") Collection<String> statuses);

    @Query("select coalesce(sum(f.fileSize), 0) from FileEntity f "
            + "where f.ownerStudentId = :studentId and f.status in :statuses")
    long sumSizeByOwnerStudent(@Param("studentId") Long studentId, @Param("statuses") Collection<String> statuses);

    @Query("select coalesce(sum(f.fileSize), 0) from FileEntity f "
            + "where f.uploadedByUserId = :userId and f.status in :statuses")
    long sumSizeByUploader(@Param("userId") Long userId, @Param("statuses") Collection<String> statuses);

    long countByStatusAndChecksumSha256IsNull(String status);

    /** Cặp (module, referenceId) khác nhau của file theo trạng thái - để tính "nơi sử dụng". */
    @Query("select distinct f.moduleName, f.referenceId from FileEntity f "
            + "where f.referenceId is not null and f.status in :statuses")
    List<Object[]> distinctReferences(@Param("statuses") Collection<String> statuses);

    @Query("select f.id from FileEntity f where f.status = :status and f.purgeAfter is not null "
            + "and f.purgeAfter < :now and f.id > :afterId order by f.id")
    List<Long> findIdsDueForPurge(@Param("status") String status, @Param("now") LocalDateTime now,
                                  @Param("afterId") Long afterId, Pageable pageable);

    @Query("select f.id from FileEntity f where f.status = :status "
            + "and coalesce(f.purgeAfter, f.createdAt) < :cutoff and f.id > :afterId order by f.id")
    List<Long> findPendingIdsOlderThan(@Param("status") String status, @Param("cutoff") LocalDateTime cutoff,
                                       @Param("afterId") Long afterId, Pageable pageable);

    List<FileEntity> findByStatusInAndIdGreaterThanOrderByIdAsc(Collection<String> statuses, Long afterId,
                                                                 Pageable pageable);

    List<FileEntity> findByStatusInAndChecksumSha256IsNullAndIdGreaterThanOrderByIdAsc(
            Collection<String> statuses, Long afterId, Pageable pageable);

    /** Có dòng metadata nào (mọi trạng thái trừ PURGED) đang trỏ tới storage key này không. */
    @Query("select count(f) from FileEntity f where (f.filePath = :key or f.filePath = :altKey) "
            + "and f.status <> 'PURGED'")
    long countLiveByFilePath(@Param("key") String key, @Param("altKey") String altKey);

    /** Ghi checksum cho file cũ (chỉ khi chưa có) - không đụng cột khác. */
    @Modifying
    @Transactional
    @Query("update FileEntity f set f.checksumSha256 = :checksum where f.id = :id and f.checksumSha256 is null")
    int fillChecksum(@Param("id") Long id, @Param("checksum") String checksum);
}
