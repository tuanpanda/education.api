package com.education.base.repository;

import com.education.base.entity.QuizAttemptEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** GĐ4 - sự kiện của attempt. */
public interface QuizAttemptEventRepository extends JpaRepository<QuizAttemptEventEntity, Long> {

    List<QuizAttemptEventEntity> findByAttemptIdOrderByOccurredAtAscIdAsc(Long attemptId);
}
