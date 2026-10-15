package com.education.base.repository;

import com.education.base.entity.QuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** GĐ4 - ngân hàng câu hỏi ({@code LMS_QUESTIONS}). */
public interface QuestionRepository extends JpaRepository<QuestionEntity, Long>, JpaSpecificationExecutor<QuestionEntity> {

    Optional<QuestionEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    List<QuestionEntity> findByIdIn(Collection<Long> ids);

    /**
     * Ứng viên cho quy tắc ngẫu nhiên: chưa xoá, đúng chủ đề + độ khó, nhìn thấy được với người tạo đề
     * ({@code ownerId} null = không giới hạn; khác null = câu của người đó hoặc SHARED).
     */
    @Query("""
            select q from QuestionEntity q
             where q.isDeleted = 0 and lower(q.topic) = lower(:topic) and q.difficulty = :difficulty
               and (:ownerId is null or q.ownerId = :ownerId or q.visibility = 'SHARED')
             order by q.id
            """)
    List<QuestionEntity> findCandidates(@Param("topic") String topic, @Param("difficulty") Integer difficulty,
                                        @Param("ownerId") Long ownerId);

    /** Đếm câu theo chủ đề × độ khó: [topic, difficulty, count]. */
    @Query("""
            select q.topic, q.difficulty, count(q) from QuestionEntity q
             where q.isDeleted = 0
               and (:subjectName is null or q.subjectName = :subjectName)
               and (:gradeLevel is null or q.gradeLevel = :gradeLevel)
               and (:ownerId is null or q.ownerId = :ownerId or q.visibility = 'SHARED')
             group by q.topic, q.difficulty
             order by q.topic, q.difficulty
            """)
    List<Object[]> countByTopic(@Param("subjectName") String subjectName, @Param("gradeLevel") String gradeLevel,
                                @Param("ownerId") Long ownerId);
}
