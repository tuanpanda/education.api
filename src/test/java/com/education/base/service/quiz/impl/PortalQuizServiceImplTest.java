package com.education.base.service.quiz.impl;

import com.education.base.dto.request.PortalAttemptEventRequest;
import com.education.base.dto.request.PortalSaveAnswerRequest;
import com.education.base.dto.response.PortalAttemptDto;
import com.education.base.dto.response.PortalAttemptResultDto;
import com.education.base.dto.response.PortalQuizListItemDto;
import com.education.base.dto.response.PortalSubmitResultDto;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.QuizAnswerEntity;
import com.education.base.entity.QuizAttemptEntity;
import com.education.base.entity.QuizAttemptEventEntity;
import com.education.base.entity.QuizEntity;
import com.education.base.entity.QuizQuestionEntity;
import com.education.base.entity.QuizRuleEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.exception.RateLimitedException;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.QuizAnswerRepository;
import com.education.base.repository.QuizAttemptEventRepository;
import com.education.base.repository.QuizAttemptRepository;
import com.education.base.repository.QuizQuestionRepository;
import com.education.base.repository.QuizRepository;
import com.education.base.repository.QuizRuleRepository;
import com.education.base.security.PortalStudentContext;
import com.education.base.service.impl.StudentScopeGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** GĐ4 cổng HS: IDOR (AC-13), nộp lười (AC-39), ân hạn (AC-08/09), events (AC-13e), rate limit (AC-36), không lộ đáp án (AC-06). */
class PortalQuizServiceImplTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final LocalDateTime OPEN = LocalDateTime.of(2026, 10, 20, 8, 0);
    static final long ME = 42L;
    static final long OTHER = 43L;
    static final long CLASS_ID = 7L;

    QuizRepository quizRepository = mock(QuizRepository.class);
    QuizQuestionRepository quizQuestionRepository = mock(QuizQuestionRepository.class);
    QuizRuleRepository ruleRepository = mock(QuizRuleRepository.class);
    QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
    QuizAnswerRepository answerRepository = mock(QuizAnswerRepository.class);
    QuizAttemptEventRepository eventRepository = mock(QuizAttemptEventRepository.class);
    ClassRepository classRepository = mock(ClassRepository.class);
    ClassStudentRepository classStudentRepository = mock(ClassStudentRepository.class);
    PortalStudentContext ctx = mock(PortalStudentContext.class);
    StudentScopeGuard scope = mock(StudentScopeGuard.class);
    QuizGradingService grading = mock(QuizGradingService.class);
    QuizJson json = new QuizJson();
    LocalDateTime now = OPEN.plusMinutes(10);
    QuizSaveRateLimiter limiter;
    com.education.base.testsupport.InMemoryTransactionManager txManager = new com.education.base.testsupport.InMemoryTransactionManager();
    PortalQuizServiceImpl service;
    QuizEntity quiz;

    @BeforeEach
    void setUp() {
        rebuild();
        quiz = QuizEntity.builder().id(1L).classId(CLASS_ID).title("KT 15'").status("PUBLISHED").openAt(OPEN)
                .closeAt(OPEN.plusHours(1)).durationMin(30).maxAttempts(1).resultMode("IMMEDIATE").scorePolicy("HIGHEST")
                .shuffleQuestions(0).shuffleOptions(0).maxScore(new BigDecimal("2")).isDeleted(0).version(0L).build();
        when(ctx.requireCurrentStudentId()).thenReturn(ME);
        when(quizRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(quiz));
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(scope.isEnrolled(CLASS_ID)).thenReturn(true);
        when(grading.expireIfDue(any(), any())).thenAnswer(i -> i.getArgument(0));
        when(attemptRepository.saveAndFlush(any())).thenAnswer(i -> {
            QuizAttemptEntity a = i.getArgument(0);
            a.setId(100L);
            return a;
        });
    }

    void rebuild() {
        Clock clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
        limiter = new QuizSaveRateLimiter(clock);
        service = new PortalQuizServiceImpl(quizRepository, quizQuestionRepository, ruleRepository, attemptRepository,
                answerRepository, eventRepository, classRepository, classStudentRepository, ctx, scope, grading, limiter,
                json, clock, txManager);
        service.random = new Random(7);
    }

    void at(LocalDateTime t) {
        now = t;
        rebuild();
    }

    QuizAttemptEntity attempt(long id, long studentId, String status) {
        QuizAttemptEntity a = QuizAttemptEntity.builder().id(id).quizId(1L).studentId(studentId).attemptNo(1).status(status)
                .startedAt(OPEN).deadlineAt(OPEN.plusMinutes(30)).maxScore(new BigDecimal("2")).focusLostCount(0)
                .ipAddress("10.0.0.1").userAgent("UA").questionOrder("[]").version(0L).build();
        when(attemptRepository.findById(id)).thenReturn(Optional.of(a));
        return a;
    }

    QuizAnswerEntity answer(long attemptId, long questionId) {
        QuizJson.Snapshot s = QuizRulesTest.single();
        QuizAnswerEntity ans = QuizAnswerEntity.builder().id(attemptId * 10 + questionId).attemptId(attemptId).questionId(questionId)
                .points(BigDecimal.ONE).snapshot(json.write(new QuizJson.Snapshot(questionId, s.type(), s.topic(), s.difficulty(),
                        s.content(), s.explanation(), s.options(), s.acceptedAnswers(), BigDecimal.ONE))).sortOrder(0).version(0L).build();
        when(answerRepository.findByAttemptIdAndQuestionId(attemptId, questionId)).thenReturn(Optional.of(ans));
        return ans;
    }

    // ------------------------------------------------------------------ AC-13 IDOR -> 404

    @Test
    void otherClassQuiz_is404_andNoAttemptCreated_QZ_K01_K02() {
        when(scope.isEnrolled(CLASS_ID)).thenReturn(false);
        assertThatThrownBy(() -> service.get(1L)).extracting("errorCode").isEqualTo("QUIZ_NOT_FOUND");
        assertThatThrownBy(() -> service.start(1L, "ip", "ua")).extracting("errorCode").isEqualTo("QUIZ_NOT_FOUND");
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void draftOrFutureOrMissingQuiz_is404_QZ_K10_K14() {
        quiz.setStatus("DRAFT");
        assertThatThrownBy(() -> service.start(1L, "ip", "ua")).extracting("errorCode").isEqualTo("QUIZ_NOT_FOUND");
        quiz.setStatus("PUBLISHED");
        at(OPEN.minusMinutes(1));
        assertThatThrownBy(() -> service.get(1L)).extracting("errorCode").isEqualTo("QUIZ_NOT_FOUND");
        assertThatThrownBy(() -> service.get(9_999_999L)).extracting("errorCode").isEqualTo("QUIZ_NOT_FOUND");
    }

    @Test
    void foreignAttempt_everyEndpointIs404_QZ_K03_K07() {
        QuizAttemptEntity y = attempt(200L, OTHER, "IN_PROGRESS");
        answer(200L, 5L);
        PortalSaveAnswerRequest save = PortalSaveAnswerRequest.builder().selectedOptionIds(List.of(12L)).build();
        assertThatThrownBy(() -> service.getAttempt(200L)).extracting("errorCode").isEqualTo("QUIZ_ATTEMPT_NOT_FOUND");
        assertThatThrownBy(() -> service.saveAnswer(200L, 5L, save)).extracting("errorCode").isEqualTo("QUIZ_ATTEMPT_NOT_FOUND");
        assertThatThrownBy(() -> service.submit(200L)).extracting("errorCode").isEqualTo("QUIZ_ATTEMPT_NOT_FOUND");
        assertThatThrownBy(() -> service.result(200L)).extracting("errorCode").isEqualTo("QUIZ_ATTEMPT_NOT_FOUND");
        assertThatThrownBy(() -> service.getAttempt(9_999_999L)).extracting("errorCode").isEqualTo("QUIZ_ATTEMPT_NOT_FOUND");
        verify(answerRepository, never()).save(any());
        verify(grading, never()).finish(anyLong(), anyBoolean(), any());
        assertThat(y.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void questionNotInOwnAttempt_is404_QZ_K08_K09() {
        attempt(100L, ME, "IN_PROGRESS");
        when(answerRepository.findByAttemptIdAndQuestionId(100L, 77L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.saveAnswer(100L, 77L, new PortalSaveAnswerRequest()))
                .extracting("errorCode").isEqualTo("QUESTION_NOT_FOUND");
        verify(answerRepository, never()).save(any());
    }

    @Test
    void withdrawnStudent_ownAttemptIs404_QZ_E08() {
        attempt(100L, ME, "AUTO_SUBMITTED");
        when(scope.isEnrolled(CLASS_ID)).thenReturn(false);
        assertThatThrownBy(() -> service.result(100L)).extracting("errorCode").isEqualTo("QUIZ_ATTEMPT_NOT_FOUND");
    }

    // ------------------------------------------------------------------ events (AC-13e)

    @Test
    void events_foreignAttempt404EvenIfSubmitted_QZ_K06_K18() {
        QuizAttemptEntity y = attempt(200L, OTHER, "IN_PROGRESS");
        PortalAttemptEventRequest ev = PortalAttemptEventRequest.builder().type("FOCUS_LOST").build();
        assertThatThrownBy(() -> service.event(200L, ev, "ip", "ua")).extracting("errorCode").isEqualTo("QUIZ_ATTEMPT_NOT_FOUND");
        y.setStatus("SUBMITTED");
        assertThatThrownBy(() -> service.event(200L, ev, "ip", "ua")).extracting("errorCode").isEqualTo("QUIZ_ATTEMPT_NOT_FOUND");
        assertThat(y.getFocusLostCount()).isZero();
        verify(eventRepository, never()).save(any());
    }

    @Test
    void events_ownSubmittedAttemptIgnored_QZ_K19() {
        QuizAttemptEntity x = attempt(100L, ME, "SUBMITTED");
        service.event(100L, PortalAttemptEventRequest.builder().type("FOCUS_LOST").build(), "ip", "ua");
        assertThat(x.getFocusLostCount()).isZero();
        verify(eventRepository, never()).save(any());
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void events_countFocusLostAndFlagIpUaChange_noPenalty_QZ_I01_I02() {
        QuizAttemptEntity x = attempt(100L, ME, "IN_PROGRESS");
        PortalAttemptEventRequest ev = PortalAttemptEventRequest.builder().type("FOCUS_LOST").build();
        service.event(100L, ev, "10.0.0.1", "UA");
        service.event(100L, ev, "10.0.0.1", "UA");
        service.event(100L, ev, "10.9.9.9", "Other UA");
        assertThat(x.getFocusLostCount()).isEqualTo(3);
        assertThat(x.getFlags()).contains("IP_CHANGED").contains("UA_CHANGED");
        assertThat(x.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(x.getScore()).isNull();
    }

    // ------------------------------------------------------------------ start

    @Test
    void start_isIdempotent_QZ_D08() {
        QuizAttemptEntity x = attempt(100L, ME, "IN_PROGRESS");
        when(attemptRepository.findByQuizIdAndStudentIdOrderByAttemptNoAsc(1L, ME)).thenReturn(new ArrayList<>(List.of(x)));
        PortalAttemptDto dto = service.start(1L, "ip", "ua");
        assertThat(dto.getAttemptId()).isEqualTo(100L);
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void start_parallelRaceReturnsExisting_QZ_D08() {
        when(attemptRepository.findByQuizIdAndStudentIdOrderByAttemptNoAsc(1L, ME)).thenReturn(new ArrayList<>());
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(fixed(5L)));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("UX_QATT_ONE_IN_PROGRESS")).when(attemptRepository).saveAndFlush(any());
        QuizAttemptEntity existing = attempt(300L, ME, "IN_PROGRESS");
        when(attemptRepository.findFirstByQuizIdAndStudentIdAndStatus(1L, ME, "IN_PROGRESS")).thenReturn(Optional.of(existing));
        assertThat(service.start(1L, "ip", "ua").getAttemptId()).isEqualTo(300L);
    }

    @Test
    void start_concurrentUniqueViolation_insertInOwnTx_rereadsInFreshTx_BUG_GD4_2() {
        when(attemptRepository.findByQuizIdAndStudentIdOrderByAttemptNoAsc(1L, ME)).thenReturn(new ArrayList<>());
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(fixed(5L)));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("ORA-00001: UQ_QATT_QUIZ_STUDENT_NO"))
                .when(attemptRepository).saveAndFlush(any());
        QuizAttemptEntity existing = attempt(300L, ME, "IN_PROGRESS");
        when(attemptRepository.findFirstByQuizIdAndStudentIdAndStatus(1L, ME, "IN_PROGRESS")).thenReturn(Optional.of(existing));
        assertThat(service.start(1L, "ip", "ua").getAttemptId()).isEqualTo(300L);
        // insert chạy trong tx riêng (rollback), đọc lại trong tx riêng khác (commit); không ghi câu/event.
        assertThat(txManager.rollbacks).isEqualTo(1);
        assertThat(txManager.commits).isEqualTo(1);
        verify(answerRepository, never()).saveAll(anyList());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void start_uniqueViolationWithoutInProgress_rethrows_BUG_GD4_2() {
        when(attemptRepository.findByQuizIdAndStudentIdOrderByAttemptNoAsc(1L, ME)).thenReturn(new ArrayList<>());
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(fixed(5L)));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("x")).when(attemptRepository).saveAndFlush(any());
        when(attemptRepository.findFirstByQuizIdAndStudentIdAndStatus(1L, ME, "IN_PROGRESS")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.start(1L, "ip", "ua")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void start_success_writesAttemptAnswersEventInOneNewTx_BUG_GD4_2() {
        when(attemptRepository.findByQuizIdAndStudentIdOrderByAttemptNoAsc(1L, ME)).thenReturn(new ArrayList<>());
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(fixed(5L)));
        assertThat(service.start(1L, "ip", "ua").getAttemptId()).isEqualTo(100L);
        assertThat(txManager.commits).isEqualTo(1);
        assertThat(txManager.rollbacks).isZero();
        verify(answerRepository).saveAll(anyList());
    }

    @Test
    void start_noAttemptsLeft_QZ_D07() {
        QuizAttemptEntity done = attempt(100L, ME, "GRADED");
        when(attemptRepository.findByQuizIdAndStudentIdOrderByAttemptNoAsc(1L, ME)).thenReturn(new ArrayList<>(List.of(done)));
        assertThatThrownBy(() -> service.start(1L, "ip", "ua")).extracting("errorCode").isEqualTo("QUIZ_NO_ATTEMPTS_LEFT");
    }

    @Test
    void start_neverLeaksAnswers_andReturnsServerTime_QZ_D05_F01_D10() throws Exception {
        at(OPEN.plusMinutes(55));
        when(attemptRepository.findByQuizIdAndStudentIdOrderByAttemptNoAsc(1L, ME)).thenReturn(new ArrayList<>());
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(fixed(5L)));
        List<QuizAnswerEntity> saved = new ArrayList<>();
        when(answerRepository.saveAll(anyList())).thenAnswer(i -> {
            saved.addAll(i.getArgument(0));
            return i.getArgument(0);
        });
        when(answerRepository.findByAttemptIdOrderBySortOrderAscIdAsc(100L)).thenAnswer(i -> saved);
        PortalAttemptDto dto = service.start(1L, "10.0.0.1", "UA");
        assertThat(dto.getDeadlineAt()).isEqualTo(quiz.getCloseAt());
        assertThat(dto.getRemainingSeconds()).isEqualTo(300);
        assertThat(dto.getServerNow()).isEqualTo(now);
        assertThat(dto.getQuestions()).hasSize(1);
        String body = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writeValueAsString(dto);
        // Hợp đồng §0: ISO không múi giờ, giờ server Asia/Ho_Chi_Minh.
        assertThat(body).contains("\"serverNow\":\"2026-10-20T08:55:00\"").doesNotContain("+07:00");
        assertThat(body).doesNotContain("isCorrect").doesNotContain("correct\"").doesNotContain("acceptedAnswers")
                .doesNotContain("explanation").doesNotContain("correctOptionIds").doesNotContain("candidateSnapshot").doesNotContain("CANDIDATE_SNAPSHOT").doesNotContain("Giải thích");
        verify(eventRepository).save(any(QuizAttemptEventEntity.class));
    }

    @Test
    void start_rulesDrawFromLockedCandidates_perStudent_QZ_D04() {
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());
        List<QuizJson.Snapshot> cands = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            cands.add(new QuizJson.Snapshot(i, "SINGLE", "t", 1, "c" + i, null, QuizRulesTest.single().options(), List.of(), null));
        }
        when(ruleRepository.findByQuizIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(QuizRuleEntity.builder().quizId(1L)
                .topic("t").difficulty(1).questionCount(3).pointsEach(new BigDecimal("0.5")).candidateSnapshot(json.write(cands)).build()));
        List<QuizJson.Snapshot> paper = service.buildPaper(quiz);
        assertThat(paper).hasSize(3).allMatch(s -> s.points().compareTo(new BigDecimal("0.5")) == 0)
                .allMatch(s -> s.id() >= 1 && s.id() <= 10);
        assertThat(paper.stream().map(QuizJson.Snapshot::id).distinct().count()).isEqualTo(3);
    }

    @Test
    void shuffle_isDeterministicPerSeed_QZ_D01() {
        quiz.setShuffleQuestions(1);
        List<QuizQuestionEntity> qs = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            qs.add(fixed(i));
        }
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(1L)).thenReturn(qs);
        service.random = new Random(1);
        List<Long> a = service.buildPaper(quiz).stream().map(QuizJson.Snapshot::id).toList();
        service.random = new Random(1);
        List<Long> a2 = service.buildPaper(quiz).stream().map(QuizJson.Snapshot::id).toList();
        service.random = new Random(2);
        List<Long> b = service.buildPaper(quiz).stream().map(QuizJson.Snapshot::id).toList();
        assertThat(a).isEqualTo(a2).isNotEqualTo(b);
    }

    // ------------------------------------------------------------------ save / grace / lazy submit

    @Test
    void save_withinGrace_isAccepted_QZ_E02_E06() {
        at(OPEN.plusMinutes(30).plusSeconds(20));
        attempt(100L, ME, "IN_PROGRESS");
        QuizAnswerEntity ans = answer(100L, 5L);
        var r = service.saveAnswer(100L, 5L, PortalSaveAnswerRequest.builder().selectedOptionIds(List.of(12L)).build());
        assertThat(r.getRemainingSeconds()).isZero();
        assertThat(ans.getSelectedOptionIds()).isEqualTo("[12]");
        verify(grading, never()).finish(anyLong(), anyBoolean(), any());
    }

    @Test
    void save_afterGrace_expiredAndLazySubmitted_QZ_E03_E12() {
        at(OPEN.plusMinutes(30).plusSeconds(31));
        attempt(100L, ME, "IN_PROGRESS");
        QuizAnswerEntity ans = answer(100L, 5L);
        assertThatThrownBy(() -> service.saveAnswer(100L, 5L,
                PortalSaveAnswerRequest.builder().selectedOptionIds(List.of(12L)).build()))
                .extracting("errorCode").isEqualTo("QUIZ_ATTEMPT_EXPIRED");
        verify(grading).finish(eq(100L), eq(true), any());
        assertThat(ans.getSelectedOptionIds()).isNull();
    }

    @Test
    void save_onSubmittedAttempt_QZ_D11() {
        attempt(100L, ME, "SUBMITTED");
        answer(100L, 5L);
        assertThatThrownBy(() -> service.saveAnswer(100L, 5L, new PortalSaveAnswerRequest()))
                .extracting("errorCode").isEqualTo("QUIZ_ATTEMPT_SUBMITTED");
    }

    @Test
    void save_rejectsForeignOptionAndMultipleForSingle() {
        attempt(100L, ME, "IN_PROGRESS");
        answer(100L, 5L);
        assertThatThrownBy(() -> service.saveAnswer(100L, 5L,
                PortalSaveAnswerRequest.builder().selectedOptionIds(List.of(999L)).build()))
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
        assertThatThrownBy(() -> service.saveAnswer(100L, 5L,
                PortalSaveAnswerRequest.builder().selectedOptionIds(List.of(11L, 12L)).build()))
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void submit_afterGrace_isLazy200AutoSubmitted_notExpired_QZ_E11_AC39() {
        at(OPEN.plusMinutes(30).plusSeconds(31));
        QuizAttemptEntity a = attempt(100L, ME, "IN_PROGRESS");
        when(grading.finish(eq(100L), eq(true), any())).thenAnswer(i -> {
            a.setStatus("AUTO_SUBMITTED");
            a.setSubmittedAt(now);
            a.setScore(BigDecimal.ONE);
            return a;
        });
        PortalSubmitResultDto r = service.submit(100L);
        assertThat(r.getStatus()).isEqualTo("AUTO_SUBMITTED");
        assertThat(r.getRemainingSeconds()).isZero();
        assertThat(r.getScore()).isEqualByComparingTo("1");
        verify(grading).finish(eq(100L), eq(true), any());
    }

    @Test
    void submit_again_isIdempotent_noRegrade_QZ_E13_D09() {
        QuizAttemptEntity a = attempt(100L, ME, "AUTO_SUBMITTED");
        a.setSubmittedAt(OPEN.plusMinutes(31));
        PortalSubmitResultDto r = service.submit(100L);
        assertThat(r.getStatus()).isEqualTo("AUTO_SUBMITTED");
        assertThat(r.getSubmittedAt()).isEqualTo(OPEN.plusMinutes(31));
        verify(grading, never()).finish(anyLong(), anyBoolean(), any());
    }

    @Test
    void submit_inTime_isManualSubmit_QZ_F04() {
        QuizAttemptEntity a = attempt(100L, ME, "IN_PROGRESS");
        when(grading.finish(eq(100L), eq(false), any())).thenAnswer(i -> {
            a.setStatus("GRADED");
            return a;
        });
        assertThat(service.submit(100L).getRemainingSeconds()).isZero();
        verify(grading).finish(eq(100L), eq(false), any());
    }

    @Test
    void submit_afterCloseHidesScoreUntilReleased_AFTER_CLOSE() {
        quiz.setResultMode("AFTER_CLOSE");
        QuizAttemptEntity a = attempt(100L, ME, "GRADED");
        a.setScore(BigDecimal.ONE);
        PortalSubmitResultDto r = service.submit(100L);
        assertThat(r.getScoreReleased()).isFalse();
        assertThat(r.getScore()).isNull();
    }

    // ------------------------------------------------------------------ result (AC-12)

    @Test
    void result_immediateWhileOpen_scoreButNoAnswers_QZ_G06() {
        QuizAttemptEntity a = attempt(100L, ME, "GRADED");
        a.setScore(BigDecimal.ONE);
        when(answerRepository.findByAttemptIdOrderBySortOrderAscIdAsc(100L)).thenReturn(List.of(gradedAnswer()));
        PortalAttemptResultDto r = service.result(100L);
        assertThat(r.getScoreReleased()).isTrue();
        assertThat(r.getScore10()).isEqualByComparingTo("5");
        assertThat(r.getAnswersReleased()).isFalse();
        assertThat(r.getQuestions()).isNull();
    }

    @Test
    void result_afterClose_hasAnswers_QZ_G07() {
        at(OPEN.plusHours(2));
        QuizAttemptEntity a = attempt(100L, ME, "GRADED");
        a.setScore(BigDecimal.ONE);
        when(answerRepository.findByAttemptIdOrderBySortOrderAscIdAsc(100L)).thenReturn(List.of(gradedAnswer()));
        PortalAttemptResultDto r = service.result(100L);
        assertThat(r.getAnswersReleased()).isTrue();
        assertThat(r.getQuestions()).hasSize(1);
        assertThat(r.getQuestions().get(0).getCorrectOptionIds()).containsExactly(12L);
        assertThat(r.getQuestions().get(0).getExplanation()).isEqualTo("e");
    }

    @Test
    void result_manualBeforeRelease_hidden_QZ_G09_H02() {
        at(OPEN.plusHours(2));
        quiz.setResultMode("MANUAL");
        attempt(100L, ME, "SUBMITTED");
        when(answerRepository.findByAttemptIdOrderBySortOrderAscIdAsc(100L)).thenReturn(List.of(gradedAnswer()));
        PortalAttemptResultDto r = service.result(100L);
        assertThat(r.getScoreReleased()).isFalse();
        assertThat(r.getScore()).isNull();
        assertThat(r.getAnswersReleased()).isFalse();
    }

    // ------------------------------------------------------------------ list (AC-13i)

    @Test
    void list_hidesClosedNeverOpenedAndBadTab_QZ_K12() {
        ClassStudentEntity e = new ClassStudentEntity();
        e.setClassId(CLASS_ID);
        e.setStatus("ENROLLED");
        when(classStudentRepository.findByStudentIdAndIsDeleted(ME, 0)).thenReturn(List.of(e));
        QuizEntity closedEarly = QuizEntity.builder().id(2L).classId(CLASS_ID).title("x").status("CLOSED")
                .openAt(OPEN.plusDays(1)).closeAt(OPEN.plusDays(2)).closedAt(OPEN).maxAttempts(1).isDeleted(0).build();
        when(quizRepository.findByClassIdInAndIsDeletedAndStatusIn(any(), eq(0), any())).thenReturn(List.of(quiz, closedEarly));
        when(classRepository.findAllById(any())).thenReturn(List.of());
        List<PortalQuizListItemDto> open = service.list(null);
        assertThat(open).extracting(PortalQuizListItemDto::getId).containsExactly(1L);
        assertThat(service.list("DONE")).isEmpty();
        assertThat(service.list("UPCOMING")).isEmpty();
        assertThatThrownBy(() -> service.list("xyz")).extracting("errorCode").isEqualTo("VALIDATION_ERROR");
    }

    // ------------------------------------------------------------------ rate limit (AC-36)

    @Test
    void rateLimit_61stSaveIs429_perAttempt_QZ_L01_L04() {
        attempt(100L, ME, "IN_PROGRESS");
        answer(100L, 5L);
        PortalSaveAnswerRequest r = PortalSaveAnswerRequest.builder().selectedOptionIds(List.of(12L)).build();
        for (int i = 0; i < 60; i++) {
            service.saveAnswer(100L, 5L, r);
        }
        assertThatThrownBy(() -> service.saveAnswer(100L, 5L, r)).isInstanceOf(RateLimitedException.class)
                .satisfies(ex -> assertThat(((RateLimitedException) ex).getRetryAfterSeconds()).isGreaterThanOrEqualTo(1));
        // attempt khác của HS khác không bị ảnh hưởng
        when(ctx.requireCurrentStudentId()).thenReturn(OTHER);
        attempt(200L, OTHER, "IN_PROGRESS");
        answer(200L, 5L);
        assertThat(service.saveAnswer(200L, 5L, r).getQuestionId()).isEqualTo(5L);
    }

    // ------------------------------------------------------------------ QZ-C24: không lộ đáp án trước CLOSE_AT

    static final java.util.Set<String> SECRET = java.util.Set.of("correct", "isCorrect", "correctOptionIds",
            "acceptedAnswers", "explanation");

    static void assertNoSecrets(Object dto) throws Exception {
        com.fasterxml.jackson.databind.JsonNode tree = new ObjectMapper().registerModule(new JavaTimeModule()).valueToTree(dto);
        List<String> found = new ArrayList<>();
        scan(tree, "$", found);
        assertThat(found).as("lộ đáp án").isEmpty();
    }

    static void scan(com.fasterxml.jackson.databind.JsonNode n, String path, List<String> found) {
        if (n.isObject()) {
            n.fields().forEachRemaining(e -> {
                if (SECRET.contains(e.getKey()) && !e.getValue().isNull()) {
                    found.add(path + "." + e.getKey());
                }
                scan(e.getValue(), path + "." + e.getKey(), found);
            });
        } else if (n.isArray()) {
            for (int i = 0; i < n.size(); i++) {
                scan(n.get(i), path + "[" + i + "]", found);
            }
        }
    }

    @Test
    void noPortalResponseLeaksAnswersBeforeClose_inAnyResultMode_QZ_C24() throws Exception {
        for (String mode : List.of("IMMEDIATE", "AFTER_CLOSE", "MANUAL")) {
            at(OPEN.plusMinutes(59).plusSeconds(59));
            quiz.setResultMode(mode);
            quiz.setResultsReleasedAt("MANUAL".equals(mode) ? OPEN.plusMinutes(30) : null);
            // list + detail + start + getAttempt + save
            ClassStudentEntity e = new ClassStudentEntity();
            e.setClassId(CLASS_ID);
            e.setStatus("ENROLLED");
            when(classStudentRepository.findByStudentIdAndIsDeleted(ME, 0)).thenReturn(List.of(e));
            when(quizRepository.findByClassIdInAndIsDeletedAndStatusIn(any(), eq(0), any())).thenReturn(List.of(quiz));
            when(classRepository.findAllById(any())).thenReturn(List.of());
            when(attemptRepository.findByQuizIdAndStudentIdOrderByAttemptNoAsc(1L, ME)).thenReturn(new ArrayList<>());
            when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(fixed(5L)));
            List<QuizAnswerEntity> saved = new ArrayList<>();
            when(answerRepository.saveAll(anyList())).thenAnswer(i -> {
                saved.clear();
                saved.addAll(i.getArgument(0));
                return i.getArgument(0);
            });
            when(answerRepository.findByAttemptIdOrderBySortOrderAscIdAsc(100L)).thenAnswer(i -> saved);
            assertNoSecrets(service.list(null));
            assertNoSecrets(service.get(1L));
            assertNoSecrets(service.start(1L, "ip", "ua"));
            QuizAttemptEntity a = attempt(100L, ME, "IN_PROGRESS");
            a.setDeadlineAt(OPEN.plusHours(1));
            assertNoSecrets(service.getAttempt(100L));
            answer(100L, 5L);
            assertNoSecrets(service.saveAnswer(100L, 5L, PortalSaveAnswerRequest.builder().selectedOptionIds(List.of(12L)).build()));
            // submit + result của lượt đã chấm, vẫn trước CLOSE_AT
            a.setStatus("GRADED");
            a.setScore(BigDecimal.ONE);
            when(answerRepository.findByAttemptIdOrderBySortOrderAscIdAsc(100L)).thenReturn(List.of(gradedAnswer()));
            assertNoSecrets(service.submit(100L));
            PortalAttemptResultDto r = service.result(100L);
            assertThat(r.getAnswersReleased()).as(mode).isFalse();
            assertNoSecrets(r);
            // Đúng CLOSE_AT: chỉ công bố khi chế độ cho phép điểm (MANUAL đã công bố / IMMEDIATE / AFTER_CLOSE).
            at(OPEN.plusHours(1));
            assertThat(service.result(100L).getAnswersReleased()).as(mode).isTrue();
        }
    }

    @Test
    void manualNotReleased_neverLeaksEvenAfterClose_QZ_C24() throws Exception {
        at(OPEN.plusHours(3));
        quiz.setResultMode("MANUAL");
        QuizAttemptEntity a = attempt(100L, ME, "GRADED");
        a.setScore(BigDecimal.ONE);
        when(answerRepository.findByAttemptIdOrderBySortOrderAscIdAsc(100L)).thenReturn(List.of(gradedAnswer()));
        assertNoSecrets(service.result(100L));
        assertNoSecrets(service.submit(100L));
    }

    // ------------------------------------------------------------------ data

    QuizQuestionEntity fixed(long questionId) {
        QuizJson.Snapshot s = QuizRulesTest.single();
        return QuizQuestionEntity.builder().quizId(1L).questionId(questionId).points(BigDecimal.ONE).sortOrder((int) questionId)
                .snapshot(json.write(new QuizJson.Snapshot(questionId, s.type(), s.topic(), s.difficulty(), "Câu " + questionId,
                        "Giải thích", s.options(), List.of(), null))).build();
    }

    QuizAnswerEntity gradedAnswer() {
        QuizAnswerEntity a = QuizAnswerEntity.builder().attemptId(100L).questionId(5L).points(BigDecimal.ONE)
                .snapshot(json.write(QuizRulesTest.single())).selectedOptionIds("[12]").isCorrect(1)
                .pointsAwarded(BigDecimal.ONE).questionId(5L).build();
        return a;
    }
}
