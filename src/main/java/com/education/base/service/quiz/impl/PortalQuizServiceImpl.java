package com.education.base.service.quiz.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.quiz.QuizConstants;
import com.education.base.common.quiz.QuizConstants.AttemptStatus;
import com.education.base.common.quiz.QuizConstants.EventType;
import com.education.base.common.quiz.QuizConstants.Flag;
import com.education.base.common.quiz.QuizConstants.PortalTab;
import com.education.base.common.quiz.QuizConstants.QuestionType;
import com.education.base.common.quiz.QuizConstants.QuizStatus;
import com.education.base.common.quiz.QuizErrorCodes;
import com.education.base.dto.request.PortalAttemptEventRequest;
import com.education.base.dto.request.PortalSaveAnswerRequest;
import com.education.base.dto.response.PortalAttemptDto;
import com.education.base.dto.response.PortalAttemptResultDto;
import com.education.base.dto.response.PortalQuizDetailDto;
import com.education.base.dto.response.PortalQuizListItemDto;
import com.education.base.dto.response.PortalSaveAnswerDto;
import com.education.base.dto.response.PortalSubmitResultDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.QuizAnswerEntity;
import com.education.base.entity.QuizAttemptEntity;
import com.education.base.entity.QuizAttemptEventEntity;
import com.education.base.entity.QuizEntity;
import com.education.base.entity.QuizQuestionEntity;
import com.education.base.entity.QuizRuleEntity;
import com.education.base.exception.OracleBusinessException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * GĐ4 - cổng học sinh ({@code /api/v1/portal/me}). HS lấy từ {@link PortalStudentContext}; mọi thứ không thuộc HS
 * (đề lớp khác, DRAFT, chưa mở, attempt HS khác, câu ngoài attempt) -> 404, không 403. Hợp đồng §3.
 */
@Service
@RequiredArgsConstructor
public class PortalQuizServiceImpl {

    private static final int IP_MAX = 64;
    private static final int UA_MAX = 500;

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizRuleRepository ruleRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAnswerRepository answerRepository;
    private final QuizAttemptEventRepository eventRepository;
    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
    private final PortalStudentContext portalStudentContext;
    private final StudentScopeGuard studentScopeGuard;
    private final QuizGradingService gradingService;
    private final QuizSaveRateLimiter rateLimiter;
    private final QuizJson json;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;
    /** Nguồn ngẫu nhiên bốc đề / xáo (test thay được). */
    Random random = new java.security.SecureRandom();

    // ============================================================================ danh sách / chi tiết

