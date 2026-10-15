package com.education.base.repository;

import com.education.base.entity.QuestionOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** GĐ4 - lựa chọn câu hỏi. */
public interface QuestionOptionRepository extends JpaRepository<QuestionOptionEntity, Long> {

    List<QuestionOptionEntity> findByQuestionIdOrderBySortOrderAscIdAsc(Long questionId);

    List<QuestionOptionEntity> findByQuestionIdInOrderBySortOrderAscIdAsc(Collection<Long> questionIds);

    void deleteByQuestionId(Long questionId);
}
