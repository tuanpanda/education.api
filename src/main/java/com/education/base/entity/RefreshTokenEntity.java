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
 * Entity ánh xạ bảng {@code SYS_REFRESH_TOKENS} (V13_2) - refresh token đã phát hành, phục vụ xoay vòng
 * (rotation) và phát hiện dùng lại.
 * <p>
 * Mỗi lần đăng nhập tạo một "family" ({@code FAMILY_ID}, cũng là claim {@code sid} của JWT). Mỗi lần làm mới,
 * token cũ bị thu hồi ({@code REVOKED_AT}, {@code REPLACED_BY}) và token mới cùng family được ghi thêm.
 */
@Entity
@Table(name = "SYS_REFRESH_TOKENS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenEntity {

    @Id
    @SequenceGenerator(name = "seq_refresh_token", sequenceName = "SEQ_SYS_REFRESH_TOKENS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_refresh_token")
    @Column(name = "ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** Claim {@code jti} của refresh token (duy nhất). */
    @Column(name = "JTI", nullable = false, unique = true, length = 64)
    private String jti;

    /** Phiên đăng nhập; mọi token sinh ra do xoay vòng từ cùng một lần đăng nhập có chung giá trị. */
    @Column(name = "FAMILY_ID", nullable = false, length = 64)
    private String familyId;

    @Column(name = "EXPIRES_AT", nullable = false)
    private LocalDateTime expiresAt;

    /** Thời điểm thu hồi (xoay vòng, đăng xuất, phát hiện dùng lại); {@code null} = còn hiệu lực. */
    @Column(name = "REVOKED_AT")
    private LocalDateTime revokedAt;

    /** {@code JTI} của token thay thế khi xoay vòng. */
    @Column(name = "REPLACED_BY", length = 64)
    private String replacedBy;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
