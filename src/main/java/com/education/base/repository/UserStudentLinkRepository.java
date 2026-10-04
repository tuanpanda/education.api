package com.education.base.repository;

import com.education.base.entity.UserStudentLinkEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository JPA của {@code EDU_USER_STUDENT_LINKS} (V17_1) và các truy vấn "Tài khoản học sinh".
 */
public interface UserStudentLinkRepository extends JpaRepository<UserStudentLinkEntity, Long> {

    /**
     * Liên kết {@code SELF} đang hoạt động (chưa xóa, {@code STATUS = ACTIVE}) của tài khoản {@code userId}
     * - xác định học sinh của tài khoản học sinh đang đăng nhập.
     */
    @Query("""
            select l from UserStudentLinkEntity l
             where l.userId = :userId
               and l.relation = 'SELF'
               and l.status = 'ACTIVE'
               and l.isDeleted = 0
             order by l.isPrimary desc, l.id asc
            """)
    List<UserStudentLinkEntity> findActiveSelfLinksByUserId(@Param("userId") Long userId);

    /** Liên kết {@code SELF} đang hoạt động của các học sinh {@code studentIds}. */
    @Query("""
            select l from UserStudentLinkEntity l
             where l.studentId in :studentIds
               and l.relation = 'SELF'
               and l.status = 'ACTIVE'
               and l.isDeleted = 0
            """)
    List<UserStudentLinkEntity> findActiveSelfLinksByStudentIds(@Param("studentIds") Collection<Long> studentIds);

    /** Liên kết {@code SELF} đang hoạt động của tài khoản {@code userId} với học sinh bất kỳ (nếu có). */
    default Optional<UserStudentLinkEntity> findActiveSelfLinkByUserId(Long userId) {
        return findActiveSelfLinksByUserId(userId).stream().findFirst();
    }

    /**
     * Số tiếp theo của {@code SEQ_STUDENT_USERNAME} (V17_1) để sinh tên đăng nhập {@code hs00001},
     * {@code hs00002}... (xem {@code StudentAccountServiceImpl}).
     */
    @Query(value = "SELECT SEQ_STUDENT_USERNAME.NEXTVAL FROM DUAL", nativeQuery = true)
    Long nextStudentUsernameNumber();

    /**
     * Tìm học sinh (chưa xóa mềm) kèm tài khoản {@code SELF} đang hoạt động (nếu có). Mỗi phần tử:
     * {@code [StudentEntity, UserEntity hoặc null]}.
     *
     * @param keyword    mẫu LIKE đã chuẩn hóa chữ thường ({@code %abc%}, ký tự đặc biệt escape bằng {@code \}),
     *                   khớp mã học sinh, họ tên hoặc tên đăng nhập; {@code null} = bỏ qua.
     * @param classId    chỉ học sinh đang ghi danh ({@code ENROLLED}) vào lớp; {@code null} = bỏ qua.
     * @param hasAccount 1 = đã có tài khoản, 0 = chưa có; {@code null} = bỏ qua.
     */
    @Query(value = """
            select s, u
              from StudentEntity s
              left join UserStudentLinkEntity l
                     on l.studentId = s.id and l.relation = 'SELF' and l.status = 'ACTIVE' and l.isDeleted = 0
              left join UserEntity u
                     on u.id = l.userId and u.isDeleted = 0
             where s.isDeleted = 0
               and (:keyword is null
                    or lower(s.studentCode) like :keyword escape '\\'
                    or lower(s.fullName) like :keyword escape '\\'
                    or lower(u.username) like :keyword escape '\\')
               and (:classId is null
                    or exists (select 1 from ClassStudentEntity cs
                                where cs.studentId = s.id
                                  and cs.classId = :classId
                                  and cs.isDeleted = 0
                                  and cs.status = 'ENROLLED'))
               and (:hasAccount is null
                    or (:hasAccount = 1 and u.id is not null)
                    or (:hasAccount = 0 and u.id is null))
             order by s.fullName asc, s.id asc
            """,
            countQuery = """
            select count(s)
              from StudentEntity s
              left join UserStudentLinkEntity l
                     on l.studentId = s.id and l.relation = 'SELF' and l.status = 'ACTIVE' and l.isDeleted = 0
              left join UserEntity u
                     on u.id = l.userId and u.isDeleted = 0
             where s.isDeleted = 0
               and (:keyword is null
                    or lower(s.studentCode) like :keyword escape '\\'
                    or lower(s.fullName) like :keyword escape '\\'
                    or lower(u.username) like :keyword escape '\\')
               and (:classId is null
                    or exists (select 1 from ClassStudentEntity cs
                                where cs.studentId = s.id
                                  and cs.classId = :classId
                                  and cs.isDeleted = 0
                                  and cs.status = 'ENROLLED'))
               and (:hasAccount is null
                    or (:hasAccount = 1 and u.id is not null)
                    or (:hasAccount = 0 and u.id is null))
            """)
    Page<Object[]> searchStudentAccounts(@Param("keyword") String keyword,
                                         @Param("classId") Long classId,
                                         @Param("hasAccount") Integer hasAccount,
                                         Pageable pageable);
}
