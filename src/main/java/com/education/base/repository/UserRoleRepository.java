package com.education.base.repository;

import com.education.base.entity.UserRoleEntity;
import com.education.base.entity.UserRoleId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Repository JPA của {@code SYS_USER_ROLES} (khóa kép {@link UserRoleId}).
 */
public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleId> {

    List<UserRoleEntity> findByUserId(Long userId);

    List<UserRoleEntity> findByRoleId(Long roleId);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    /**
     * Khóa ghi ({@code SELECT ... FOR UPDATE}) mọi dòng gán vai trò {@code roleCode}. Gọi trước khi kiểm tra
     * "quản trị viên cuối cùng" để các giao dịch song song phải xếp hàng.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ur from UserRoleEntity ur
             where ur.roleId in (select r.id from RoleEntity r where r.roleCode = :roleCode)
            """)
    List<UserRoleEntity> lockByRoleCode(@Param("roleCode") String roleCode);

    /** Vai trò (kèm entity {@code RoleEntity}) của nhiều người dùng trong một truy vấn. */
    @Query("select ur from UserRoleEntity ur join fetch ur.role r where ur.userId in :userIds")
    List<UserRoleEntity> findWithRoleByUserIdIn(@Param("userIds") Collection<Long> userIds);

    /**
     * Số tài khoản chưa xóa theo từng vai trò. Mỗi phần tử: {@code [roleId (Long), count (Long)]}.
     */
    @Query("""
            select ur.roleId, count(ur)
              from UserRoleEntity ur
              join ur.user u
             where u.isDeleted = 0
               and ur.roleId in :roleIds
             group by ur.roleId
            """)
    List<Object[]> countActiveUsersByRoleIds(@Param("roleIds") Collection<Long> roleIds);
}
