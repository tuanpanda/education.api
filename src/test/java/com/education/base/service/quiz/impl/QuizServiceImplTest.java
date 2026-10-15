package com.education.base.service.quiz.impl;

import com.education.base.dto.request.GradeQuizAnswerRequest;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.QuizAnswerEntity;
import com.education.base.entity.QuizAttemptEntity;
import com.education.base.entity.QuizEntity;
import com.education.base.entity.QuizQuestionEntity;
import com.education.base.entity.QuizRuleEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.LessonRepository;
import com.education.base.repository.QuestionRepository;
import com.education.base.repository.QuizAnswerRepository;
import com.education.base.repository.QuizAttemptEventRepository;
import com.education.base.repository.QuizAttemptRepository;
import com.education.base.repository.QuizQuestionRepository;
import com.education.base.repository.QuizRepository;
import com.education.base.repository.QuizRuleRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.common.HtmlContentSanitizer;
import com.education.base.service.AuditService;
import com.education.base.support.TestSecurityContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** GĐ4 GV: copy (AC-40), đóng sớm (AC-27), công bố (AC-12), chấm/ghi đè (AC-15/16). */
class QuizServiceImplTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final LocalDateTime OPEN = LocalDateTime.of(2026, 10, 20, 8, 0);

    QuizRepository quizRepository = mock(QuizRepository.class);
    QuizQuestionRepository quizQuestionRepository = mock(QuizQuestionRepository.class);
    QuizRuleRepository ruleRepository = mock(QuizRuleRepository.class);
    QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
    QuizAnswerRepository answerRepository = mock(QuizAnswerRepository.class);
    QuestionRepository questionRepository = mock(QuestionRepository.class);
    QuizGradingService grading = mock(QuizGradingService.class);
    QuizAccess access = mock(QuizAccess.class);
    AuditService audit = mock(AuditService.class);
    QuizJson json = new QuizJson();
    QuestionServiceImpl questionService = mock(QuestionServiceImpl.class);
    QuizServiceImpl service;
    QuizEntity quiz;
    LocalDateTime now = OPEN.plusMinutes(10);

    @BeforeEach
    void setUp() {
        TestSecurityContexts.login(5L, List.of("TEACHER"), java.util.Set.of());
        rebuild();
        quiz = QuizEntity.builder().id(1L).classId(7L).lessonId(70L).title("KT").description("d").status("PUBLISHED")
                .openAt(OPEN).closeAt(OPEN.plusHours(1)).durationMin(30).maxAttempts(2).resultMode("MANUAL")
                .scorePolicy("HIGHEST").shuffleQuestions(1).shuffleOptions(0).maxScore(BigDecimal.TEN)
                .resultsReleasedAt(OPEN).isDeleted(0).version(3L).build();
        when(quizRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(quiz));
        when(quizRepository.saveAndFlush(any())).thenAnswer(i -> {
            QuizEntity q = i.getArgument(0);
            if (q.getId() == null) {
                q.setId(2L);
            }
            return q;
        });
        when(quizRepository.findById(any())).thenAnswer(i -> Optional.empty());
        when(access.requireClassScope(7L)).thenReturn(cls(7L));
        when(access.requireClassScope(8L)).thenReturn(cls(8L));
        when(grading.expireIfDue(any(), any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TestSecurityContexts.clear();
    }

    void rebuild() {
        Clock clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
        service = new QuizServiceImpl(quizRepository, quizQuestionRepository, ruleRepository, attemptRepository, answerRepository,
                mock(QuizAttemptEventRepository.class), questionRepository, questionService,
                mock(ClassRepository.class), mock(ClassStudentRepository.class), mock(StudentRepository.class),
                mock(LessonRepository.class), grading, access, json, mock(HtmlContentSanitizer.class), audit, clock);
    }

    static ClassEntity cls(long id) {
        ClassEntity c = new ClassEntity();
        c.setId(id);
        c.setClassName("Lớp " + id);
        return c;
    }

    // ------------------------------------------------------------------ AC-40 copy

    @SuppressWarnings("unchecked")
    @Test
    void copy_createsCleanDraft_QZ_C15_C16_C17() {
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(QuizQuestionEntity.builder()
                .quizId(1L).questionId(5L).points(BigDecimal.ONE).sortOrder(1).snapshot("{\"locked\":true}").build()));
        when(ruleRepository.findByQuizIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(QuizRuleEntity.builder().quizId(1L)
                .topic("t").difficulty(1).questionCount(3).pointsEach(BigDecimal.ONE).candidateSnapshot("[{\"id\":1}]").build()));
        var dto = service.copy(1L, 8L);
        ArgumentCaptor<QuizEntity> q = ArgumentCaptor.forClass(QuizEntity.class);
        verify(quizRepository).saveAndFlush(q.capture());
        QuizEntity c = q.getValue();
        assertThat(c.getClassId()).isEqualTo(8L);
        assertThat(c.getStatus()).isEqualTo("DRAFT");
        assertThat(c.getLessonId()).isNull();
        assertThat(c.getResultsReleasedAt()).isNull();
        assertThat(c.getClosedAt()).isNull();
        assertThat(c.getTitle()).isEqualTo("KT");
        assertThat(c.getMaxAttempts()).isEqualTo(2);
        assertThat(c.getVersion()).isNull();
        ArgumentCaptor<List<QuizQuestionEntity>> qs = ArgumentCaptor.forClass(List.class);
        verify(quizQuestionRepository).saveAll(qs.capture());
        assertThat(qs.getValue()).singleElement().satisfies(x -> {
            assertThat(x.getQuizId()).isEqualTo(2L);
            assertThat(x.getSnapshot()).isNull();
        });
        ArgumentCaptor<List<QuizRuleEntity>> rs = ArgumentCaptor.forClass(List.class);
        verify(ruleRepository).saveAll(rs.capture());
        assertThat(rs.getValue()).singleElement().satisfies(x -> assertThat(x.getCandidateSnapshot()).isNull());
        verify(attemptRepository, never()).save(any());
        verify(attemptRepository, never()).saveAll(anyList());
        verify(audit).success(eq("QUIZ_COPIED"), any(), any(), any());
        assertThat(dto.getStatus()).isEqualTo("DRAFT");
        assertThat(quiz.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void copy_forbiddenWhenNotTeachingSource_QZ_C18() {
        when(access.requireClassScope(7L)).thenThrow(new ForbiddenException("NOT_CLASS_TEACHER", "x"));
        assertThatThrownBy(() -> service.copy(1L, 8L)).isInstanceOf(ForbiddenException.class);
        verify(quizRepository, never()).saveAndFlush(any());
    }

    @Test
    void copy_forbiddenWhenNotTeachingTarget_QZ_C19() {
        when(access.requireClassScope(8L)).thenThrow(new ForbiddenException("NOT_CLASS_TEACHER", "x"));
        assertThatThrownBy(() -> service.copy(1L, 8L)).isInstanceOf(ForbiddenException.class);
        verify(quizRepository, never()).saveAndFlush(any());
    }

    @Test
    void copy_missingTargetOrSource_is404_QZ_C20() {
        when(access.requireClassScope(99L)).thenThrow(new OracleBusinessException("CLASS_NOT_FOUND", "x"));
        assertThatThrownBy(() -> service.copy(1L, 99L)).extracting("errorCode").isEqualTo("CLASS_NOT_FOUND");
        assertThatThrownBy(() -> service.copy(404L, 8L)).extracting("errorCode").isEqualTo("QUIZ_NOT_FOUND");
        verify(quizRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------ publish / close

    @Test
    void publish_requiresAtLeastOneQuestion_QZ_C04() {
        quiz.setStatus("DRAFT");
        assertThatThrownBy(() -> service.publish(1L, 3L)).extracting("errorCode").isEqualTo("QUIZ_RULE_NOT_ENOUGH_QUESTIONS");
    }

    @Test
    void publish_versionMismatch_is409() {
        quiz.setStatus("DRAFT");
        assertThatThrownBy(() -> service.publish(1L, 2L))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void close_whileOpen_shortensDeadlines_QZ_C10() {
        when(attemptRepository.shortenDeadlines(1L, now)).thenReturn(2);
        service.close(1L, 3L);
        assertThat(quiz.getStatus()).isEqualTo("CLOSED");
        assertThat(quiz.getCloseAt()).isEqualTo(now);
        verify(attemptRepository).shortenDeadlines(1L, now);
        verify(audit).success(eq("QUIZ_CLOSED_EARLY"), any(), any(), any());
    }

    @Test
    void close_beforeOpen_keepsTimesAndTouchesNoAttempt_QZ_C11() {
        now = OPEN.minusHours(1);
        rebuild();
        service.close(1L, 3L);
        assertThat(quiz.getStatus()).isEqualTo("CLOSED");
        assertThat(quiz.getCloseAt()).isEqualTo(OPEN.plusHours(1));
        assertThat(QuizRules.portalListed(quiz, OPEN.plusMinutes(5))).isFalse();
        verify(attemptRepository, never()).shortenDeadlines(any(), any());
    }

    @Test
    void close_draft_isLocked_QZ_C12() {
        quiz.setStatus("DRAFT");
        assertThatThrownBy(() -> service.close(1L, 3L)).extracting("errorCode").isEqualTo("QUIZ_LOCKED");
    }

    @Test
    void releaseResults_onlyManualAfterClose_QZ_G08_G10() {
        assertThatThrownBy(() -> service.releaseResults(1L)).extracting("errorCode").isEqualTo("QUIZ_LOCKED");
        now = OPEN.plusHours(2);
        rebuild();
        service.releaseResults(1L);
        assertThat(quiz.getResultsReleasedAt()).isEqualTo(now);
        quiz.setResultMode("IMMEDIATE");
        assertThatThrownBy(() -> service.releaseResults(1L)).extracting("errorCode").isEqualTo("QUIZ_LOCKED");
    }

    // ------------------------------------------------------------------ grade

    QuizAnswerEntity gradable(String type) {
        QuizAttemptEntity a = QuizAttemptEntity.builder().id(100L).quizId(1L).studentId(42L).status("SUBMITTED")
                .maxScore(BigDecimal.TEN).build();
        when(attemptRepository.findById(100L)).thenReturn(Optional.of(a));
        QuizJson.Snapshot s = new QuizJson.Snapshot(5L, type, "t", 1, "c", null, QuizRulesTest.single().options(), List.of(), null);
        QuizAnswerEntity ans = QuizAnswerEntity.builder().id(1L).attemptId(100L).questionId(5L).points(new BigDecimal("2"))
                .snapshot(json.write(s)).version(4L).build();
        when(answerRepository.findByAttemptIdAndQuestionId(100L, 5L)).thenReturn(Optional.of(ans));
        when(answerRepository.findByAttemptIdOrderBySortOrderAscIdAsc(100L)).thenReturn(List.of(ans));
        return ans;
    }

    static GradeQuizAnswerRequest req(String pts, String reason, long version) {
        return GradeQuizAnswerRequest.builder().pointsAwarded(new BigDecimal(pts)).reason(reason).expectedVersion(version).build();
    }

    @Test
    void grade_rangeStepReasonConflict_QZ_H03_H07() {
        gradable("SINGLE");
        assertThatThrownBy(() -> service.grade(100L, 5L, req("1", "ok lý do", 3L))).extracting("errorCode").isEqualTo("GRADE_CONFLICT");
        assertThatThrownBy(() -> service.grade(100L, 5L, req("2.5", "ok lý do", 4L)))
                .extracting("errorCode").isEqualTo("GRADE_SCORE_OUT_OF_RANGE");
        assertThatThrownBy(() -> service.grade(100L, 5L, req("-1", "ok lý do", 4L)))
                .extracting("errorCode").isEqualTo("GRADE_SCORE_OUT_OF_RANGE");
        assertThatThrownBy(() -> service.grade(100L, 5L, req("1.1", "ok lý do", 4L)))
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
        assertThatThrownBy(() -> service.grade(100L, 5L, req("1", "abc", 4L))).extracting("errorCode").isEqualTo("VALIDATION_ERROR");
        assertThatThrownBy(() -> service.grade(100L, 5L, req("1", null, 4L))).extracting("errorCode").isEqualTo("VALIDATION_ERROR");
        verify(answerRepository, never()).saveAndFlush(any());
    }

    @Test
    void grade_overrideAutoWithReason_recomputesAndAudits_QZ_H06() {
        QuizAnswerEntity ans = gradable("SINGLE");
        service.grade(100L, 5L, req("1.75", "Đáp án B cũng đúng", 4L));
        assertThat(ans.getPointsAwarded()).isEqualByComparingTo("1.75");
        assertThat(ans.getOverrideReason()).isEqualTo("Đáp án B cũng đúng");
        verify(attemptRepository).saveAndFlush(any());
    }

    @Test
    void grade_essayNoReasonNeeded_QZ_H01() {
        QuizAnswerEntity ans = gradable("ESSAY");
        service.grade(100L, 5L, req("1.5", null, 4L));
        assertThat(ans.getPointsAwarded()).isEqualByComparingTo("1.5");
        assertThat(ans.getOverrideReason()).isNull();
    }

    @Test
    void grade_inProgress_isLocked() {
        gradable("ESSAY");
        attemptRepository.findById(100L).get().setStatus("IN_PROGRESS");
        assertThatThrownBy(() -> service.grade(100L, 5L, req("1", null, 4L))).extracting("errorCode").isEqualTo("QUIZ_LOCKED");
    }

    @Test
    void grade_otherClassAttempt_403_QZ_J03() {
        gradable("ESSAY");
        when(access.requireClassScope(7L)).thenThrow(new ForbiddenException("NOT_CLASS_TEACHER", "x"));
        assertThatThrownBy(() -> service.grade(100L, 5L, req("1", null, 4L))).isInstanceOf(ForbiddenException.class);
    }

    // ------------------------------------------------------------------ CANDIDATE_SNAPSHOT (§9 #7)

    @SuppressWarnings("unchecked")
    @Test
    void publish_storesCandidateSnapshotOver4000Bytes_andRoundTrips() throws Exception {
        quiz.setStatus("DRAFT");
        when(ruleRepository.findByQuizIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(QuizRuleEntity.builder().quizId(1L)
                .topic("Hàm số").difficulty(2).questionCount(3).pointsEach(BigDecimal.ONE).build()));
        List<com.education.base.entity.QuestionEntity> bank = new java.util.ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            bank.add(com.education.base.entity.QuestionEntity.builder().id(i).questionType("SINGLE").topic("Hàm số")
                    .difficulty(2).isDeleted(0).content("<p>Câu " + i + ": đồ thị hàm số bậc nhất " + "ạ".repeat(150) + "</p>").build());
        }
        when(questionRepository.findCandidates(any(), any(), any())).thenReturn(bank);
        when(questionService.snapshot(any(), any())).thenAnswer(i -> {
            com.education.base.entity.QuestionEntity q = i.getArgument(0);
            return new QuizJson.Snapshot(q.getId(), q.getQuestionType(), q.getTopic(), q.getDifficulty(), q.getContent(),
                    "Giải thích", List.of(new QuizJson.SnapOption(q.getId() * 10, "Đúng", true),
                    new QuizJson.SnapOption(q.getId() * 10 + 1, "Sai", false)), List.of(), null);
        });
        service.publish(1L, 3L);
        ArgumentCaptor<List<QuizRuleEntity>> rs = ArgumentCaptor.forClass(List.class);
        verify(ruleRepository).saveAll(rs.capture());
        String stored = rs.getValue().get(0).getCandidateSnapshot();
        assertThat(stored.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isGreaterThan(4000);
        // JSON hợp lệ (CK_QR_CAND_JSON IS JSON) và đọc lại nguyên vẹn.
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(stored).isArray()).isTrue();
        List<QuizJson.Snapshot> back = json.snapshots(stored);
        assertThat(back).hasSize(20);
        assertThat(back.get(19).content()).isEqualTo(bank.get(19).getContent());
        assertThat(back.get(0).options()).extracting(QuizJson.SnapOption::correct).containsExactly(true, false);
        assertThat(quiz.getStatus()).isEqualTo("PUBLISHED");
    }
}
