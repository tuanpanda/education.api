package com.education.base.service.homework.impl;

import com.education.base.entity.AssignmentEntity;
import com.education.base.entity.GradeEntity;
import com.education.base.entity.SubmissionEntity;
import com.education.base.repository.GradeRepository;
import com.education.base.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.education.base.service.homework.impl.HomeworkTestData.DUE;
import static com.education.base.service.homework.impl.HomeworkTestData.assignment;
import static com.education.base.service.homework.impl.HomeworkTestData.submission;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeworkGradebookSyncImplTest {

    GradeRepository gradeRepository = mock(GradeRepository.class);
    SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    HomeworkGradebookSyncImpl sync = new HomeworkGradebookSyncImpl(gradeRepository, submissionRepository);

    AssignmentEntity synced() {
        AssignmentEntity a = assignment(1, 7, "PUBLISHED", "TEXT");
        a.setSyncToGradebook(1);
        a.setMaxScore(new BigDecimal("20"));
        a.setGradeWeight(new BigDecimal("2"));
        return a;
    }

    @Test
    void scaleTo10_rounded_andClamped() {
        assertThat(HomeworkGradebookSyncImpl.toScale10(new BigDecimal("15"), new BigDecimal("20"))).isEqualByComparingTo("7.50");
        assertThat(HomeworkGradebookSyncImpl.toScale10(new BigDecimal("1"), new BigDecimal("3"))).isEqualByComparingTo("3.33");
        assertThat(HomeworkGradebookSyncImpl.toScale10(new BigDecimal("30"), new BigDecimal("20"))).isEqualByComparingTo("10");
        assertThat(HomeworkGradebookSyncImpl.note("x".repeat(300))).hasSize(255).startsWith("BT: ");
    }

    @Test
    void onGraded_insertsAssignmentGrade() {
        SubmissionEntity s = submission(10, 1, 50, "GRADED");
        s.setScore(new BigDecimal("15"));
        when(gradeRepository.findByAssignmentIdAndStudentIdAndIsDeleted(1L, 50L, 0)).thenReturn(List.of());
        sync.onGraded(synced(), s);
        ArgumentCaptor<GradeEntity> c = ArgumentCaptor.forClass(GradeEntity.class);
        verify(gradeRepository).save(c.capture());
        GradeEntity g = c.getValue();
        assertThat(g.getAssignmentId()).isEqualTo(1L);
        assertThat(g.getClassId()).isEqualTo(7L);
        assertThat(g.getGradeType()).isEqualTo("ASSIGNMENT");
        assertThat(g.getScore()).isEqualByComparingTo("7.5");
        assertThat(g.getWeight()).isEqualByComparingTo("2");
        assertThat(g.getExamDate()).isEqualTo(DUE.toLocalDate());
        assertThat(g.getNote()).isEqualTo("BT: Bai 1");
        assertThat(g.getIsDeleted()).isZero();
    }

    @Test
    void onGraded_upsertsExistingRow_andIgnoredWhenFlagOff() {
        SubmissionEntity s = submission(10, 1, 50, "GRADED");
        s.setScore(new BigDecimal("20"));
        GradeEntity existing = GradeEntity.builder().id(3L).assignmentId(1L).studentId(50L).score(BigDecimal.ONE).isDeleted(0).build();
        when(gradeRepository.findByAssignmentIdAndStudentIdAndIsDeleted(1L, 50L, 0)).thenReturn(List.of(existing));
        sync.onGraded(synced(), s);
        assertThat(existing.getScore()).isEqualByComparingTo("10");
        AssignmentEntity off = synced();
        off.setSyncToGradebook(0);
        sync.onGraded(off, s);
        verify(gradeRepository).save(existing);
    }

    @Test
    void onUngraded_softDeletes() {
        GradeEntity existing = GradeEntity.builder().id(3L).assignmentId(1L).studentId(50L).isDeleted(0).build();
        when(gradeRepository.findByAssignmentIdAndStudentIdAndIsDeleted(1L, 50L, 0)).thenReturn(List.of(existing));
        sync.onUngraded(synced(), submission(10, 1, 50, "RETURNED"));
        assertThat(existing.getIsDeleted()).isEqualTo(1);
    }

    @Test
    void resync_upsertsGraded_softDeletesOthers_orAllWhenDisabled() {
        AssignmentEntity a = synced();
        SubmissionEntity graded = submission(10, 1, 50, "GRADED");
        graded.setScore(BigDecimal.TEN);
        SubmissionEntity returned = submission(11, 1, 51, "RETURNED");
        GradeEntity g50 = GradeEntity.builder().id(3L).assignmentId(1L).studentId(50L).isDeleted(0).build();
        GradeEntity g51 = GradeEntity.builder().id(4L).assignmentId(1L).studentId(51L).isDeleted(0).build();
        when(gradeRepository.findByAssignmentIdAndIsDeleted(1L, 0)).thenReturn(new ArrayList<>(List.of(g50, g51)));
        when(gradeRepository.findByAssignmentIdAndStudentIdAndIsDeleted(1L, 50L, 0)).thenReturn(List.of(g50));
        when(submissionRepository.findByAssignmentIdAndIsDeleted(1L, 0)).thenReturn(List.of(graded, returned));
        sync.resyncAssignment(a);
        assertThat(g50.getScore()).isEqualByComparingTo("5");
        assertThat(g50.getIsDeleted()).isZero();
        assertThat(g51.getIsDeleted()).isEqualTo(1);

        g50.setIsDeleted(0);
        a.setSyncToGradebook(0);
        sync.resyncAssignment(a);
        assertThat(g50.getIsDeleted()).isEqualTo(1);
        verify(gradeRepository, atLeastOnce()).save(any());
        verify(submissionRepository, never()).findAll();
    }
}
