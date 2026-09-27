package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng {@code SYS_CODE_RULES} - quy luật sinh mã nghiệp vụ.
 * <p>
 * Pattern hỗ trợ token {@code {PREFIX}}, {@code {GRADE}}, {@code {YYYY}}, {@code {YY}}, {@code {MM}},
 * {@code {DD}}, {@code {SEQ}}. Function Oracle {@code FN_NEXT_BIZ_CODE} đọc hàng này
 * (FOR UPDATE) để cấp mã kế tiếp.
 */
@Entity
@Table(name = "SYS_CODE_RULES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeRuleEntity {

    @Id
    @SequenceGenerator(name = "seq_code_rule", sequenceName = "SEQ_SYS_CODE_RULES", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_code_rule")
    @Column(name = "ID")
    private Long id;

    @Column(name = "RULE_CODE", nullable = false, unique = true, length = 30)
    private String ruleCode;

    @Column(name = "MODULE_NAME", nullable = false, length = 30)
    private String moduleName;

    @Column(name = "TABLE_NAME", nullable = false, length = 30)
    private String tableName;

    @Column(name = "PREFIX", length = 10)
    private String prefix;

    @Column(name = "PATTERN", nullable = false, length = 80)
    private String pattern;

    @Column(name = "SEQ_LENGTH", nullable = false)
    private Integer seqLength;

    /** {@code NEVER}, {@code YEAR}, {@code MONTH} hoặc {@code DAY}. */
    @Column(name = "RESET_CYCLE", nullable = false, length = 10)
    private String resetCycle;

    @Column(name = "LAST_RESET_KEY", length = 8)
    private String lastResetKey;

    @Column(name = "LAST_SEQ", nullable = false)
    private Long lastSeq;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Integer isActive;

    @Column(name = "IS_DELETED", nullable = false)
    private Integer isDeleted;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "CREATED_BY", length = 50)
    private String createdBy;

    @Column(name = "UPDATED_BY", length = 50)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (isDeleted == null) {
            isDeleted = 0;
        }
        if (isActive == null) {
            isActive = 1;
        }
        if (seqLength == null) {
            seqLength = 4;
        }
        if (lastSeq == null) {
            lastSeq = 0L;
        }
        if (resetCycle == null || resetCycle.isBlank()) {
            resetCycle = "YEAR";
        }
        if (pattern == null || pattern.isBlank()) {
            pattern = "{PREFIX}{YYYY}{SEQ}";
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
