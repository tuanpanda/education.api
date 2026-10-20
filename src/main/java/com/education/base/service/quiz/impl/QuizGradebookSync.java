package com.education.base.service.quiz.impl;

import com.education.base.common.PersistenceFlags;
import com.education.base.common.quiz.QuizConstants;
import com.education.base.entity.GradeEntity;
import com.education.base.entity.QuizEntity;
import com.education.base.repository.GradeRepository;
import com.education.base.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * GĐ4 - đẩy điểm đề vào {@code EDU_GRADES} (V22_4, BR-34): 1 dòng chưa xoá / (QUIZ_ID, STUDENT_ID), GRADE_TYPE = QUIZ,
 * SCORE = score10, EXAM_DATE = ngày CLOSE_AT. Chỉ khi {@code SYNC_TO_GRADEBOOK = 1}. Mỗi lời gọi transaction riêng;
 * bên gọi bắt lỗi + audit {@code QUIZ_GRADEBOOK_SYNC_FAILED} (lỗi đồng bộ không làm hỏng việc chấm).
 * Điểm truyền vào từ bên gọi (transaction REQUIRES_NEW không thấy dữ liệu chưa commit).
 */
@Service
@RequiredArgsConstructor
public class QuizGradebookSync {

    static final int NOTE_MAX = 255;

    private final GradeRepository gradeRepository;

    /** @param finalScore điểm đề (thang gốc) của HS; null = chưa có -> gỡ dòng đồng bộ. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void syncStudent(QuizEntity quiz, Long studentId, BigDecimal finalScore) {
        List<GradeEntity> rows = gradeRepository.findByQuizIdAndStudentIdAndIsDeleted(quiz.getId(), studentId,
                PersistenceFlags.NOT_DELETED);
        if (!enabled(quiz) || finalScore == null) {
            softDelete(rows);
            return;
        }
        upsert(quiz, studentId, finalScore, rows);
    }

    /** Bật/tắt cờ hoặc đổi điểm hàng loạt: {@code finals} = điểm đề của mọi HS đã có attempt kết thúc. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void resyncQuiz(QuizEntity quiz, Map<Long, BigDecimal> finals) {
        List<GradeEntity> existing = gradeRepository.findByQuizIdAndIsDeleted(quiz.getId(), PersistenceFlags.NOT_DELETED);
        if (!enabled(quiz)) {
            softDelete(existing);
            return;
        }
        for (Map.Entry<Long, BigDecimal> e : finals.entrySet()) {
            if (e.getValue() != null) {
                upsert(quiz, e.getKey(), e.getValue(), existing.stream()
                        .filter(g -> e.getKey().equals(g.getStudentId())).toList());
            }
        }
        softDelete(existing.stream().filter(g -> finals.get(g.getStudentId()) == null).toList());
    }

    static boolean enabled(QuizEntity q) {
        return q != null && QuizRules.flag(q.getSyncToGradebook())
                && (q.getIsDeleted() == null || q.getIsDeleted() == PersistenceFlags.NOT_DELETED);
    }

    private void upsert(QuizEntity quiz, Long studentId, BigDecimal finalScore, List<GradeEntity> rows) {
        String user = SecurityUtils.currentUsername();
        GradeEntity g = rows.isEmpty()
                ? GradeEntity.builder().quizId(quiz.getId()).studentId(studentId).createdBy(user).build()
                : rows.get(0);
        g.setClassId(quiz.getClassId());
        g.setGradeType(QuizConstants.GRADEBOOK_GRADE_TYPE);
        g.setScore(QuizRules.score10(finalScore, quiz.getMaxScore()));
        g.setWeight(BigDecimal.ONE);
        g.setExamDate(quiz.getCloseAt() == null ? null : quiz.getCloseAt().toLocalDate());
        String note = "KT: " + (quiz.getTitle() == null ? "" : quiz.getTitle().strip());
        g.setNote(note.length() > NOTE_MAX ? note.substring(0, NOTE_MAX) : note);
        g.setIsDeleted(PersistenceFlags.NOT_DELETED);
        g.setUpdatedBy(user);
        gradeRepository.save(g);
        if (rows.size() > 1) {
            softDelete(rows.subList(1, rows.size()));
        }
    }

    private void softDelete(List<GradeEntity> rows) {
        String user = SecurityUtils.currentUsername();
        for (GradeEntity g : rows) {
            g.setIsDeleted(PersistenceFlags.DELETED);
            g.setUpdatedBy(user);
            gradeRepository.save(g);
        }
    }
}
