package com.education.base.repository;

import com.education.base.entity.FileScanRunEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** {@code SYS_FILE_SCAN_RUNS} - Luồng B. */
@Repository
public interface FileScanRunRepository extends JpaRepository<FileScanRunEntity, Long> {

    List<FileScanRunEntity> findByStatus(String status);

    Page<FileScanRunEntity> findAllByOrderByStartedAtDescIdDesc(Pageable pageable);
}
