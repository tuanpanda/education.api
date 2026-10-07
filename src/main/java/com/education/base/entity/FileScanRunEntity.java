package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Một đợt đối soát / tính checksum - bảng {@code SYS_FILE_SCAN_RUNS} (V19_2). Luồng B.
 * Tối đa một dòng {@code RUNNING} (unique index {@code UQ_FILE_SCAN_RUNS_RUNNING}).
 */
@Entity
@Table(name = "SYS_FILE_SCAN_RUNS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileScanRunEntity {

    @Id
    @SequenceGenerator(name = "seq_file_scan_run", sequenceName = "SEQ_SYS_FILE_SCAN_RUNS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_file_scan_run")
    @Column(name = "ID")
    private Long id;

    /** ORPHAN / CHECKSUM. */
    @Column(name = "RUN_TYPE", nullable = false, length = 20)
    private String runType;

    /** RUNNING / DONE / FAILED. */
    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "STARTED_AT", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "FINISHED_AT")
    private LocalDateTime finishedAt;

    @Column(name = "STARTED_BY", length = 50)
    private String startedBy;

    @Column(name = "DB_ROWS_CHECKED", nullable = false)
    @Builder.Default
    private Long dbRowsChecked = 0L;

    @Column(name = "BLOBS_CHECKED", nullable = false)
    @Builder.Default
    private Long blobsChecked = 0L;

    @Column(name = "MISSING_BLOB_COUNT", nullable = false)
    @Builder.Default
    private Long missingBlobCount = 0L;

    @Column(name = "ORPHAN_BLOB_COUNT", nullable = false)
    @Builder.Default
    private Long orphanBlobCount = 0L;

    @Column(name = "MISMATCH_COUNT", nullable = false)
    @Builder.Default
    private Long mismatchCount = 0L;

    @Column(name = "DETAIL", length = 4000)
    private String detail;

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }
}
