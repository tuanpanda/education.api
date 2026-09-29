package com.education.base.repository;

import com.education.base.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
