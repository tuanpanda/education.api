package com.education.base.repository;

import com.education.base.entity.QuizEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** GĐ4 - đề kiểm tra. */
public interface QuizRepository extends JpaRepository<QuizEntity, Long>, JpaSpecificationExecutor<QuizEntity> {

    Optional<QuizEntity> findByIdAndIsDeleted(Long id, Integer isDeleted);

    List<QuizEntity> findByClassIdInAndIsDeletedAndStatusIn(Collection<Long> classIds, Integer isDeleted,
                                                          Collection<String> statuses);
}
