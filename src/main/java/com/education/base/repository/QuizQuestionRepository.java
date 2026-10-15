package com.education.base.repository;

import com.education.base.entity.QuizQuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** GĐ4 - câu chọn tay của đề. */
public interface QuizQuestionRepository extends JpaRepository<QuizQuestionEntity, Long> {

    List<QuizQuestionEntity> findByQuizIdOrderBySortOrderAscIdAsc(Long quizId);

    List<QuizQuestionEntity> findByQuizIdIn(Collection<Long> quizIds);

    void deleteByQuizId(Long quizId);

    /** [questionId, số đề chưa xoá dùng câu]. */
    @Query("""
            select qq.questionId, count(distinct qq.quizId) from QuizQuestionEntity qq, QuizEntity z
             where z.id = qq.quizId and z.isDeleted = 0 and qq.questionId in :ids
             group by qq.questionId
            """)
    List<Object[]> countQuizzesByQuestionIds(@Param("ids") Collection<Long> ids);
}
