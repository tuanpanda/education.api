package com.education.base.service.homework.impl;

import com.education.base.entity.AssignmentEntity;
import org.junit.jupiter.api.Test;

import static com.education.base.service.homework.impl.HomeworkTestData.DUE;
import static com.education.base.service.homework.impl.HomeworkTestData.assignment;
import static com.education.base.service.homework.impl.HomeworkTestData.submission;
import static org.assertj.core.api.Assertions.assertThat;

class HomeworkRulesTest {

    @Test
    void onTime_inclusiveOfDueAt() {
        AssignmentEntity a = assignment(1, 1, "PUBLISHED", "TEXT");
        assertThat(HomeworkRules.withinWindow(a, null, DUE)).isTrue();
        assertThat(HomeworkRules.late(a, DUE)).isFalse();
        assertThat(HomeworkRules.withinWindow(a, null, DUE.plusSeconds(1))).isFalse();
    }

    @Test
    void lateWindow_untilLateUntil_orOpenEnded() {
        AssignmentEntity a = assignment(1, 1, "PUBLISHED", "TEXT");
        a.setAllowLate(1);
        a.setLateUntil(DUE.plusDays(1));
        assertThat(HomeworkRules.withinWindow(a, null, DUE.plusHours(5))).isTrue();
        assertThat(HomeworkRules.withinWindow(a, null, DUE.plusDays(1))).isTrue();
        assertThat(HomeworkRules.withinWindow(a, null, DUE.plusDays(1).plusMinutes(1))).isFalse();
        a.setLateUntil(null);
        assertThat(HomeworkRules.withinWindow(a, null, DUE.plusDays(30))).isTrue();
    }

    @Test
    void closed_blocksEverything_returnedAllowedUntilClosed() {
        AssignmentEntity a = assignment(1, 1, "PUBLISHED", "TEXT");
        assertThat(HomeworkRules.withinWindow(a, submission(1, 1, 1, "RETURNED"), DUE.plusDays(10))).isTrue();
        a.setStatus("CLOSED");
        assertThat(HomeworkRules.withinWindow(a, null, DUE.minusDays(1))).isFalse();
        assertThat(HomeworkRules.withinWindow(a, submission(1, 1, 1, "RETURNED"), DUE.minusDays(1))).isFalse();
    }

    @Test
    void tabs_exactlyOne() {
        AssignmentEntity a = assignment(1, 1, "PUBLISHED", "TEXT");
        assertThat(HomeworkRules.tab(a, null, DUE.minusHours(1))).isEqualTo("todo");
        assertThat(HomeworkRules.tab(a, submission(1, 1, 1, "DRAFT"), DUE.plusHours(1))).isEqualTo("overdue");
        assertThat(HomeworkRules.tab(a, submission(1, 1, 1, "SUBMITTED"), DUE.plusHours(1))).isEqualTo("submitted");
        assertThat(HomeworkRules.tab(a, submission(1, 1, 1, "GRADED"), DUE.plusHours(1))).isEqualTo("graded");
        assertThat(HomeworkRules.tab(a, submission(1, 1, 1, "RETURNED"), DUE.plusHours(1))).isEqualTo("todo");
        a.setStatus("CLOSED");
        assertThat(HomeworkRules.tab(a, submission(1, 1, 1, "RETURNED"), DUE.plusHours(1))).isEqualTo("overdue");
    }

    @Test
    void matrixStates() {
        AssignmentEntity a = assignment(1, 1, "PUBLISHED", "TEXT");
        assertThat(HomeworkRules.matrixState(a, null, DUE.minusHours(1))).isEqualTo("NOT_SUBMITTED");
        assertThat(HomeworkRules.matrixState(a, submission(1, 1, 1, "DRAFT"), DUE.plusHours(1))).isEqualTo("MISSING");
        var late = submission(1, 1, 1, "SUBMITTED");
        late.setIsLate(1);
        assertThat(HomeworkRules.matrixState(a, late, DUE.plusHours(1))).isEqualTo("LATE");
        assertThat(HomeworkRules.matrixState(a, submission(1, 1, 1, "SUBMITTED"), DUE)).isEqualTo("SUBMITTED");
        assertThat(HomeworkRules.matrixState(a, submission(1, 1, 1, "RETURNED"), DUE)).isEqualTo("RETURNED");
        assertThat(HomeworkRules.matrixState(a, submission(1, 1, 1, "GRADED"), DUE)).isEqualTo("GRADED");
    }
}
