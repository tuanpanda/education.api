package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Một mục phát hiện trong đợt đối soát - bảng {@code SYS_FILE_SCAN_ITEMS} (V19_2). Luồng B.
 */
@Entity
@Table(name = "SYS_FILE_SCAN_ITEMS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileScanItemEntity {

    @Id
    @SequenceGenerator(name = "seq_file_scan_item", sequenceName = "SEQ_SYS_FILE_SCAN_ITEMS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_file_scan_item")
    @Column(name = "ID")
    private Long id;

    @Column(name = "RUN_ID", nullable = false)
    private Long runId;

    @Column(name = "ITEM_TYPE", nullable = false, length = 30)
    private String itemType;

    @Column(name = "FILE_ID")
    private Long fileId;

    @Column(name = "STORAGE_KEY", length = 500)
    private String storageKey;

    @Column(name = "DB_SIZE")
    private Long dbSize;

    @Column(name = "BLOB_SIZE")
    private Long blobSize;

    @Column(name = "BLOB_MTIME")
    private LocalDateTime blobMtime;

    /** NULL = chưa xử lý. */
    @Column(name = "RESOLUTION", length = 30)
    private String resolution;

    @Column(name = "RESOLVED_AT")
    private LocalDateTime resolvedAt;

    @Column(name = "RESOLVED_BY", length = 50)
    private String resolvedBy;

    @Column(name = "NOTE", length = 500)
    private String note;
}
