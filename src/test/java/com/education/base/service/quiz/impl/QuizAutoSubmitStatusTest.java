package com.education.base.service.quiz.impl;

import com.education.base.entity.QuizAnswerEntity;
import com.education.base.entity.QuizAttemptEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** BUG-GD4-3: nộp tự động giữ AUTO_SUBMITTED; chỉ GRADED khi GV chấm xong tự luận. HS tự nộp giữ như cũ. */
class QuizAutoSubmitStatusTest {

    static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 10, 20, 8, 30);

    static QuizAttemptEntity attempt(String status, LocalDateTime submittedAt) {
        return QuizAttemptEntity.builder().id(1L).status(status).deadlineAt(DEADLINE).submittedAt(submittedAt).build();
    }

    static QuizAnswerEntity auto(String pts) {
        return QuizAnswerEntity.builder().points(BigDecimal.ONE).pointsAwarded(new BigDecimal(pts)).isCorrect(1)
                .gradedBy("SYSTEM").build();
    }

    static QuizAnswerEntity essay(String pts, String by) {
        return QuizAnswerEntity.builder().points(BigDecimal.ONE).pointsAwarded(pts == null ? null : new BigDecimal(pts))
                .gradedBy(by).build();
    }

    LocalDateTime late() {
        return DEADLINE.plusSeconds(QuizRules.graceSec() + 60);
    }

    @Test
    void autoSubmitted_noEssay_staysAutoSubmitted_scoreComplete() {
        QuizAttemptEntity a = attempt("AUTO_SUBMITTED", late());
        QuizGradingService.applyScore(a, List.of(auto("1"), auto("0")));
        assertThat(a.getStatus()).isEqualTo("AUTO_SUBMITTED");
        assertThat(a.getScore()).isEqualByComparingTo("1");
        assertThat(a.getMaxScore()).isEqualByComparingTo("2");
    }

    @Test
    void autoSubmitted_blankEssayScoredZeroBySystem_staysAutoSubmitted() {
        QuizAttemptEntity a = attempt("AUTO_SUBMITTED", late());
        QuizGradingService.applyScore(a, List.of(auto("1"), essay("0", "SYSTEM")));
        assertThat(a.getStatus()).isEqualTo("AUTO_SUBMITTED");
    }

    @Test
    void autoSubmitted_withEssay_staysAutoUntilTeacherGradesAll_thenGraded() {
        QuizAttemptEntity a = attempt("AUTO_SUBMITTED", late());
        QuizGradingService.applyScore(a, List.of(auto("1"), essay(null, null), essay(null, null)));
        assertThat(a.getStatus()).isEqualTo("AUTO_SUBMITTED");
        QuizGradingService.applyScore(a, List.of(auto("1"), essay("1", "gv1"), essay(null, null)));
        assertThat(a.getStatus()).isEqualTo("AUTO_SUBMITTED");
        QuizGradingService.applyScore(a, List.of(auto("1"), essay("1", "gv1"), essay("0.5", "gv1")));
        assertThat(a.getStatus()).isEqualTo("GRADED");
        assertThat(a.getScore()).isEqualByComparingTo("2.5");
    }

    @Test
    void graded_autoAttempt_teacherClearsEssay_revertsToAutoSubmitted() {
        QuizAttemptEntity a = attempt("GRADED", late());
        QuizGradingService.applyScore(a, List.of(auto("1"), essay(null, null)));
        assertThat(a.getStatus()).isEqualTo("AUTO_SUBMITTED");
    }

    @Test
    void selfSubmitted_inTimeOrGrace_unchanged() {
        QuizAttemptEntity a = attempt("SUBMITTED", DEADLINE.plusSeconds(1));
        QuizGradingService.applyScore(a, List.of(auto("1")));
        assertThat(a.getStatus()).isEqualTo("GRADED");
        QuizAttemptEntity b = attempt("SUBMITTED", DEADLINE.minusMinutes(5));
        QuizGradingService.applyScore(b, List.of(auto("1"), essay(null, null)));
        assertThat(b.getStatus()).isEqualTo("SUBMITTED");
        QuizAttemptEntity c = attempt("GRADED", DEADLINE.minusMinutes(5));
        QuizGradingService.applyScore(c, List.of(auto("1"), essay(null, null)));
        assertThat(c.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void autoSubmitted_teacherOverridesAutoQuestionOnly_staysAutoSubmitted() {
        QuizAttemptEntity a = attempt("AUTO_SUBMITTED", late());
        QuizAnswerEntity overridden = auto("0.5");
        overridden.setGradedBy("gv1");
        QuizGradingService.applyScore(a, List.of(overridden));
        assertThat(a.getStatus()).isEqualTo("AUTO_SUBMITTED");
    }
}
