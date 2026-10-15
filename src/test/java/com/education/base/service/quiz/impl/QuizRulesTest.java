package com.education.base.service.quiz.impl;

import com.education.base.entity.QuizAttemptEntity;
import com.education.base.entity.QuizEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** GĐ4 - luật thuần: chấm (QZ-G01..G04), điểm nhiều lượt (G11), thang 10 (G13), ân hạn (E04), hạn (D10), công bố (G05..G10). */
class QuizRulesTest {

    static final LocalDateTime T = LocalDateTime.of(2026, 10, 20, 8, 0);

    static QuizJson.Snapshot single() {
        return new QuizJson.Snapshot(1L, "SINGLE", "t", 1, "c", "e",
                List.of(new QuizJson.SnapOption(11L, "a", false), new QuizJson.SnapOption(12L, "b", true)), List.of(), BigDecimal.ONE);
    }

    static QuizJson.Snapshot multi() {
        return new QuizJson.Snapshot(2L, "MULTI", "t", 1, "c", null,
                List.of(new QuizJson.SnapOption(21L, "a", true), new QuizJson.SnapOption(22L, "b", true),
                        new QuizJson.SnapOption(23L, "c", false)), List.of(), BigDecimal.ONE);
    }

    static QuizJson.Snapshot shortText(String... accepted) {
        return new QuizJson.Snapshot(3L, "SHORT_TEXT", "t", 1, "c", null, List.of(), List.of(accepted), BigDecimal.ONE);
    }

    @Test
    void single_trueFalse_blank() {
        assertThat(QuizRules.autoCorrect(single(), List.of(12L), null)).isTrue();
        assertThat(QuizRules.autoCorrect(single(), List.of(11L), null)).isFalse();
        assertThat(QuizRules.autoCorrect(single(), List.of(), null)).isFalse();
        assertThat(QuizRules.autoCorrect(single(), null, null)).isFalse();
    }

    @Test
    void multi_allOrNothing_QZ_G02() {
        assertThat(QuizRules.autoCorrect(multi(), List.of(21L, 22L), null)).isTrue();
        assertThat(QuizRules.autoCorrect(multi(), List.of(21L), null)).isFalse();
        assertThat(QuizRules.autoCorrect(multi(), List.of(21L, 22L, 23L), null)).isFalse();
        assertThat(QuizRules.autoCorrect(multi(), List.of(), null)).isFalse();
    }

    @Test
    void shortText_caseSpaceNfc_butAccentSensitive_QZ_G03_G04() {
        QuizJson.Snapshot s = shortText("Hà Nội");
        assertThat(QuizRules.autoCorrect(s, null, "  hà   NỘI ")).isTrue();
        String nfd = java.text.Normalizer.normalize("Hà Nội", java.text.Normalizer.Form.NFD);
        assertThat(QuizRules.autoCorrect(s, null, nfd)).isTrue();
        assertThat(QuizRules.autoCorrect(s, null, "Ha Noi")).isFalse();
        assertThat(QuizRules.autoCorrect(s, null, "")).isFalse();
    }

    @Test
    void essay_isManual() {
        QuizJson.Snapshot e = new QuizJson.Snapshot(4L, "ESSAY", "t", 1, "c", null, List.of(), List.of(), BigDecimal.ONE);
        assertThat(QuizRules.autoCorrect(e, null, "bài làm")).isNull();
    }

    @Test
    void scorePolicies_QZ_G11() {
        List<QuizAttemptEntity> attempts = List.of(attempt(1, "GRADED", "6"), attempt(2, "GRADED", "8"),
                attempt(3, "IN_PROGRESS", null));
        assertThat(QuizRules.finalScore("HIGHEST", attempts)).isEqualByComparingTo("8");
        assertThat(QuizRules.finalScore("LAST", attempts)).isEqualByComparingTo("8");
        assertThat(QuizRules.finalScore("AVERAGE", attempts)).isEqualByComparingTo("7");
        assertThat(QuizRules.finalScore(null, attempts)).isEqualByComparingTo("8");
        assertThat(QuizRules.finalScore("HIGHEST", List.of(attempt(1, "IN_PROGRESS", null)))).isNull();
    }

