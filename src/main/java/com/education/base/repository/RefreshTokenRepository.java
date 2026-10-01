package com.education.base.repository;

import com.education.base.entity.RefreshTokenEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository JPA của {@code SYS_REFRESH_TOKENS}.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    /**
     * Đọc và khóa dòng ({@code SELECT ... FOR UPDATE}) để hai request làm mới đồng thời bằng cùng một
     * refresh token được xử lý tuần tự.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshTokenEntity t where t.jti = :jti")
    Optional<RefreshTokenEntity> findByJtiForUpdate(@Param("jti") String jti);

    /** Phiên còn ít nhất một refresh token chưa bị thu hồi. */
    boolean existsByFamilyIdAndRevokedAtIsNull(String familyId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshTokenEntity t
               set t.revokedAt = :now
             where t.familyId = :familyId
               and t.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshTokenEntity t
               set t.revokedAt = :now
             where t.familyId = :familyId
               and t.userId = :userId
               and t.revokedAt is null
            """)
    int revokeFamilyOfUser(@Param("familyId") String familyId, @Param("userId") Long userId,
                           @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshTokenEntity t
               set t.revokedAt = :now
             where t.userId = :userId
               and t.revokedAt is null
            """)
    int revokeAllOfUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** Dọn các refresh token đã hết hạn trước {@code cutoff} của một người dùng. */
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshTokenEntity t where t.userId = :userId and t.expiresAt < :cutoff")
    int deleteExpiredOfUser(@Param("userId") Long userId, @Param("cutoff") LocalDateTime cutoff);
}
