package com.education.base.repository;

import com.education.base.entity.StudentEntity;
import com.education.base.repository.custom.StudentRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository của Module Quản lý Học sinh theo mô hình Hybrid:
 * <ul>
 *     <li>{@link JpaRepository} cho CRUD chuẩn hóa và tra cứu theo khóa chính.</li>
 *     <li>{@link StudentRepositoryCustom} cho tìm kiếm động/phân trang qua Oracle Procedure.</li>
 * </ul>
 * Mọi truy vấn đọc đều giới hạn {@code IS_DELETED = 0} để bản ghi đã xóa mềm không lọt ra ngoài.
 */
public interface StudentRepository extends JpaRepository<StudentEntity, Long>, StudentRepositoryCustom {

    /**
     * Tìm học sinh còn hiệu lực theo ID.
     */
    Optional<StudentEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    /**
     * Kiểm tra mã học sinh đã tồn tại chưa (tính cả bản ghi đã xóa mềm, vì ràng buộc
     * {@code UQ_EDU_STUDENTS_CODE} là unique trên toàn bảng).
     */
    boolean existsByStudentCode(String studentCode);

    /**
     * Kiểm tra mã học sinh còn hiệu lực đã tồn tại chưa.
     */
    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
              FROM StudentEntity s
             WHERE s.studentCode = :studentCode
               AND s.isDeleted = 0
            """)
    boolean existsByStudentCodeAndIsDeletedFalse(@Param("studentCode") String studentCode);

    /**
     * Lấy các mã học sinh đã tồn tại (kể cả xóa mềm) để kiểm tra trùng khi import hàng loạt.
     */
    @Query("""
            SELECT UPPER(s.studentCode)
              FROM StudentEntity s
             WHERE UPPER(s.studentCode) IN :codes
            """)
    List<String> findExistingCodesUpper(@Param("codes") Collection<String> codes);

    /**
     * Tìm học sinh còn hiệu lực theo mã.
     */
    Optional<StudentEntity> findByStudentCodeAndIsDeleted(String studentCode, Integer isDeleted);

    /**
     * Đếm số học sinh còn hiệu lực theo trạng thái.
     */
    long countByStatusAndIsDeleted(String status, Integer isDeleted);
}
