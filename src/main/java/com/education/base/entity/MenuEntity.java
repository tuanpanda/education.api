package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Entity ánh xạ bảng {@code SYS_MENUS} - menu đa cấp của sidebar.
 * <p>
 * Quan hệ cha - con được biểu diễn bằng cột {@code PARENT_ID} (menu gốc có {@code PARENT_ID} null).
 */
@Entity
@Table(name = "SYS_MENUS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuEntity {

    @Id
    @SequenceGenerator(name = "seq_menu", sequenceName = "SEQ_SYS_MENUS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_menu")
    @Column(name = "ID")
    private Long id;

    @Column(name = "PARENT_ID")
    private Long parentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARENT_ID", insertable = false, updatable = false)
    private MenuEntity parent;

    @Column(name = "MENU_CODE", nullable = false, unique = true, length = 50)
    private String menuCode;

    @Column(name = "MENU_NAME", nullable = false, length = 100)
    private String menuName;

    /**
     * Loại menu: {@code DIR} (thư mục chứa menu con) hoặc {@code MENU} (trang chức năng).
     */
    @Column(name = "MENU_TYPE", nullable = false, length = 20)
    private String menuType;

    @Column(name = "PATH", length = 255)
    private String path;

    @Column(name = "ICON", length = 50)
    private String icon;

    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder;

    @Column(name = "IS_HIDDEN", nullable = false)
    private Integer isHidden;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

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

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @Builder.Default
    private List<MenuEntity> children = new ArrayList<>();

    @OneToMany(mappedBy = "menu", fetch = FetchType.LAZY)
    @Builder.Default
    private List<FunctionEntity> functions = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (isDeleted == null) {
            isDeleted = 0;
        }
        if (isHidden == null) {
            isHidden = 0;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
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
