package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * Entity ánh xạ bảng {@code SYS_ROLE_MENU_PERMISSIONS} - quyền của một vai trò trên một menu.
 * <p>
 * Cột {@code ALLOWED_FUNCTIONS} lưu danh sách mã chức năng, phân tách bằng dấu phẩy
 * (ví dụ: {@code VIEW,CREATE,UPDATE,DELETE,EXPORT}).
 */
@Entity
@Table(name = "SYS_ROLE_MENU_PERMISSIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleMenuPermissionEntity {

    @Id
    @SequenceGenerator(name = "seq_role_menu_perm", sequenceName = "SEQ_SYS_ROLE_MENU_PERM", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_role_menu_perm")
    @Column(name = "ID")
    private Long id;

    @Column(name = "ROLE_ID", nullable = false)
    private Long roleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ROLE_ID", insertable = false, updatable = false)
    private RoleEntity role;

    @Column(name = "MENU_ID", nullable = false)
    private Long menuId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MENU_ID", insertable = false, updatable = false)
    private MenuEntity menu;

    @Column(name = "ALLOWED_FUNCTIONS", length = 500)
    private String allowedFunctions;

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
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
