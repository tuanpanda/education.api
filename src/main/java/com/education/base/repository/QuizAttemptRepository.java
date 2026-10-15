package com.education.base.repository;

import com.education.base.entity.QuizAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** GĐ4 - lượt làm bài. */
public interface QuizAttemptRepository extends JpaRepository<QuizAttemptEntity, Long> {

    List<QuizAttemptEntity> findByQuizIdAndStudentIdOrderByAttemptNoAsc(Long quizId, Long studentId);

    List<QuizAttemptEntity> findByQuizIdOrderByStudentIdAscAttemptNoAsc(Long quizId);

    List<QuizAttemptEntity> findByStudentIdAndQuizIdIn(Long studentId, Collection<Long> quizIds);

    List<QuizAttemptEntity> findByQuizIdInAndStatus(Collection<Long> quizIds, String status);

    Optional<QuizAttemptEntity> findFirstByQuizIdAndStudentIdAndStatus(Long quizId, Long studentId, String status);

    boolean existsByQuizId(Long quizId);

    long countByQuizId(Long quizId);

    List<QuizAttemptEntity> findByQuizIdAndStatus(Long quizId, String status);

    /** Attempt đang làm đã quá hạn + ân hạn (job tự nộp). */
    @Query("select a.id from QuizAttemptEntity a where a.status = 'IN_PROGRESS' and a.deadlineAt < :threshold")
    List<Long> findExpiredInProgressIds(@Param("threshold") LocalDateTime threshold);

    /**
     * Chuyển trạng thái có điều kiện {@code WHERE STATUS='IN_PROGRESS'} - chỉ 1 bên (job / nộp tay / nộp lười) thắng.
     *
     * @return 1 nếu chuyển được, 0 nếu attempt đã kết thúc.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update QuizAttemptEntity a set a.status = :status, a.submittedAt = :submittedAt, a.version = a.version + 1
             where a.id = :id and a.status = 'IN_PROGRESS'
            """)
    int finishIfInProgress(@Param("id") Long id, @Param("status") String status,
                           @Param("submittedAt") LocalDateTime submittedAt);

    /** Đóng sớm: rút DEADLINE_AT của attempt đang làm về CLOSE_AT. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update QuizAttemptEntity a set a.deadlineAt = :closeAt, a.version = a.version + 1
             where a.quizId = :quizId and a.status = 'IN_PROGRESS' and a.deadlineAt > :closeAt
            """)
    int shortenDeadlines(@Param("quizId") Long quizId, @Param("closeAt") LocalDateTime closeAt);
}
