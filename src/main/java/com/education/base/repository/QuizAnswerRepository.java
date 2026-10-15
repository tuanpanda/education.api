package com.education.base.repository;

import com.education.base.entity.QuizAnswerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** GĐ4 - đáp án theo câu của attempt. */
public interface QuizAnswerRepository extends JpaRepository<QuizAnswerEntity, Long> {

    List<QuizAnswerEntity> findByAttemptIdOrderBySortOrderAscIdAsc(Long attemptId);

    List<QuizAnswerEntity> findByAttemptIdIn(Collection<Long> attemptIds);

    Optional<QuizAnswerEntity> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);
}
