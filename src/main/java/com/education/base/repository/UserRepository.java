package com.education.base.repository;

import com.education.base.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository JPA của {@code SYS_USERS}.
 */
public interface UserRepository extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {

    Optional<UserEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    Optional<UserEntity> findByUsernameAndIsDeleted(String username, Integer isDeleted);

    boolean existsByUsername(String username);

    /**
     * Đếm số tài khoản ĐANG HOẠT ĐỘNG (chưa xóa, {@code STATUS = ACTIVE}) có vai trò {@code roleCode},
     * không tính tài khoản {@code excludedUserId}. Dùng để chặn khóa/xóa/gỡ quyền quản trị viên cuối cùng.
     */
    @Query("""
            select count(distinct u.id)
              from UserEntity u
              join u.userRoles ur
              join ur.role r
             where u.isDeleted = 0
               and u.status = 'ACTIVE'
               and r.isDeleted = 0
               and r.roleCode = :roleCode
               and u.id <> :excludedUserId
            """)
    long countActiveUsersWithRoleExcluding(@Param("roleCode") String roleCode,
                                           @Param("excludedUserId") Long excludedUserId);

    /**
     * Tăng {@code TOKEN_VERSION} nguyên tử ở DB ({@code SET TOKEN_VERSION = TOKEN_VERSION + 1}) để vô hiệu hóa
     * mọi JWT đã cấp, không bị mất lượt tăng khi có cập nhật đồng thời. Đọc giá trị mới bằng
     * {@link #findTokenVersionById(Long)}.
     */
    @Modifying(flushAutomatically = true)
    @Query("update UserEntity u set u.tokenVersion = u.tokenVersion + 1 where u.id = :id")
    int incrementTokenVersion(@Param("id") Long id);

    @Query("select u.tokenVersion from UserEntity u where u.id = :id")
    Optional<Integer> findTokenVersionById(@Param("id") Long id);

    /** Tăng bộ đếm đăng nhập sai nguyên tử ở DB. Đọc lại bằng {@link #findFailedLoginCountById(Long)}. */
    @Modifying(flushAutomatically = true)
    @Query("update UserEntity u set u.failedLoginCount = coalesce(u.failedLoginCount, 0) + 1 where u.id = :id")
    int incrementFailedLoginCount(@Param("id") Long id);

    @Query("select u.failedLoginCount from UserEntity u where u.id = :id")
    Optional<Integer> findFailedLoginCountById(@Param("id") Long id);

    /** Khóa tạm thời tài khoản tới {@code lockedUntil} (không đổi {@code STATUS}). */
    @Modifying(flushAutomatically = true)
    @Query("update UserEntity u set u.lockedUntil = :lockedUntil where u.id = :id")
    int lockUntil(@Param("id") Long id, @Param("lockedUntil") LocalDateTime lockedUntil);

    /** Xóa bộ đếm đăng nhập sai và khóa tạm thời (ví dụ khi quản trị viên mở khóa / đặt lại mật khẩu). */
    @Modifying(flushAutomatically = true)
    @Query("update UserEntity u set u.failedLoginCount = 0, u.lockedUntil = null where u.id = :id")
    int clearLoginFailures(@Param("id") Long id);
}