    @Test
    void scale10_QZ_G13() {
        assertThat(QuizRules.score10(new BigDecimal("15"), new BigDecimal("20"))).isEqualByComparingTo("7.5");
        assertThat(QuizRules.score10(null, BigDecimal.TEN)).isNull();
        assertThat(QuizRules.score10(BigDecimal.ONE, BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void grace_boundaryIsClosed_QZ_E04() {
        QuizAttemptEntity a = attempt(1, "IN_PROGRESS", null);
        a.setDeadlineAt(T);
        assertThat(QuizRules.overdue(a, T.plusSeconds(20))).isFalse();
        assertThat(QuizRules.overdue(a, T.plusSeconds(30))).isFalse();
        assertThat(QuizRules.overdue(a, T.plusSeconds(31))).isTrue();
        assertThat(QuizRules.remainingSeconds(T, T.plusSeconds(10))).isZero();
        assertThat(QuizRules.remainingSeconds(T, T.minusSeconds(90))).isEqualTo(90);
    }

    @Test
    void deadline_isMinOfDurationAndClose_QZ_D10() {
        QuizEntity q = quiz("AFTER_CLOSE");
        q.setCloseAt(T.plusMinutes(5));
        q.setDurationMin(30);
        assertThat(QuizRules.deadline(T, q)).isEqualTo(T.plusMinutes(5));
        q.setDurationMin(null);
        assertThat(QuizRules.deadline(T, q)).isEqualTo(T.plusMinutes(5));
        q.setCloseAt(T.plusHours(2));
        q.setDurationMin(30);
        assertThat(QuizRules.deadline(T, q)).isEqualTo(T.plusMinutes(30));
    }

    @Test
    void release_scoreVsAnswers_QZ_G05_G06_G09() {
        QuizEntity imm = quiz("IMMEDIATE");
        assertThat(QuizRules.scoreReleased(imm, T.plusMinutes(10))).isTrue();
        assertThat(QuizRules.answersReleased(imm, T.plusMinutes(10))).isFalse();
        assertThat(QuizRules.answersReleased(imm, T.plusHours(2))).isTrue();
        QuizEntity after = quiz("AFTER_CLOSE");
        assertThat(QuizRules.scoreReleased(after, T.plusMinutes(10))).isFalse();
        assertThat(QuizRules.scoreReleased(after, T.plusHours(1))).isTrue();
        QuizEntity manual = quiz("MANUAL");
        assertThat(QuizRules.scoreReleased(manual, T.plusHours(2))).isFalse();
        manual.setResultsReleasedAt(T.plusHours(2));
        assertThat(QuizRules.answersReleased(manual, T.plusHours(2))).isTrue();
    }

    @Test
    void closedBeforeOpen_isNeverListed_QZ_K12_C11() {
        QuizEntity q = quiz("AFTER_CLOSE");
        q.setStatus("CLOSED");
        q.setClosedAt(T.minusDays(1));
        assertThat(QuizRules.portalListed(q, T.plusMinutes(5))).isFalse();
        q.setClosedAt(T.plusMinutes(10));
        q.setCloseAt(T.plusMinutes(10));
        assertThat(QuizRules.portalListed(q, T.plusMinutes(20))).isTrue();
        q.setStatus("DRAFT");
        assertThat(QuizRules.portalListed(q, T.plusMinutes(20))).isFalse();
    }

    @Test
    void preview_stripsHtml() {
        assertThat(QuizRules.preview("<p>Xin <b>chào</b>&nbsp;bạn</p>")).isEqualTo("Xin chào bạn");
        assertThat(QuizRules.preview("x".repeat(500))).hasSize(200);
    }

    static QuizAttemptEntity attempt(int no, String status, String score) {
        return QuizAttemptEntity.builder().id((long) no).attemptNo(no).status(status)
                .score(score == null ? null : new BigDecimal(score)).deadlineAt(T.plusHours(1)).build();
    }

    static QuizEntity quiz(String mode) {
        return QuizEntity.builder().id(1L).classId(7L).status("PUBLISHED").resultMode(mode).openAt(T).closeAt(T.plusHours(1))
                .maxAttempts(1).maxScore(BigDecimal.TEN).isDeleted(0).build();
    }
}
