package com.education.base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity ánh xạ bảng {@code SYS_USERS} - người dùng hệ thống.
 * <p>
 * {@link DynamicUpdate}: câu UPDATE chỉ gồm các cột thật sự thay đổi, để việc lưu entity không ghi đè
 * các cột được tăng nguyên tử bằng câu lệnh riêng ({@code TOKEN_VERSION}, {@code FAILED_LOGIN_COUNT}).
 */
@Entity
@Table(name = "SYS_USERS")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @SequenceGenerator(name = "seq_user", sequenceName = "SEQ_SYS_USERS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_user")
    @Column(name = "ID")
    private Long id;

    @Column(name = "USERNAME", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "FULL_NAME", nullable = false, length = 100)
    private String fullName;

    @Column(name = "EMAIL", length = 100)
    private String email;

    @Column(name = "PHONE", length = 20)
    private String phone;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    /** 1 = bắt buộc đổi mật khẩu ở lần đăng nhập kế tiếp (V12). */
    @Column(name = "MUST_CHANGE_PASSWORD", nullable = false)
    private Integer mustChangePassword;

    /**
     * Phiên bản token (V12). Tăng lên khi đổi/đặt lại mật khẩu hoặc khóa tài khoản
     * để vô hiệu hóa mọi JWT đã phát hành. Đăng xuất chỉ thu hồi phiên hiện tại ({@code SYS_REFRESH_TOKENS}).
     */
    @Column(name = "TOKEN_VERSION", nullable = false)
    private Integer tokenVersion;

    /** Số lần đăng nhập sai liên tiếp (V13_2); về 0 khi đăng nhập thành công. */
    @Column(name = "FAILED_LOGIN_COUNT", nullable = false)
    private Integer failedLoginCount;

    /** Khóa tạm thời do đăng nhập sai nhiều lần (V13_2); {@code null} hoặc đã qua = không khóa. */
    @Column(name = "LOCKED_UNTIL")
    private LocalDateTime lockedUntil;

    @Column(name = "LAST_LOGIN_AT")
    private LocalDateTime lastLoginAt;

    @Column(name = "PASSWORD_CHANGED_AT")
    private LocalDateTime passwordChangedAt;

    /**
     * Loại tài khoản (V17_1): {@code STAFF} (mặc định) / {@code STUDENT} / {@code PARENT}
     * ({@code CK_USERS_USER_TYPE}). Tài khoản học sinh do màn hình "Tài khoản học sinh" tạo.
     */
    @Column(name = "USER_TYPE", nullable = false, length = 20)
    private String userType;

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

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Builder.Default
    private List<UserRoleEntity> userRoles = new ArrayList<>();

    @OneToMany(mappedBy = "teacher", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ClassEntity> teachingClasses = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (isDeleted == null) {
            isDeleted = 0;
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
        if (userType == null || userType.isBlank()) {
            userType = "STAFF";
        }
        if (mustChangePassword == null) {
            mustChangePassword = 0;
        }
        if (tokenVersion == null) {
            tokenVersion = 0;
        }
        if (failedLoginCount == null) {
            failedLoginCount = 0;
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
