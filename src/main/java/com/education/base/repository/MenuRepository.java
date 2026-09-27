package com.education.base.repository;

import com.education.base.entity.MenuEntity;
import com.education.base.repository.custom.MenuRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository của {@code SYS_MENUS}: CRUD chuẩn hóa qua Spring Data JPA,
 * kết hợp truy vấn menu/quyền theo người dùng qua Standalone Procedure ({@link MenuRepositoryCustom}).
 */
@Repository
public interface MenuRepository extends JpaRepository<MenuEntity, Long>, MenuRepositoryCustom {
}
