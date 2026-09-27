package com.education.base.repository;

import com.education.base.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository JPA của {@code SYS_USERS}.
 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    Optional<UserEntity> findByUsernameAndIsDeleted(String username, Integer isDeleted);

    boolean existsByUsername(String username);
}