    @Transactional(rollbackFor = Exception.class)
    public List<PortalQuizListItemDto> list(String tab) {
        String t = tab == null || tab.isBlank() ? PortalTab.OPEN : tab.strip().toUpperCase(Locale.ROOT);
        if (!List.of(PortalTab.UPCOMING, PortalTab.OPEN, PortalTab.DONE).contains(t)) {
            throw new OracleBusinessException(QuizErrorCodes.VALIDATION_ERROR, "Tab không hợp lệ (UPCOMING|OPEN|DONE).");
        }
        Long studentId = portalStudentContext.requireCurrentStudentId();
        LocalDateTime now = QuizRules.now(clock);
        Set<Long> classIds = classStudentRepository.findByStudentIdAndIsDeleted(studentId, PersistenceFlags.NOT_DELETED)
                .stream().filter(e -> DomainConstants.ENROLLMENT_STATUS_ENROLLED.equals(e.getStatus()))
                .map(ClassStudentEntity::getClassId).collect(Collectors.toSet());
        if (classIds.isEmpty()) {
            return List.of();
        }
        List<QuizEntity> quizzes = quizRepository.findByClassIdInAndIsDeletedAndStatusIn(classIds, PersistenceFlags.NOT_DELETED,
                List.of(QuizStatus.PUBLISHED, QuizStatus.CLOSED)).stream().filter(q -> QuizRules.portalListed(q, now)).toList();
        if (quizzes.isEmpty()) {
            return List.of();
        }
        Map<Long, List<QuizAttemptEntity>> attempts = attemptsByQuiz(studentId, quizzes);
        Map<Long, ClassEntity> classes = classRepository.findAllById(quizzes.stream().map(QuizEntity::getClassId)
                .distinct().toList()).stream().collect(Collectors.toMap(ClassEntity::getId, Function.identity()));
        Map<Long, Integer> counts = questionCounts(quizzes.stream().map(QuizEntity::getId).toList());
        List<PortalQuizListItemDto> out = new ArrayList<>();
        for (QuizEntity q : quizzes) {
            List<QuizAttemptEntity> list = attempts.getOrDefault(q.getId(), new ArrayList<>());
            list = expireDue(list, now);
            PortalQuizListItemDto dto = toListItem(q, classes.get(q.getClassId()), list, counts.getOrDefault(q.getId(), 0), now);
            if (t.equals(dto.getTab())) {
                out.add(dto);
            }
        }
        Comparator<PortalQuizListItemDto> cmp = switch (t) {
            case PortalTab.UPCOMING -> Comparator.comparing(PortalQuizListItemDto::getOpenAt);
            case PortalTab.OPEN -> Comparator.comparing(PortalQuizListItemDto::getCloseAt);
            default -> Comparator.comparing(PortalQuizListItemDto::getCloseAt).reversed();
        };
        out.sort(cmp.thenComparing(PortalQuizListItemDto::getId));
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public PortalQuizDetailDto get(Long quizId) {
        LocalDateTime now = QuizRules.now(clock);
        QuizEntity q = requireStartedQuiz(quizId, now);
        Long studentId = portalStudentContext.requireCurrentStudentId();
        List<QuizAttemptEntity> list = expireDue(attemptRepository.findByQuizIdAndStudentIdOrderByAttemptNoAsc(q.getId(), studentId), now);
        ClassEntity c = classRepository.findById(q.getClassId()).orElse(null);
        PortalQuizListItemDto li = toListItem(q, c, list, questionCounts(List.of(q.getId())).getOrDefault(q.getId(), 0), now);
        boolean released = QuizRules.scoreReleased(q, now);
        List<PortalQuizDetailDto.AttemptItem> items = list.stream().map(a -> {
            boolean rel = released && QuizRules.finished(a);
            return PortalQuizDetailDto.AttemptItem.builder().attemptId(a.getId()).attemptNo(a.getAttemptNo())
                    .status(a.getStatus()).submittedAt(a.getSubmittedAt()).scoreReleased(rel)
                    .score10(rel ? QuizRules.score10(a.getScore(), a.getMaxScore()) : null).build();
        }).toList();
        return PortalQuizDetailDto.builder().id(li.getId()).title(li.getTitle()).className(li.getClassName())
                .subjectName(li.getSubjectName()).openAt(li.getOpenAt()).closeAt(li.getCloseAt())
                .durationMin(li.getDurationMin()).questionCount(li.getQuestionCount()).maxAttempts(li.getMaxAttempts())
                .attemptsUsed(li.getAttemptsUsed()).tab(li.getTab()).inProgressAttemptId(li.getInProgressAttemptId())
                .canStart(li.getCanStart()).scoreReleased(li.getScoreReleased()).finalScore10(li.getFinalScore10())
                .description(q.getDescription()).resultMode(q.getResultMode()).serverNow(now).attempts(items).build();
    }

    // ============================================================================ làm bài

    /** Bắt đầu (idempotent: đang có IN_PROGRESS -> trả lại). Hết lượt -> QUIZ_NO_ATTEMPTS_LEFT. */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = OracleBusinessException.class)
    public PortalAttemptDto start(Long quizId, String ip, String userAgent) {
        LocalDateTime now = QuizRules.now(clock);
        QuizEntity q = requireStartedQuiz(quizId, now);
        Long studentId = portalStudentContext.requireCurrentStudentId();
        List<QuizAttemptEntity> list = expireDue(attemptRepository.findByQuizIdAndStudentIdOrderByAttemptNoAsc(q.getId(), studentId), now);
        QuizAttemptEntity current = list.stream().filter(a -> AttemptStatus.IN_PROGRESS.equals(a.getStatus())).findFirst().orElse(null);
        if (current != null) {
            return toAttemptDto(current, q, now);
        }
        if (!QuizRules.isOpen(q, now)) {
            throw new OracleBusinessException(QuizErrorCodes.QUIZ_LOCKED, "Bài kiểm tra đã đóng.");
        }
        if (list.size() >= q.getMaxAttempts()) {
            throw new OracleBusinessException(QuizErrorCodes.QUIZ_NO_ATTEMPTS_LEFT, "Em đã dùng hết số lần làm bài.");
        }
        List<QuizJson.Snapshot> paper = buildPaper(q);
        QuizAttemptEntity a = QuizAttemptEntity.builder().quizId(q.getId()).studentId(studentId)
                .attemptNo(list.stream().mapToInt(QuizAttemptEntity::getAttemptNo).max().orElse(0) + 1)
                .status(AttemptStatus.IN_PROGRESS).startedAt(now).deadlineAt(QuizRules.deadline(now, q))
                .maxScore(paper.stream().map(QuizJson.Snapshot::points).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
                .ipAddress(cut(ip, IP_MAX)).userAgent(cut(userAgent, UA_MAX)).focusLostCount(0).createdAt(now)
                .createdBy("STUDENT:" + studentId).build();
        List<QuizJson.PaperItem> order = new ArrayList<>();
        for (QuizJson.Snapshot s : paper) {
            List<Long> opts = new ArrayList<>(s.options() == null ? List.of() : s.options().stream().map(QuizJson.SnapOption::id).toList());
            if (QuizRules.flag(q.getShuffleOptions()) && !QuestionType.TRUE_FALSE.equals(s.type())) {
                Collections.shuffle(opts, random);
            }
            order.add(new QuizJson.PaperItem(s.id(), opts));
        }
        a.setQuestionOrder(json.write(order));
        // Ghi attempt + câu + event trong transaction RIÊNG (REQUIRES_NEW): nếu request song song đã chèn trước
        // (UQ_QATT_QUIZ_STUDENT_NO / UX_QATT_ONE_IN_PROGRESS) thì chỉ transaction con rollback, transaction ngoài vẫn
        // dùng được để đọc lại attempt đang làm và trả cùng attemptId (không 500).
        QuizAttemptEntity saved;
        try {
            saved = newTx().execute(st -> insertAttempt(a, paper, now));
        } catch (DataIntegrityViolationException e) {
            QuizAttemptEntity existing = newTx().execute(st -> attemptRepository.findFirstByQuizIdAndStudentIdAndStatus(
                    q.getId(), studentId, AttemptStatus.IN_PROGRESS).orElse(null));
            if (existing == null) {
                throw e;
            }
            return toAttemptDto(existing, q, now);
        }
        return toAttemptDto(saved, q, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public PortalAttemptDto getAttempt(Long attemptId) {
        LocalDateTime now = QuizRules.now(clock);
        QuizAttemptEntity a = gradingService.expireIfDue(requireOwnAttempt(attemptId), now);
        QuizEntity q = quizOf(a);
        return toAttemptDto(a, q, now);
    }

    /**
     * Lưu 1 câu. Thứ tự: (1) attempt/câu thuộc HS (404); (2) rate limit (429); (3) đã nộp -> QUIZ_ATTEMPT_SUBMITTED;
     * quá DEADLINE_AT + GRACE_SEC -> nộp lười + QUIZ_ATTEMPT_EXPIRED (nộp lười vẫn được commit).
     */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = OracleBusinessException.class)
    public PortalSaveAnswerDto saveAnswer(Long attemptId, Long questionId, PortalSaveAnswerRequest r) {
        QuizAttemptEntity a = requireOwnAttempt(attemptId);
        QuizAnswerEntity ans = answerRepository.findByAttemptIdAndQuestionId(a.getId(), questionId)
                .orElseThrow(PortalQuizServiceImpl::questionNotFound);
        rateLimiter.acquire(a.getId());
        LocalDateTime now = QuizRules.now(clock);
        if (QuizRules.finished(a)) {
            throw submitted();
        }
        if (QuizRules.overdue(a, now)) {
            gradingService.finish(a.getId(), true, now);
            throw new OracleBusinessException(QuizErrorCodes.QUIZ_ATTEMPT_EXPIRED, "Đã hết giờ làm bài.");
        }
        QuizJson.Snapshot s = json.snapshot(ans.getSnapshot());
        List<Long> selected = r.getSelectedOptionIds() == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(r.getSelectedOptionIds().stream().filter(Objects::nonNull).toList()));
        String text = r.getTextAnswer();
        validateAnswer(s, selected, text);
        boolean textType = QuestionType.SHORT_TEXT.equals(s.type()) || QuestionType.ESSAY.equals(s.type());
        ans.setSelectedOptionIds(textType ? null : json.write(selected));
        ans.setTextAnswer(textType ? (text == null ? null : text) : null);
        ans.setSavedAt(now);
        answerRepository.save(ans);
        return PortalSaveAnswerDto.builder().questionId(questionId).savedAt(now).serverNow(now)
                .remainingSeconds(QuizRules.remainingSeconds(a.getDeadlineAt(), now)).build();
    }

    /** Nộp (idempotent). Quá hạn + ân hạn mà job chưa chạy -> nộp lười, 200 AUTO_SUBMITTED (AC-39). */
    @Transactional(rollbackFor = Exception.class)
    public PortalSubmitResultDto submit(Long attemptId) {
        QuizAttemptEntity a = requireOwnAttempt(attemptId);
        LocalDateTime now = QuizRules.now(clock);
        if (AttemptStatus.IN_PROGRESS.equals(a.getStatus())) {
            a = gradingService.finish(a.getId(), QuizRules.overdue(a, now), now);
        }
        QuizEntity q = quizOf(a);
        boolean released = QuizRules.scoreReleased(q, now);
        boolean pending = QuizGradingService.pendingEssay(answerRepository.findByAttemptIdOrderBySortOrderAscIdAsc(a.getId()));
        return PortalSubmitResultDto.builder().attemptId(a.getId()).status(a.getStatus()).submittedAt(a.getSubmittedAt())
                .serverNow(now).remainingSeconds(0L).scoreReleased(released)
                .score(released ? a.getScore() : null).maxScore(released ? a.getMaxScore() : null)
                .score10(released ? QuizRules.score10(a.getScore(), a.getMaxScore()) : null).pendingEssay(pending).build();
    }

    @Transactional(rollbackFor = Exception.class)
    public PortalAttemptResultDto result(Long attemptId) {
        LocalDateTime now = QuizRules.now(clock);
        QuizAttemptEntity a = gradingService.expireIfDue(requireOwnAttempt(attemptId), now);
        QuizEntity q = quizOf(a);
        if (!QuizRules.finished(a)) {
            return PortalAttemptResultDto.builder().attemptId(a.getId()).status(a.getStatus()).scoreReleased(false)
                    .pendingEssay(false).answersReleased(false).build();
        }
        List<QuizAnswerEntity> answers = answerRepository.findByAttemptIdOrderBySortOrderAscIdAsc(a.getId());
        boolean pending = QuizGradingService.pendingEssay(answers);
        boolean scoreRel = QuizRules.scoreReleased(q, now);
        boolean ansRel = QuizRules.answersReleased(q, now);
        PortalAttemptResultDto.PortalAttemptResultDtoBuilder b = PortalAttemptResultDto.builder().attemptId(a.getId())
                .status(a.getStatus()).scoreReleased(scoreRel).pendingEssay(pending).answersReleased(ansRel);
        if (scoreRel) {
            b.score(a.getScore()).maxScore(a.getMaxScore()).score10(QuizRules.score10(a.getScore(), a.getMaxScore()))
                    .passed(q.getPassScore() == null || a.getScore() == null ? null : a.getScore().compareTo(q.getPassScore()) >= 0);
        }
        if (ansRel) {
            Map<Long, List<Long>> orders = json.paper(a.getQuestionOrder()).stream()
                    .collect(Collectors.toMap(QuizJson.PaperItem::questionId, QuizJson.PaperItem::optionOrder, (x, y) -> x));
            b.questions(answers.stream().map(x -> {
                QuizJson.Snapshot s = json.snapshot(x.getSnapshot());
                return PortalAttemptResultDto.Question.builder().questionId(x.getQuestionId()).content(s.content())
                        .type(s.type()).points(x.getPoints()).options(orderedOptions(s, orders.get(x.getQuestionId())).stream()
                                .map(o -> PortalAttemptResultDto.Option.builder().id(o.id()).content(o.content()).build()).toList())
                        .selectedOptionIds(json.longs(x.getSelectedOptionIds())).textAnswer(x.getTextAnswer())
                        .isCorrect(x.getIsCorrect() == null ? null : x.getIsCorrect() == 1).pointsAwarded(x.getPointsAwarded())
                        .correctOptionIds(s.options() == null ? List.of() : s.options().stream().filter(QuizJson.SnapOption::correct)
                                .map(QuizJson.SnapOption::id).toList())
                        .acceptedAnswers(s.acceptedAnswers() == null ? List.of() : s.acceptedAnswers())
                        .explanation(s.explanation()).graderComment(x.getGraderComment()).build();
            }).toList());
        }
        return b.build();
    }

    /** Sự kiện: (1) attempt thuộc HS, không -> 404; (2) không IN_PROGRESS -> bỏ qua 200; (3) ghi. Không trừ điểm. */
    @Transactional(rollbackFor = Exception.class)
    public void event(Long attemptId, PortalAttemptEventRequest r, String ip, String userAgent) {
        QuizAttemptEntity a = requireOwnAttempt(attemptId);
        LocalDateTime now = QuizRules.now(clock);
        if (!AttemptStatus.IN_PROGRESS.equals(a.getStatus())) {
            return;
        }
        if (QuizRules.overdue(a, now)) {
            gradingService.finish(a.getId(), true, now);
            return;
        }
        String type = r.getType().strip().toUpperCase(Locale.ROOT);
        eventRepository.save(QuizAttemptEventEntity.builder().attemptId(a.getId()).eventType(type).occurredAt(now)
                .ipAddress(cut(ip, IP_MAX)).userAgent(cut(userAgent, UA_MAX)).build());
        if (EventType.FOCUS_LOST.equals(type)) {
            a.setFocusLostCount((a.getFocusLostCount() == null ? 0 : a.getFocusLostCount()) + 1);
        }
        Set<String> flags = new LinkedHashSet<>(QuizServiceImpl.flagsOf(a));
        if (ip != null && a.getIpAddress() != null && !Objects.equals(cut(ip, IP_MAX), a.getIpAddress())) {
            flags.add(Flag.IP_CHANGED);
        }
        if (userAgent != null && a.getUserAgent() != null && !Objects.equals(cut(userAgent, UA_MAX), a.getUserAgent())) {
            flags.add(Flag.UA_CHANGED);
        }
        a.setFlags(flags.isEmpty() ? null : String.join(",", flags));
        attemptRepository.save(a);
    }

    // ============================================================================ helpers

    private TransactionTemplate newTx() {
        TransactionTemplate t = new TransactionTemplate(transactionManager);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return t;
    }

    private QuizAttemptEntity insertAttempt(QuizAttemptEntity a, List<QuizJson.Snapshot> paper, LocalDateTime now) {
        QuizAttemptEntity saved = attemptRepository.saveAndFlush(a);
        List<QuizAnswerEntity> answers = new ArrayList<>();
        int i = 0;
        for (QuizJson.Snapshot s : paper) {
            answers.add(QuizAnswerEntity.builder().attemptId(saved.getId()).questionId(s.id()).sortOrder(i++)
                    .points(s.points()).snapshot(json.write(s)).build());
        }
        answerRepository.saveAll(answers);
        eventRepository.save(QuizAttemptEventEntity.builder().attemptId(saved.getId()).eventType(EventType.STARTED)
                .occurredAt(now).ipAddress(saved.getIpAddress()).userAgent(saved.getUserAgent()).build());
        return saved;
    }

    /** Đề phải: chưa xoá, PUBLISHED/CLOSED đã từng mở, lớp HS đang ENROLLED, now >= OPEN_AT. Khác -> 404. */
    QuizEntity requireStartedQuiz(Long quizId, LocalDateTime now) {
        QuizEntity q = quizRepository.findByIdAndIsDeleted(quizId, PersistenceFlags.NOT_DELETED)
                .filter(x -> QuizRules.portalListed(x, now) && !now.isBefore(x.getOpenAt()))
                .orElseThrow(PortalQuizServiceImpl::quizNotFound);
        if (!studentScopeGuard.isEnrolled(q.getClassId())) {
            throw quizNotFound();
        }
        return q;
    }

    /** Attempt của chính HS + đề còn nhìn thấy được với HS. Khác -> 404 QUIZ_ATTEMPT_NOT_FOUND. */
    QuizAttemptEntity requireOwnAttempt(Long attemptId) {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        QuizAttemptEntity a = attemptRepository.findById(attemptId)
                .filter(x -> Objects.equals(x.getStudentId(), studentId))
                .orElseThrow(PortalQuizServiceImpl::attemptNotFound);
        QuizEntity q = quizRepository.findByIdAndIsDeleted(a.getQuizId(), PersistenceFlags.NOT_DELETED)
                .orElseThrow(PortalQuizServiceImpl::attemptNotFound);
        if (!studentScopeGuard.isEnrolled(q.getClassId())) {
            throw attemptNotFound();
        }
        return a;
    }

    private QuizEntity quizOf(QuizAttemptEntity a) {
        return quizRepository.findById(a.getQuizId()).orElseThrow(PortalQuizServiceImpl::attemptNotFound);
    }

    /** Bốc đề riêng cho HS (BR-15): câu chọn tay (SNAPSHOT) + mỗi quy tắc bốc từ CANDIDATE_SNAPSHOT đã khoá. */
    List<QuizJson.Snapshot> buildPaper(QuizEntity q) {
        List<QuizJson.Snapshot> paper = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (QuizQuestionEntity x : quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(q.getId())) {
            QuizJson.Snapshot s = json.snapshot(x.getSnapshot());
            if (s != null && used.add(s.id())) {
                paper.add(s.withPoints(x.getPoints()));
            }
        }
        for (QuizRuleEntity rule : ruleRepository.findByQuizIdOrderBySortOrderAscIdAsc(q.getId())) {
            List<QuizJson.Snapshot> cands = new ArrayList<>(json.snapshots(rule.getCandidateSnapshot()).stream()
                    .filter(s -> !used.contains(s.id())).toList());
            Collections.shuffle(cands, random);
            for (QuizJson.Snapshot s : cands.subList(0, Math.min(rule.getQuestionCount(), cands.size()))) {
                used.add(s.id());
                paper.add(s.withPoints(rule.getPointsEach()));
            }
        }
        if (QuizRules.flag(q.getShuffleQuestions())) {
            Collections.shuffle(paper, random);
        }
        return paper;
    }

    private PortalAttemptDto toAttemptDto(QuizAttemptEntity a, QuizEntity q, LocalDateTime now) {
        boolean inProgress = AttemptStatus.IN_PROGRESS.equals(a.getStatus());
        List<PortalAttemptDto.Question> questions = new ArrayList<>();
        List<PortalAttemptDto.SavedAnswer> saved = new ArrayList<>();
        if (inProgress) {
            Map<Long, List<Long>> orders = json.paper(a.getQuestionOrder()).stream()
                    .collect(Collectors.toMap(QuizJson.PaperItem::questionId, QuizJson.PaperItem::optionOrder, (x, y) -> x));
            int index = 1;
            for (QuizAnswerEntity x : answerRepository.findByAttemptIdOrderBySortOrderAscIdAsc(a.getId())) {
                QuizJson.Snapshot s = json.snapshot(x.getSnapshot());
                questions.add(PortalAttemptDto.Question.builder().questionId(x.getQuestionId()).index(index++).type(s.type())
                        .content(s.content()).points(x.getPoints())
                        .options(orderedOptions(s, orders.get(x.getQuestionId())).stream()
                                .map(o -> PortalAttemptDto.Option.builder().id(o.id()).content(o.content()).build()).toList())
                        .build());
                if (x.getSavedAt() != null) {
                    saved.add(PortalAttemptDto.SavedAnswer.builder().questionId(x.getQuestionId())
                            .selectedOptionIds(json.longs(x.getSelectedOptionIds())).textAnswer(x.getTextAnswer())
                            .savedAt(x.getSavedAt()).build());
                }
            }
        }
        return PortalAttemptDto.builder().attemptId(a.getId()).quizId(a.getQuizId()).title(q.getTitle())
                .attemptNo(a.getAttemptNo()).status(a.getStatus()).startedAt(a.getStartedAt()).deadlineAt(a.getDeadlineAt())
                .serverNow(now).remainingSeconds(inProgress ? QuizRules.remainingSeconds(a.getDeadlineAt(), now) : 0L)
                .questions(questions).savedAnswers(saved).build();
    }

    private static List<QuizJson.SnapOption> orderedOptions(QuizJson.Snapshot s, List<Long> order) {
        List<QuizJson.SnapOption> opts = s.options() == null ? List.of() : s.options();
        if (order == null || order.isEmpty()) {
            return opts;
        }
        Map<Long, QuizJson.SnapOption> byId = opts.stream().collect(Collectors.toMap(QuizJson.SnapOption::id, o -> o, (x, y) -> x));
        List<QuizJson.SnapOption> out = new ArrayList<>();
        for (Long id : order) {
            if (byId.containsKey(id)) {
                out.add(byId.remove(id));
            }
        }
        out.addAll(byId.values());
        return out;
    }

    static void validateAnswer(QuizJson.Snapshot s, List<Long> selected, String text) {
        boolean textType = QuestionType.SHORT_TEXT.equals(s.type()) || QuestionType.ESSAY.equals(s.type());
        if (textType) {
            if (!selected.isEmpty()) {
                throw invalid("Câu này không có lựa chọn.");
            }
            if (text != null && text.length() > QuizConstants.TEXT_ANSWER_MAX) {
                throw invalid("Câu trả lời tối đa 10000 ký tự.");
            }
            return;
        }
        Set<Long> ids = s.options().stream().map(QuizJson.SnapOption::id).collect(Collectors.toSet());
        if (!ids.containsAll(selected)) {
            throw invalid("Lựa chọn không thuộc câu hỏi.");
        }
        if (!QuestionType.MULTI.equals(s.type()) && selected.size() > 1) {
            throw invalid("Câu này chỉ chọn 1 đáp án.");
        }
    }

    private Map<Long, List<QuizAttemptEntity>> attemptsByQuiz(Long studentId, List<QuizEntity> quizzes) {
        Map<Long, List<QuizAttemptEntity>> out = new HashMap<>();
        List<Long> ids = quizzes.stream().map(QuizEntity::getId).toList();
        for (List<Long> chunk : QuestionServiceImpl.chunks(ids)) {
            for (QuizAttemptEntity a : attemptRepository.findByStudentIdAndQuizIdIn(studentId, chunk)) {
                out.computeIfAbsent(a.getQuizId(), k -> new ArrayList<>()).add(a);
            }
        }
        out.values().forEach(l -> l.sort(Comparator.comparing(QuizAttemptEntity::getAttemptNo)));
        return out;
    }

    private Map<Long, Integer> questionCounts(List<Long> quizIds) {
        Map<Long, Integer> out = new HashMap<>();
        for (List<Long> chunk : QuestionServiceImpl.chunks(quizIds)) {
            for (QuizQuestionEntity x : quizQuestionRepository.findByQuizIdIn(chunk)) {
                out.merge(x.getQuizId(), 1, Integer::sum);
            }
            for (QuizRuleEntity x : ruleRepository.findByQuizIdIn(chunk)) {
                out.merge(x.getQuizId(), x.getQuestionCount(), Integer::sum);
            }
        }
        return out;
    }

    private List<QuizAttemptEntity> expireDue(List<QuizAttemptEntity> list, LocalDateTime now) {
        boolean any = false;
        List<QuizAttemptEntity> out = new ArrayList<>();
        for (QuizAttemptEntity a : list) {
            if (AttemptStatus.IN_PROGRESS.equals(a.getStatus()) && QuizRules.overdue(a, now)) {
                out.add(gradingService.finish(a.getId(), true, now));
                any = true;
            } else {
                out.add(a);
            }
        }
        return any ? out : list;
    }

    private PortalQuizListItemDto toListItem(QuizEntity q, ClassEntity c, List<QuizAttemptEntity> attempts, int questionCount,
                                             LocalDateTime now) {
        QuizAttemptEntity inProgress = attempts.stream().filter(a -> AttemptStatus.IN_PROGRESS.equals(a.getStatus()))
                .findFirst().orElse(null);
        boolean open = QuizRules.isOpen(q, now);
        boolean canStart = open && (inProgress != null || attempts.size() < q.getMaxAttempts());
        String tab;
        if (now.isBefore(q.getOpenAt())) {
            tab = PortalTab.UPCOMING;
        } else if (canStart) {
            tab = PortalTab.OPEN;
        } else {
            tab = PortalTab.DONE;
        }
        boolean released = QuizRules.scoreReleased(q, now) && attempts.stream().anyMatch(QuizRules::finished);
        java.math.BigDecimal fin = released ? QuizRules.finalScore(q.getScorePolicy(), attempts) : null;
        return PortalQuizListItemDto.builder().id(q.getId()).title(q.getTitle()).className(c == null ? null : c.getClassName())
                .subjectName(c == null ? null : c.getSubjectName()).openAt(q.getOpenAt()).closeAt(q.getCloseAt())
                .durationMin(q.getDurationMin()).questionCount(questionCount).maxAttempts(q.getMaxAttempts())
                .attemptsUsed(attempts.size()).tab(tab).inProgressAttemptId(inProgress == null ? null : inProgress.getId())
                .canStart(canStart).scoreReleased(released)
                .finalScore10(fin == null ? null : QuizRules.score10(fin, q.getMaxScore())).build();
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    static OracleBusinessException quizNotFound() {
        return new OracleBusinessException(QuizErrorCodes.QUIZ_NOT_FOUND, "Không tìm thấy bài kiểm tra.");
    }

    static OracleBusinessException attemptNotFound() {
        return new OracleBusinessException(QuizErrorCodes.QUIZ_ATTEMPT_NOT_FOUND, "Không tìm thấy lượt làm bài.");
    }

    static OracleBusinessException questionNotFound() {
        return new OracleBusinessException(QuizErrorCodes.QUESTION_NOT_FOUND, "Không tìm thấy câu hỏi.");
    }

    private static OracleBusinessException submitted() {
        return new OracleBusinessException(QuizErrorCodes.QUIZ_ATTEMPT_SUBMITTED, "Bài đã nộp.");
    }

    private static OracleBusinessException invalid(String msg) {
        return new OracleBusinessException(QuizErrorCodes.VALIDATION_ERROR, msg);
    }
}
