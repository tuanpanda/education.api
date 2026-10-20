package com.education.base.service.quiz.impl;

import com.education.base.audit.AuditActions;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.quiz.QuizConstants.AttemptStatus;
import com.education.base.common.quiz.QuizConstants.EventType;
import com.education.base.common.quiz.QuizErrorCodes;
import com.education.base.entity.QuizAnswerEntity;
import com.education.base.entity.QuizAttemptEntity;
import com.education.base.entity.QuizAttemptEventEntity;
import com.education.base.entity.QuizEntity;
import com.education.base.repository.QuizAnswerRepository;
import com.education.base.repository.QuizAttemptEventRepository;
import com.education.base.repository.QuizAttemptRepository;
import com.education.base.repository.QuizRepository;
import com.education.base.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GĐ4 - chốt / chấm attempt. Chuyển IN_PROGRESS -> SUBMITTED|AUTO_SUBMITTED bằng cập nhật có điều kiện
 * ({@link QuizAttemptRepository#finishIfInProgress}) nên job, nộp tay và nộp lười chỉ thắng 1 lần.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizGradingService {

    private final QuizAttemptRepository attemptRepository;
    private final QuizAnswerRepository answerRepository;
    private final QuizAttemptEventRepository eventRepository;
    private final QuizRepository quizRepository;
    private final QuizGradebookSync gradebookSync;
    private final QuizJson json;
    private final AuditService auditService;

    /**
     * Chốt attempt nếu còn IN_PROGRESS. {@code auto=true}: AUTO_SUBMITTED (job / nộp lười), ngược lại SUBMITTED.
     * Còn câu ESSAY chưa chấm thì giữ trạng thái đó, không thì GRADED.
     *
     * @return attempt sau khi chốt (đọc lại từ DB).
     */
    @Transactional(rollbackFor = Exception.class)
    public QuizAttemptEntity finish(Long attemptId, boolean auto, LocalDateTime now) {
        int changed = attemptRepository.finishIfInProgress(attemptId,
                auto ? AttemptStatus.AUTO_SUBMITTED : AttemptStatus.SUBMITTED, now);
        QuizAttemptEntity a = attemptRepository.findById(attemptId).orElseThrow(() ->
                new com.education.base.exception.OracleBusinessException(QuizErrorCodes.QUIZ_ATTEMPT_NOT_FOUND,
                        "Không tìm thấy lượt làm bài."));
        if (changed == 1) {
            List<QuizAnswerEntity> answers = answerRepository.findByAttemptIdOrderBySortOrderAscIdAsc(a.getId());
            for (QuizAnswerEntity ans : answers) {
                autoGrade(ans);
            }
            answerRepository.saveAll(answers);
            applyScore(a, answers);
            attemptRepository.save(a);
            eventRepository.save(QuizAttemptEventEntity.builder().attemptId(a.getId())
                    .eventType(auto ? EventType.AUTO_SUBMITTED : EventType.SUBMITTED).occurredAt(now).build());
            quizRepository.findById(a.getQuizId()).ifPresent(q -> syncStudent(q, a.getStudentId()));
        }
        return a;
    }

    /** Nộp lười: attempt IN_PROGRESS đã quá hạn + ân hạn -> AUTO_SUBMITTED. */
    @Transactional(rollbackFor = Exception.class)
    public QuizAttemptEntity expireIfDue(QuizAttemptEntity a, LocalDateTime now) {
        if (a != null && AttemptStatus.IN_PROGRESS.equals(a.getStatus()) && QuizRules.overdue(a, now)) {
            return finish(a.getId(), true, now);
        }
        return a;
    }

    /** Nộp lười hàng loạt (ma trận GV, job). @return số attempt đã chốt. */
    @Transactional(rollbackFor = Exception.class)
    public int expireAll(Collection<QuizAttemptEntity> attempts, LocalDateTime now) {
        int n = 0;
        for (QuizAttemptEntity a : attempts) {
            if (AttemptStatus.IN_PROGRESS.equals(a.getStatus()) && QuizRules.overdue(a, now)) {
                finish(a.getId(), true, now);
                n++;
            }
        }
        return n;
    }

    /** Chấm tự động 1 câu (không đụng ESSAY đã chấm). */
    void autoGrade(QuizAnswerEntity ans) {
        QuizJson.Snapshot s = json.snapshot(ans.getSnapshot());
        if (s == null) {
            return;
        }
        Boolean correct = QuizRules.autoCorrect(s, json.longs(ans.getSelectedOptionIds()), ans.getTextAnswer());
        if (correct == null) {
            boolean blank = (ans.getTextAnswer() == null || ans.getTextAnswer().isBlank());
            if (blank && ans.getPointsAwarded() == null) {
                // Bỏ trống câu tự luận -> 0 (BR: bỏ trống = 0), không cần GV chấm.
                ans.setPointsAwarded(BigDecimal.ZERO);
                ans.setGradedBy("SYSTEM");
            }
            return;
        }
        ans.setIsCorrect(correct ? 1 : 0);
        ans.setPointsAwarded(correct ? ans.getPoints() : BigDecimal.ZERO);
        ans.setGradedBy("SYSTEM");
    }

    /** Điểm attempt = tổng điểm câu; còn ESSAY chưa chấm -> giữ SUBMITTED/AUTO_SUBMITTED, không thì GRADED. */
    static void applyScore(QuizAttemptEntity a, List<QuizAnswerEntity> answers) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        boolean pending = false;
        for (QuizAnswerEntity ans : answers) {
            max = max.add(ans.getPoints() == null ? BigDecimal.ZERO : ans.getPoints());
            if (ans.getPointsAwarded() == null) {
                pending = true;
            } else {
                total = total.add(ans.getPointsAwarded());
            }
        }
        a.setScore(total);
        a.setMaxScore(max);
        boolean auto = AttemptStatus.AUTO_SUBMITTED.equals(a.getStatus());
        if (pending) {
            if (AttemptStatus.GRADED.equals(a.getStatus())) {
                // GV gỡ điểm câu tự luận -> quay về trạng thái nộp ban đầu (nộp tự động = nộp sau hạn + ân hạn).
                boolean wasAuto = a.getSubmittedAt() != null && a.getDeadlineAt() != null && QuizRules.overdue(a, a.getSubmittedAt());
                a.setStatus(wasAuto ? AttemptStatus.AUTO_SUBMITTED : AttemptStatus.SUBMITTED);
            }
        } else if (!auto || teacherGradedEssay(answers)) {
            // HS tự nộp: chấm xong -> GRADED. Nộp tự động: chỉ GRADED khi GV đã chấm xong câu tự luận;
            // đề không có tự luận (chấm máy đủ điểm) giữ AUTO_SUBMITTED.
            a.setStatus(AttemptStatus.GRADED);
        }
    }

    /** Có câu tự luận do GV chấm (không phải SYSTEM, không phải câu chấm máy). */
    static boolean teacherGradedEssay(List<QuizAnswerEntity> answers) {
        return answers.stream().anyMatch(x -> x.getIsCorrect() == null && x.getPointsAwarded() != null
                && x.getGradedBy() != null && !"SYSTEM".equals(x.getGradedBy()));
    }

    static boolean pendingEssay(List<QuizAnswerEntity> answers) {
        return answers.stream().anyMatch(x -> x.getPointsAwarded() == null);
    }

    /** Tính lại điểm đề của HS + đồng bộ sổ điểm (lỗi chỉ log + audit). */
    public void syncStudent(QuizEntity quiz, Long studentId) {
        if (!QuizGradebookSync.enabled(quiz)) {
            return;
        }
        List<QuizAttemptEntity> attempts = attemptRepository.findByQuizIdAndStudentIdOrderByAttemptNoAsc(quiz.getId(), studentId);
        BigDecimal fin = QuizRules.finalScore(quiz.getScorePolicy(), attempts);
        safe(quiz, () -> gradebookSync.syncStudent(quiz, studentId, fin));
    }

    /** Đồng bộ lại cả đề (bật/tắt cờ, đổi cách tính). */
    public void resyncQuiz(QuizEntity quiz) {
        Map<Long, BigDecimal> finals = new LinkedHashMap<>();
        if (QuizGradebookSync.enabled(quiz)) {
            Map<Long, List<QuizAttemptEntity>> byStudent = new HashMap<>();
            for (QuizAttemptEntity a : attemptRepository.findByQuizIdOrderByStudentIdAscAttemptNoAsc(quiz.getId())) {
                byStudent.computeIfAbsent(a.getStudentId(), k -> new java.util.ArrayList<>()).add(a);
            }
            byStudent.forEach((sid, list) -> finals.put(sid, QuizRules.finalScore(quiz.getScorePolicy(), list)));
        }
        safe(quiz, () -> gradebookSync.resyncQuiz(quiz, finals));
    }

    private void safe(QuizEntity quiz, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("Đồng bộ sổ điểm đề {} lỗi: {}", quiz.getId(), e.getMessage(), e);
            try {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("quizId", quiz.getId());
                d.put("code", QuizErrorCodes.QUIZ_GRADEBOOK_SYNC_FAILED);
                String msg = String.valueOf(e.getMessage());
                d.put("error", msg.length() > 200 ? msg.substring(0, 200) : msg);
                auditService.failure(AuditActions.QUIZ_GRADEBOOK_SYNC_FAILED, AuditActions.RESOURCE_QUIZ, quiz.getId(), d);
            } catch (RuntimeException ignored) {
                log.warn("Không ghi được audit đồng bộ sổ điểm: {}", ignored.getMessage());
            }
        }
    }

    Optional<QuizEntity> quiz(Long id) {
        return quizRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED);
    }
}
