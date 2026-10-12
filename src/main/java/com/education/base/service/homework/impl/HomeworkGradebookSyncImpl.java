package com.education.base.service.homework.impl;

import com.education.base.common.PersistenceFlags;
import com.education.base.common.homework.HomeworkConstants;
import com.education.base.common.homework.HomeworkConstants.SubmissionStatus;
import com.education.base.entity.AssignmentEntity;
import com.education.base.entity.GradeEntity;
import com.education.base.entity.SubmissionEntity;
import com.education.base.repository.GradeRepository;
import com.education.base.repository.SubmissionRepository;
import com.education.base.security.SecurityUtils;
import com.education.base.service.homework.HomeworkGradebookSync;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GĐ2 - đẩy điểm bài tập vào {@code EDU_GRADES} (V20_4). Một dòng chưa xoá / (ASSIGNMENT_ID, STUDENT_ID),
 * GRADE_TYPE = ASSIGNMENT, SCORE quy về thang 10. Mỗi lời gọi chạy transaction riêng (REQUIRES_NEW) để lỗi đồng bộ
 * không làm hỏng việc chấm; bên gọi bắt lỗi, log và ghi audit {@code HOMEWORK_GRADEBOOK_SYNC_FAILED}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeworkGradebookSyncImpl implements HomeworkGradebookSync {

    static final int NOTE_MAX = 255;

    private final GradeRepository gradeRepository;
    private final SubmissionRepository submissionRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onGraded(AssignmentEntity assignment, SubmissionEntity submission) {
        if (!enabled(assignment) || submission.getScore() == null) {
            return;
        }
        upsert(assignment, submission);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onUngraded(AssignmentEntity assignment, SubmissionEntity submission) {
        softDelete(gradeRepository.findByAssignmentIdAndStudentIdAndIsDeleted(
                assignment.getId(), submission.getStudentId(), PersistenceFlags.NOT_DELETED));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void resyncAssignment(AssignmentEntity assignment) {
        List<GradeEntity> existing = gradeRepository.findByAssignmentIdAndIsDeleted(
                assignment.getId(), PersistenceFlags.NOT_DELETED);
        if (!enabled(assignment)) {
            softDelete(existing);
            return;
        }
        Set<Long> graded = new HashSet<>();
        for (SubmissionEntity s : submissionRepository.findByAssignmentIdAndIsDeleted(
                assignment.getId(), PersistenceFlags.NOT_DELETED)) {
            if (SubmissionStatus.GRADED.equals(s.getStatus()) && s.getScore() != null) {
                upsert(assignment, s);
                graded.add(s.getStudentId());
            }
        }
        softDelete(existing.stream().filter(g -> !graded.contains(g.getStudentId())).toList());
    }

    private static boolean enabled(AssignmentEntity a) {
        return a != null && HomeworkRules.flag(a.getSyncToGradebook())
                && (a.getIsDeleted() == null || a.getIsDeleted() == PersistenceFlags.NOT_DELETED);
    }

    private void upsert(AssignmentEntity a, SubmissionEntity s) {
        List<GradeEntity> rows = gradeRepository.findByAssignmentIdAndStudentIdAndIsDeleted(
                a.getId(), s.getStudentId(), PersistenceFlags.NOT_DELETED);
        String user = SecurityUtils.currentUsername();
        GradeEntity g = rows.isEmpty()
                ? GradeEntity.builder().assignmentId(a.getId()).studentId(s.getStudentId()).createdBy(user).build()
                : rows.get(0);
        g.setClassId(a.getClassId());
        g.setGradeType(HomeworkConstants.GRADEBOOK_GRADE_TYPE);
        g.setScore(toScale10(s.getScore(), a.getMaxScore()));
        g.setWeight(a.getGradeWeight() == null ? BigDecimal.ONE : a.getGradeWeight());
        g.setExamDate(a.getDueAt() == null ? null : a.getDueAt().toLocalDate());
        g.setNote(note(a.getTitle()));
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

    static BigDecimal toScale10(BigDecimal score, BigDecimal maxScore) {
        if (score == null || maxScore == null || maxScore.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal v = score.multiply(BigDecimal.valueOf(HomeworkConstants.GRADEBOOK_SCALE))
                .divide(maxScore, 2, RoundingMode.HALF_UP);
        if (v.signum() < 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal ten = BigDecimal.valueOf(HomeworkConstants.GRADEBOOK_SCALE).setScale(2);
        return v.compareTo(ten) > 0 ? ten : v;
    }

    static String note(String title) {
        String n = "BT: " + (title == null ? "" : title.strip());
        return n.length() > NOTE_MAX ? n.substring(0, NOTE_MAX) : n;
    }
}
