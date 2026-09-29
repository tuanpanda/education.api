package com.education.base.repository;

import com.education.base.entity.FunctionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository JPA của {@code SYS_FUNCTIONS}.
 */
public interface FunctionRepository extends JpaRepository<FunctionEntity, Long> {

    List<FunctionEntity> findByMenuIdAndIsDeleted(Long menuId, Integer isDeleted);

    /** Toàn bộ chức năng (kể cả đã xóa mềm) của một menu - dùng khi đồng bộ để khôi phục bản ghi cũ. */
    List<FunctionEntity> findByMenuId(Long menuId);

    List<FunctionEntity> findByIsDeletedOrderByIdAsc(Integer isDeleted);
}
