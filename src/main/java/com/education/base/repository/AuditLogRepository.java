package com.education.base.repository;

import com.education.base.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * {@code SYS_AUDIT_LOGS}: chỉ thêm ({@code save}) và tra cứu; không có thao tác sửa / xóa nghiệp vụ.
 */
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long>, JpaSpecificationExecutor<AuditLogEntity> {
}
