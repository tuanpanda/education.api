package com.education.base.repository;

import com.education.base.entity.QuizRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** GĐ4 - quy tắc bốc câu ngẫu nhiên. */
public interface QuizRuleRepository extends JpaRepository<QuizRuleEntity, Long> {

    List<QuizRuleEntity> findByQuizIdOrderBySortOrderAscIdAsc(Long quizId);

    List<QuizRuleEntity> findByQuizIdIn(Collection<Long> quizIds);

    void deleteByQuizId(Long quizId);
}
