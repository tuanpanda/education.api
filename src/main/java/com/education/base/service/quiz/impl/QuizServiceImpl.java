package com.education.base.service.quiz.impl;

import com.education.base.audit.AuditActions;
import com.education.base.common.DomainConstants;
import com.education.base.common.HtmlContentSanitizer;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.homework.HomeworkErrorCodes;
import com.education.base.common.quiz.QuizConstants;
import com.education.base.common.quiz.QuizConstants.AttemptStatus;
import com.education.base.common.quiz.QuizConstants.QuestionType;
import com.education.base.common.quiz.QuizConstants.QuizStatus;
import com.education.base.common.quiz.QuizConstants.ResultMode;
import com.education.base.common.quiz.QuizConstants.ScorePolicy;
import com.education.base.common.quiz.QuizErrorCodes;
import com.education.base.dto.request.GradeQuizAnswerRequest;
import com.education.base.dto.request.QuizUpsertRequest;
import com.education.base.dto.response.AnnouncementClassOptionDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.QuizAttemptDetailDto;
import com.education.base.dto.response.QuizAttemptMatrixDto;
import com.education.base.dto.response.QuizDto;
import com.education.base.dto.response.QuizListItemDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.LessonEntity;
import com.education.base.entity.QuestionEntity;
import com.education.base.entity.QuestionOptionEntity;
import com.education.base.entity.QuizAnswerEntity;
import com.education.base.entity.QuizAttemptEntity;
import com.education.base.entity.QuizEntity;
import com.education.base.entity.QuizQuestionEntity;
import com.education.base.entity.QuizRuleEntity;
import com.education.base.entity.StudentEntity;
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
import com.education.base.security.SecurityUtils;
import com.education.base.service.AuditService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** GĐ4 - đề kiểm tra phía nhân viên ({@code /api/v1/lms/quizzes}). Hợp đồng: docs/contracts/quiz-api.md §2.2. */
@Service
@RequiredArgsConstructor
public class QuizServiceImpl {

    static final List<String> ACTIVE_CLASS_STATUSES = List.of("PLANNED", "OPEN", "ONGOING");

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizRuleRepository ruleRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAnswerRepository answerRepository;
    private final QuizAttemptEventRepository eventRepository;
    private final QuestionRepository questionRepository;
    private final QuestionServiceImpl questionService;
    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
    private final StudentRepository studentRepository;
    private final LessonRepository lessonRepository;
    private final QuizGradingService gradingService;
    private final QuizAccess access;
    private final QuizJson json;
    private final HtmlContentSanitizer htmlContentSanitizer;
    private final AuditService auditService;
    private final Clock clock;

    // ============================================================================ đọc

    @Transactional(readOnly = true)
    public PageResponse<QuizListItemDto> search(Long classId, String status, String keyword, Integer pageNo, Integer pageSize) {
        int pn = Math.max(pageNo == null ? 1 : pageNo, 1);
        int ps = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), QuizConstants.MAX_PAGE_SIZE);
        Optional<Set<Long>> taught = access.taughtClassIds();
        if (taught.isPresent() && (taught.get().isEmpty() || (classId != null && !taught.get().contains(classId)))) {
            return PageResponse.of(List.of(), pn, ps, 0);
        }
        Specification<QuizEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("isDeleted"), PersistenceFlags.NOT_DELETED));
            if (classId != null) {
                p.add(cb.equal(root.get("classId"), classId));
            }
            if (status != null && !status.isBlank()) {
                p.add(cb.equal(root.get("status"), status.strip().toUpperCase(Locale.ROOT)));
            }
            if (keyword != null && !keyword.isBlank()) {
                p.add(cb.like(cb.lower(root.get("title")), "%" + keyword.strip().toLowerCase(Locale.ROOT) + "%"));
            }
            taught.ifPresent(ids -> p.add(root.get("classId").in(ids)));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<QuizEntity> page = quizRepository.findAll(spec,
                PageRequest.of(pn - 1, ps, Sort.by(Sort.Order.desc("openAt"), Sort.Order.desc("id"))));
        LocalDateTime now = QuizRules.now(clock);
        Map<Long, ClassEntity> classes = classesOf(page.getContent().stream().map(QuizEntity::getClassId).toList());
        List<QuizListItemDto> content = page.getContent().stream()
                .map(q -> toListItem(q, classes.get(q.getClassId()), now)).toList();
        return PageResponse.of(content, pn, ps, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<AnnouncementClassOptionDto> manageableClasses() {
        List<ClassEntity> classes = access.taughtClassIds().isPresent()
                ? classRepository.findByIdInAndIsDeletedOrderByClassCode(access.taughtClassIds().get(), PersistenceFlags.NOT_DELETED)
                : classRepository.findByStatusInAndIsDeletedOrderByCreatedAtDesc(ACTIVE_CLASS_STATUSES, PersistenceFlags.NOT_DELETED);
        return classes.stream().map(c -> AnnouncementClassOptionDto.builder().id(c.getId()).classCode(c.getClassCode())
                .className(c.getClassName()).build()).toList();
    }

    @Transactional(readOnly = true)
    public QuizDto get(Long id) {
        QuizEntity q = requireQuiz(id);
        ClassEntity c = access.requireClassScope(q.getClassId());
        return toDto(q, c);
    }

    // ============================================================================ ghi

    @Transactional(rollbackFor = Exception.class)
    public QuizDto create(QuizUpsertRequest r) {
        ClassEntity c = access.requireClassScope(r.getClassId());
        QuizEntity q = new QuizEntity();
        q.setClassId(c.getId());
        q.setStatus(QuizStatus.DRAFT);
        q.setIsDeleted(PersistenceFlags.NOT_DELETED);
        q.setCreatedBy(SecurityUtils.currentUsername());
        q.setCreatedAt(QuizRules.now(clock));
        applySettings(q, r);
        validateLesson(r.getLessonId(), c.getId());
        q.setLessonId(r.getLessonId());
        List<QuizQuestionEntity> questions = validateQuestions(r);
        List<QuizRuleEntity> rules = buildRules(r);
        q.setMaxScore(maxScore(questions, rules));
        validateSchedule(q);
        QuizEntity saved = quizRepository.saveAndFlush(q);
        saveChildren(saved.getId(), questions, rules);
        audit(AuditActions.QUIZ_CREATED, saved, Map.of("classId", saved.getClassId()));
        return toDto(saved, c);
    }

    @Transactional(rollbackFor = Exception.class)
    public QuizDto update(Long id, QuizUpsertRequest r) {
        QuizEntity q = requireQuiz(id);
        ClassEntity c = access.requireClassScope(q.getClassId());
        QuestionServiceImpl.requireVersion(q.getVersion(), r.getExpectedVersion(), QuizEntity.class, id);
        boolean hasAttempts = attemptRepository.existsByQuizId(q.getId());
        boolean oldSync = QuizRules.flag(q.getSyncToGradebook());
        String oldPolicy = q.getScorePolicy();
        if (hasAttempts || !QuizStatus.DRAFT.equals(q.getStatus())) {
            requireOnlyLimitedChanges(q, r, hasAttempts);
        }
        if (!Objects.equals(q.getClassId(), r.getClassId())) {
            if (!QuizStatus.DRAFT.equals(q.getStatus())) {
                throw locked("Không đổi được lớp sau khi đã xuất bản.");
            }
            c = access.requireClassScope(r.getClassId());
            q.setClassId(c.getId());
        }
        LocalDateTime oldClose = q.getCloseAt();
        if (QuizStatus.DRAFT.equals(q.getStatus())) {
            applySettings(q, r);
            validateLesson(r.getLessonId(), q.getClassId());
            q.setLessonId(r.getLessonId());
            List<QuizQuestionEntity> questions = validateQuestions(r);
            List<QuizRuleEntity> rules = buildRules(r);
            q.setMaxScore(maxScore(questions, rules));
            validateSchedule(q);
            quizQuestionRepository.deleteByQuizId(q.getId());
            ruleRepository.deleteByQuizId(q.getId());
            quizQuestionRepository.flush();
            ruleRepository.flush();
            saveChildren(q.getId(), questions, rules);
        } else {
            // PUBLISHED / CLOSED: chỉ đổi được tiêu đề, mô tả, giờ đóng, chế độ xem kết quả (+ cờ sổ điểm, cách tính khi chưa có lượt).
            q.setTitle(r.getTitle().strip());
            q.setDescription(sanitize(r.getDescription()));
            q.setResultMode(r.getResultMode());
            if (!hasAttempts) {
                q.setScorePolicy(policy(r.getScorePolicy()));
                q.setPassScore(r.getPassScore());
            }
            q.setSyncToGradebook(QuizRules.flag(r.getSyncToGradebook()));
            if (!Objects.equals(oldClose, r.getCloseAt())) {
                if (QuizStatus.CLOSED.equals(q.getStatus())) {
                    throw locked("Đề đã đóng.");
                }
                q.setCloseAt(r.getCloseAt());
            }
            validateSchedule(q);
        }
        q.setUpdatedBy(SecurityUtils.currentUsername());
        QuizEntity saved = quizRepository.saveAndFlush(q);
        if (oldSync != QuizRules.flag(saved.getSyncToGradebook()) || !Objects.equals(oldPolicy, saved.getScorePolicy())
                || (QuizRules.flag(saved.getSyncToGradebook()) && !Objects.equals(oldClose, saved.getCloseAt()))) {
            gradingService.resyncQuiz(saved);
        }
        audit(AuditActions.QUIZ_UPDATED, saved, Map.of("classId", saved.getClassId()));
        return toDto(saved, c);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        QuizEntity q = requireQuiz(id);
        access.requireClassScope(q.getClassId());
        if (attemptRepository.existsByQuizId(q.getId())) {
            throw locked("Đề đã có lượt làm, không xoá được.");
        }
        q.setIsDeleted(PersistenceFlags.DELETED);
        q.setUpdatedBy(SecurityUtils.currentUsername());
        quizRepository.save(q);
        audit(AuditActions.QUIZ_DELETED, q, Map.of("classId", q.getClassId()));
    }

    /**
     * Chép đề sang lớp khác (BR-10, AC-40): bản chép DRAFT ở lớp đích, lessonId = null; chép câu chọn tay, quy tắc
     * (không CANDIDATE_SNAPSHOT), cài đặt; không chép snapshot, attempt, RESULTS_RELEASED_AT. GV phải dạy cả 2 lớp.
     */
    @Transactional(rollbackFor = Exception.class)
    public QuizDto copy(Long id, Long targetClassId) {
        QuizEntity src = requireQuiz(id);
        access.requireClassScope(src.getClassId());
        ClassEntity target = access.requireClassScope(targetClassId);
        LocalDateTime now = QuizRules.now(clock);
        QuizEntity q = QuizEntity.builder().classId(target.getId()).lessonId(null).title(src.getTitle())
                .description(src.getDescription()).openAt(src.getOpenAt()).closeAt(src.getCloseAt())
                .durationMin(src.getDurationMin()).maxAttempts(src.getMaxAttempts())
                .shuffleQuestions(src.getShuffleQuestions()).shuffleOptions(src.getShuffleOptions())
                .resultMode(src.getResultMode()).scorePolicy(src.getScorePolicy()).passScore(src.getPassScore())
                .maxScore(src.getMaxScore()).syncToGradebook(src.getSyncToGradebook()).status(QuizStatus.DRAFT)
                .isDeleted(PersistenceFlags.NOT_DELETED).createdBy(SecurityUtils.currentUsername()).createdAt(now).build();
        QuizEntity saved = quizRepository.saveAndFlush(q);
        List<QuizQuestionEntity> qs = quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(src.getId()).stream()
                .map(x -> QuizQuestionEntity.builder().quizId(saved.getId()).questionId(x.getQuestionId())
                        .points(x.getPoints()).sortOrder(x.getSortOrder()).snapshot(null).build()).toList();
        List<QuizRuleEntity> rs = ruleRepository.findByQuizIdOrderBySortOrderAscIdAsc(src.getId()).stream()
                .map(x -> QuizRuleEntity.builder().quizId(saved.getId()).topic(x.getTopic()).difficulty(x.getDifficulty())
                        .questionCount(x.getQuestionCount()).pointsEach(x.getPointsEach()).sortOrder(x.getSortOrder())
                        .candidateSnapshot(null).build()).toList();
        quizQuestionRepository.saveAll(qs);
        ruleRepository.saveAll(rs);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("sourceQuizId", src.getId());
        d.put("targetClassId", target.getId());
        audit(AuditActions.QUIZ_COPIED, saved, d);
        return toDto(saved, target);
    }

    /** Xuất bản: >= 1 câu; chụp SNAPSHOT câu chọn tay; khoá danh sách ứng viên của mỗi quy tắc (BR-15). */
    @Transactional(rollbackFor = Exception.class)
    public QuizDto publish(Long id, Long expectedVersion) {
        QuizEntity q = requireQuiz(id);
        ClassEntity c = access.requireClassScope(q.getClassId());
        QuestionServiceImpl.requireVersion(q.getVersion(), expectedVersion, QuizEntity.class, id);
        if (!QuizStatus.DRAFT.equals(q.getStatus())) {
            throw locked("Chỉ xuất bản được đề nháp.");
        }
        List<QuizQuestionEntity> qs = quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(q.getId());
        List<QuizRuleEntity> rules = ruleRepository.findByQuizIdOrderBySortOrderAscIdAsc(q.getId());
        if (qs.isEmpty() && rules.isEmpty()) {
            throw new OracleBusinessException(QuizErrorCodes.QUIZ_RULE_NOT_ENOUGH_QUESTIONS, "Đề chưa có câu hỏi nào.");
        }
        Map<Long, QuestionEntity> bank = questionRepository.findByIdIn(qs.stream().map(QuizQuestionEntity::getQuestionId)
                .toList()).stream().collect(Collectors.toMap(QuestionEntity::getId, Function.identity()));
        Map<Long, List<QuestionOptionEntity>> opts = questionService.optionsOf(bank.keySet());
        for (QuizQuestionEntity x : qs) {
            QuestionEntity question = bank.get(x.getQuestionId());
            if (question == null || question.getIsDeleted() != PersistenceFlags.NOT_DELETED) {
                throw new OracleBusinessException(QuizErrorCodes.QUESTION_NOT_FOUND,
                        "Câu hỏi #" + x.getQuestionId() + " đã bị xoá, hãy bỏ khỏi đề.");
            }
            x.setSnapshot(json.write(questionService.snapshot(question, opts.getOrDefault(question.getId(), List.of()))));
        }
        Set<Long> fixedIds = qs.stream().map(QuizQuestionEntity::getQuestionId).collect(Collectors.toSet());
        Set<Long> usedByRules = new HashSet<>();
        Long ownerScope = access.questionScopeOwnerId();
        for (QuizRuleEntity rule : rules) {
            List<QuestionEntity> cands = questionRepository.findCandidates(rule.getTopic(), rule.getDifficulty(), ownerScope)
                    .stream().filter(x -> !fixedIds.contains(x.getId()) && !usedByRules.contains(x.getId())).toList();
            if (cands.size() < rule.getQuestionCount()) {
                throw new OracleBusinessException(QuizErrorCodes.QUIZ_RULE_NOT_ENOUGH_QUESTIONS,
                        "Ngân hàng không đủ câu hỏi cho quy tắc \"" + rule.getTopic() + "\" (cần "
                                + rule.getQuestionCount() + ", có " + cands.size() + ").");
            }
            Map<Long, List<QuestionOptionEntity>> co = questionService.optionsOf(cands.stream().map(QuestionEntity::getId).toList());
            List<QuizJson.Snapshot> snaps = cands.stream()
                    .map(x -> questionService.snapshot(x, co.getOrDefault(x.getId(), List.of()))).toList();
            rule.setCandidateSnapshot(json.write(snaps));
            cands.forEach(x -> usedByRules.add(x.getId()));
        }
        quizQuestionRepository.saveAll(qs);
        ruleRepository.saveAll(rules);
        q.setMaxScore(maxScore(qs, rules));
        q.setStatus(QuizStatus.PUBLISHED);
        q.setPublishedAt(QuizRules.now(clock));
        q.setUpdatedBy(SecurityUtils.currentUsername());
        QuizEntity saved = quizRepository.saveAndFlush(q);
        audit(AuditActions.QUIZ_PUBLISHED, saved, Map.of("questions", qs.size(), "rules", rules.size()));
        return toDto(saved, c);
    }

    @Transactional(rollbackFor = Exception.class)
    public QuizDto unpublish(Long id, Long expectedVersion) {
        QuizEntity q = requireQuiz(id);
        ClassEntity c = access.requireClassScope(q.getClassId());
        QuestionServiceImpl.requireVersion(q.getVersion(), expectedVersion, QuizEntity.class, id);
        if (!QuizStatus.PUBLISHED.equals(q.getStatus()) || attemptRepository.existsByQuizId(q.getId())) {
            throw locked("Chỉ huỷ xuất bản được đề chưa có lượt làm.");
        }
        List<QuizQuestionEntity> qs = quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(q.getId());
        qs.forEach(x -> x.setSnapshot(null));
        quizQuestionRepository.saveAll(qs);
        List<QuizRuleEntity> rules = ruleRepository.findByQuizIdOrderBySortOrderAscIdAsc(q.getId());
        rules.forEach(x -> x.setCandidateSnapshot(null));
        ruleRepository.saveAll(rules);
        q.setStatus(QuizStatus.DRAFT);
        q.setPublishedAt(null);
        q.setUpdatedBy(SecurityUtils.currentUsername());
        QuizEntity saved = quizRepository.saveAndFlush(q);
        audit(AuditActions.QUIZ_UNPUBLISHED, saved, Map.of());
        return toDto(saved, c);
    }

    /** Đóng sớm (BR-17): đã mở -> CLOSE_AT = now, rút DEADLINE_AT; chưa mở -> chỉ STATUS = CLOSED. */
    @Transactional(rollbackFor = Exception.class)
    public QuizDto close(Long id, Long expectedVersion) {
        QuizEntity q = requireQuiz(id);
        ClassEntity c = access.requireClassScope(q.getClassId());
        QuestionServiceImpl.requireVersion(q.getVersion(), expectedVersion, QuizEntity.class, id);
        if (!QuizStatus.PUBLISHED.equals(q.getStatus())) {
            throw locked("Chỉ đóng được đề đang xuất bản.");
        }
        LocalDateTime now = QuizRules.now(clock);
        int shortened = 0;
        boolean opened = !now.isBefore(q.getOpenAt());
        if (opened && now.isBefore(q.getCloseAt())) {
            q.setCloseAt(now);
        }
        q.setStatus(QuizStatus.CLOSED);
        q.setClosedAt(now);
        q.setUpdatedBy(SecurityUtils.currentUsername());
        QuizEntity saved = quizRepository.saveAndFlush(q);
        if (opened) {
            shortened = attemptRepository.shortenDeadlines(saved.getId(), saved.getCloseAt());
        }
        QuizEntity reloaded = quizRepository.findById(saved.getId()).orElse(saved);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("closeAt", String.valueOf(reloaded.getCloseAt()));
        d.put("opened", opened);
        d.put("inProgress", shortened);
        audit(AuditActions.QUIZ_CLOSED_EARLY, reloaded, d);
        return toDto(reloaded, c);
    }

    @Transactional(rollbackFor = Exception.class)
    public QuizDto releaseResults(Long id) {
        QuizEntity q = requireQuiz(id);
        ClassEntity c = access.requireClassScope(q.getClassId());
        LocalDateTime now = QuizRules.now(clock);
        if (!ResultMode.MANUAL.equals(q.getResultMode()) || QuizStatus.DRAFT.equals(q.getStatus())
                || now.isBefore(q.getCloseAt())) {
            throw locked("Chỉ công bố được đề chế độ thủ công sau giờ đóng.");
        }
        q.setResultsReleasedAt(now);
        q.setUpdatedBy(SecurityUtils.currentUsername());
        QuizEntity saved = quizRepository.saveAndFlush(q);
        audit(AuditActions.QUIZ_RESULTS_RELEASED, saved, Map.of());
        return toDto(saved, c);
    }

    // ============================================================================ chấm

    /** Ma trận HS × lượt (HS ENROLLED + HS đã rút có lượt). Truy vấn gộp, không N+1. Nộp lười các lượt quá hạn. */
    @Transactional(rollbackFor = Exception.class)
    public QuizAttemptMatrixDto matrix(Long id, String statusFilter) {
        QuizEntity q = requireQuiz(id);
        ClassEntity c = access.requireClassScope(q.getClassId());
        LocalDateTime now = QuizRules.now(clock);
        List<QuizAttemptEntity> attempts = attemptRepository.findByQuizIdOrderByStudentIdAscAttemptNoAsc(q.getId());
        if (gradingService.expireAll(attempts, now) > 0) {
            attempts = attemptRepository.findByQuizIdOrderByStudentIdAscAttemptNoAsc(q.getId());
        }
        Map<Long, List<QuizAttemptEntity>> byStudent = new LinkedHashMap<>();
        for (QuizAttemptEntity a : attempts) {
            byStudent.computeIfAbsent(a.getStudentId(), k -> new ArrayList<>()).add(a);
        }
        List<ClassStudentEntity> enrollments = classStudentRepository.findByClassIdAndIsDeleted(q.getClassId(),
                PersistenceFlags.NOT_DELETED);
        Map<Long, String> enrollmentStatus = new LinkedHashMap<>();
        for (ClassStudentEntity e : enrollments) {
            if (DomainConstants.ENROLLMENT_STATUS_ENROLLED.equals(e.getStatus()) || byStudent.containsKey(e.getStudentId())) {
                enrollmentStatus.put(e.getStudentId(), e.getStatus());
            }
        }
        for (Long sid : byStudent.keySet()) {
            enrollmentStatus.putIfAbsent(sid, null);
        }
        Map<Long, StudentEntity> students = studentRepository.findAllById(enrollmentStatus.keySet()).stream()
                .collect(Collectors.toMap(StudentEntity::getId, Function.identity()));
        Set<Long> pendingIds = pendingEssayIds(attempts);
        List<QuizAttemptMatrixDto.Row> rows = new ArrayList<>();
        for (Map.Entry<Long, String> e : enrollmentStatus.entrySet()) {
            List<QuizAttemptEntity> list = byStudent.getOrDefault(e.getKey(), List.of());
            QuizAttemptEntity last = list.isEmpty() ? null : list.get(list.size() - 1);
            BigDecimal best = list.stream().filter(QuizRules::finished).map(QuizAttemptEntity::getScore)
                    .filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
            BigDecimal fin = QuizRules.finalScore(q.getScorePolicy(), list);
            Set<String> flags = new LinkedHashSet<>();
            int focus = 0;
            for (QuizAttemptEntity a : list) {
                flags.addAll(flagsOf(a));
                focus += a.getFocusLostCount() == null ? 0 : a.getFocusLostCount();
            }
            StudentEntity s = students.get(e.getKey());
            String status = last == null ? AttemptStatus.NOT_STARTED : last.getStatus();
            if (statusFilter != null && !statusFilter.isBlank() && !statusFilter.equalsIgnoreCase(status)) {
                continue;
            }
            rows.add(QuizAttemptMatrixDto.Row.builder().studentId(e.getKey())
                    .studentCode(s == null ? null : s.getStudentCode()).fullName(s == null ? null : s.getFullName())
                    .enrollmentStatus(e.getValue()).attemptsUsed(list.size()).bestScore(best).finalScore(fin)
                    .finalScore10(QuizRules.score10(fin, q.getMaxScore())).status(status)
                    .lastAttemptId(last == null ? null : last.getId()).focusLostCount(focus).flags(new ArrayList<>(flags))
                    .pendingEssay(last != null && pendingIds.contains(last.getId()))
                    .build());
        }
        rows.sort(Comparator.comparing((QuizAttemptMatrixDto.Row r) -> r.getFullName() == null ? "" : r.getFullName())
                .thenComparing(QuizAttemptMatrixDto.Row::getStudentId));
        return QuizAttemptMatrixDto.builder().quiz(toListItem(q, c, now)).rows(rows).build();
    }

    @Transactional(rollbackFor = Exception.class)
    public QuizAttemptDetailDto attempt(Long attemptId) {
        QuizAttemptEntity a = requireAttemptInScope(attemptId);
        a = gradingService.expireIfDue(a, QuizRules.now(clock));
        return toDetail(a);
    }

    /**
     * Chấm / ghi đè 1 câu (BR-32, BR-33). Ngoài 0..points -> GRADE_SCORE_OUT_OF_RANGE; không bội 0.25 hoặc thiếu reason
     * khi ghi đè câu tự động -> VALIDATION_ERROR; lệch version -> 409 GRADE_CONFLICT.
     */
    @Transactional(rollbackFor = Exception.class)
    public QuizAttemptDetailDto grade(Long attemptId, Long questionId, GradeQuizAnswerRequest r) {
        QuizAttemptEntity a = requireAttemptInScope(attemptId);
        LocalDateTime now = QuizRules.now(clock);
        a = gradingService.expireIfDue(a, now);
        if (!QuizRules.finished(a)) {
            throw locked("Lượt làm chưa nộp, chưa chấm được.");
        }
        QuizAnswerEntity ans = answerRepository.findByAttemptIdAndQuestionId(a.getId(), questionId)
                .orElseThrow(QuestionServiceImpl::notFound);
        if (!Objects.equals(ans.getVersion(), r.getExpectedVersion())) {
            throw new OracleBusinessException(HomeworkErrorCodes.GRADE_CONFLICT, "Bài vừa được người khác chấm. Tải lại để xem.");
        }
        BigDecimal pts = r.getPointsAwarded();
        if (pts.signum() < 0 || pts.compareTo(ans.getPoints()) > 0) {
            throw new OracleBusinessException(HomeworkErrorCodes.GRADE_SCORE_OUT_OF_RANGE,
                    "Điểm phải trong khoảng 0 đến " + ans.getPoints().stripTrailingZeros().toPlainString() + ".");
        }
        if (!QuizRules.isStep(pts)) {
            throw new OracleBusinessException(QuizErrorCodes.VALIDATION_ERROR, "Điểm phải là bội số của 0.25.");
        }
        QuizJson.Snapshot snap = json.snapshot(ans.getSnapshot());
        boolean auto = snap != null && QuizRules.isAuto(snap.type());
        String reason = r.getReason() == null ? null : r.getReason().strip();
        if (auto && (reason == null || reason.length() < QuizConstants.REASON_MIN || reason.length() > QuizConstants.REASON_MAX)) {
            throw new OracleBusinessException(QuizErrorCodes.VALIDATION_ERROR,
                    "Ghi đè điểm câu chấm tự động cần lý do 5-500 ký tự.");
        }
        BigDecimal old = ans.getPointsAwarded();
        ans.setPointsAwarded(pts);
        ans.setGraderComment(r.getComment() == null || r.getComment().isBlank() ? null : r.getComment().strip());
        if (auto) {
            ans.setOverrideReason(reason);
        }
        ans.setGradedBy(SecurityUtils.currentUsername());
        ans.setGradedAt(now);
        answerRepository.saveAndFlush(ans);
        List<QuizAnswerEntity> answers = answerRepository.findByAttemptIdOrderBySortOrderAscIdAsc(a.getId());
        QuizGradingService.applyScore(a, answers);
        attemptRepository.saveAndFlush(a);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("attemptId", a.getId());
        d.put("questionId", questionId);
        d.put("oldPoints", old == null ? null : old.toPlainString());
        d.put("newPoints", pts.toPlainString());
        if (auto) {
            d.put("reason", reason);
        }
        auditService.success(auto ? AuditActions.QUIZ_ANSWER_OVERRIDDEN : AuditActions.QUIZ_ANSWER_GRADED,
                AuditActions.RESOURCE_QUIZ_ATTEMPT, a.getId(), d);
        QuizEntity q = requireQuiz(a.getQuizId());
        gradingService.syncStudent(q, a.getStudentId());
        return toDetail(a);
    }

    // ============================================================================ helpers

    QuizEntity requireQuiz(Long id) {
        return quizRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(QuizErrorCodes.QUIZ_NOT_FOUND, "Không tìm thấy bài kiểm tra."));
    }

    private QuizAttemptEntity requireAttemptInScope(Long attemptId) {
        QuizAttemptEntity a = attemptRepository.findById(attemptId).orElseThrow(() ->
                new OracleBusinessException(QuizErrorCodes.QUIZ_ATTEMPT_NOT_FOUND, "Không tìm thấy lượt làm bài."));
        QuizEntity q = requireQuiz(a.getQuizId());
        access.requireClassScope(q.getClassId());
        return a;
    }

    private void applySettings(QuizEntity q, QuizUpsertRequest r) {
        q.setTitle(r.getTitle().strip());
        q.setDescription(sanitize(r.getDescription()));
        q.setOpenAt(r.getOpenAt() == null ? null : r.getOpenAt().withNano(0));
        q.setCloseAt(r.getCloseAt() == null ? null : r.getCloseAt().withNano(0));
        q.setDurationMin(r.getDurationMin());
        q.setMaxAttempts(r.getMaxAttempts());
        q.setShuffleQuestions(QuizRules.flag(r.getShuffleQuestions()));
        q.setShuffleOptions(QuizRules.flag(r.getShuffleOptions()));
        q.setResultMode(r.getResultMode());
        q.setScorePolicy(policy(r.getScorePolicy()));
        q.setPassScore(r.getPassScore());
        q.setSyncToGradebook(QuizRules.flag(r.getSyncToGradebook()));
    }

    private static String policy(String p) {
        return p == null || p.isBlank() ? ScorePolicy.HIGHEST : p;
    }

    /** BR-12: CLOSE_AT > OPEN_AT, DURATION 1..300 hoặc null, MAX_ATTEMPTS 1..10, PASS_SCORE <= MAX_SCORE. */
    static void validateSchedule(QuizEntity q) {
        boolean ok = q.getOpenAt() != null && q.getCloseAt() != null && q.getCloseAt().isAfter(q.getOpenAt())
                && (q.getDurationMin() == null || (q.getDurationMin() >= QuizConstants.DURATION_MIN
                && q.getDurationMin() <= QuizConstants.DURATION_MAX))
                && q.getMaxAttempts() != null && q.getMaxAttempts() >= QuizConstants.MAX_ATTEMPTS_MIN
                && q.getMaxAttempts() <= QuizConstants.MAX_ATTEMPTS_MAX
                && (q.getPassScore() == null || q.getMaxScore() == null || q.getPassScore().compareTo(q.getMaxScore()) <= 0);
        if (!ok) {
            throw new OracleBusinessException(QuizErrorCodes.QUIZ_INVALID_SCHEDULE,
                    "Thời gian/cài đặt bài kiểm tra không hợp lệ.");
        }
    }

    private void requireOnlyLimitedChanges(QuizEntity q, QuizUpsertRequest r, boolean hasAttempts) {
        if (!hasAttempts && QuizStatus.PUBLISHED.equals(q.getStatus())) {
            // PUBLISHED chưa có lượt: phải huỷ xuất bản mới sửa câu/cài đặt.
        }
        boolean same = Objects.equals(q.getClassId(), r.getClassId())
                && Objects.equals(q.getLessonId(), r.getLessonId())
                && Objects.equals(q.getOpenAt(), r.getOpenAt() == null ? null : r.getOpenAt().withNano(0))
                && Objects.equals(q.getDurationMin(), r.getDurationMin())
                && Objects.equals(q.getMaxAttempts(), r.getMaxAttempts())
                && q.getShuffleQuestions() == QuizRules.flag(r.getShuffleQuestions())
                && q.getShuffleOptions() == QuizRules.flag(r.getShuffleOptions())
                && (!hasAttempts || (Objects.equals(q.getScorePolicy(), policy(r.getScorePolicy()))
                && sameNumber(q.getPassScore(), r.getPassScore())))
                && sameQuestions(q.getId(), r) && sameRules(q.getId(), r);
        if (!same) {
            throw locked("Đề đã xuất bản/có lượt làm: chỉ sửa được tiêu đề, mô tả, giờ đóng, chế độ xem kết quả.");
        }
        if (r.getCloseAt() == null || !r.getCloseAt().isAfter(q.getOpenAt())) {
            throw new OracleBusinessException(QuizErrorCodes.QUIZ_INVALID_SCHEDULE, "Giờ đóng phải sau giờ mở.");
        }
    }

    private boolean sameQuestions(Long quizId, QuizUpsertRequest r) {
        List<QuizQuestionEntity> cur = quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(quizId);
        List<QuizUpsertRequest.QuestionItem> req = r.getQuestions() == null ? List.of() : r.getQuestions();
        if (cur.size() != req.size()) {
            return false;
        }
        Map<Long, BigDecimal> m = cur.stream().collect(Collectors.toMap(QuizQuestionEntity::getQuestionId,
                QuizQuestionEntity::getPoints, (x, y) -> x));
        return req.stream().allMatch(x -> m.containsKey(x.getQuestionId()) && sameNumber(m.get(x.getQuestionId()), x.getPoints()));
    }

    private boolean sameRules(Long quizId, QuizUpsertRequest r) {
        List<QuizRuleEntity> cur = ruleRepository.findByQuizIdOrderBySortOrderAscIdAsc(quizId);
        List<QuizUpsertRequest.RuleItem> req = r.getRules() == null ? List.of() : r.getRules();
        if (cur.size() != req.size()) {
            return false;
        }
        for (int i = 0; i < cur.size(); i++) {
            QuizRuleEntity a = cur.get(i);
            QuizUpsertRequest.RuleItem b = req.get(i);
            if (!a.getTopic().equalsIgnoreCase(b.getTopic().strip()) || !Objects.equals(a.getDifficulty(), b.getDifficulty())
                    || !Objects.equals(a.getQuestionCount(), b.getQuestionCount()) || !sameNumber(a.getPointsEach(), b.getPointsEach())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameNumber(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private void validateLesson(Long lessonId, Long classId) {
        if (lessonId == null) {
            return;
        }
        LessonEntity l = lessonRepository.findById(lessonId).orElse(null);
        if (l == null || (l.getIsDeleted() != null && l.getIsDeleted() != 0) || !Objects.equals(l.getClassId(), classId)) {
            throw new OracleBusinessException(QuizErrorCodes.VALIDATION_ERROR, "Bài học phải thuộc cùng lớp.");
        }
    }

    private List<QuizQuestionEntity> validateQuestions(QuizUpsertRequest r) {
        List<QuizUpsertRequest.QuestionItem> items = r.getQuestions() == null ? List.of() : r.getQuestions();
        Set<Long> ids = new HashSet<>();
        for (QuizUpsertRequest.QuestionItem it : items) {
            if (!ids.add(it.getQuestionId())) {
                throw new OracleBusinessException(QuizErrorCodes.VALIDATION_ERROR, "Câu hỏi bị trùng trong đề.");
            }
            requirePoints(it.getPoints());
        }
        Map<Long, QuestionEntity> found = ids.isEmpty() ? Map.of() : questionRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(QuestionEntity::getId, Function.identity()));
        List<QuizQuestionEntity> out = new ArrayList<>();
        int i = 0;
        for (QuizUpsertRequest.QuestionItem it : items) {
            QuestionEntity qe = found.get(it.getQuestionId());
            if (qe == null || qe.getIsDeleted() != PersistenceFlags.NOT_DELETED || !access.canSee(qe)) {
                throw QuestionServiceImpl.notFound();
            }
            out.add(QuizQuestionEntity.builder().questionId(it.getQuestionId()).points(it.getPoints())
                    .sortOrder(it.getSortOrder() == null ? i : it.getSortOrder()).build());
            i++;
        }
        return out;
    }

    private List<QuizRuleEntity> buildRules(QuizUpsertRequest r) {
        List<QuizUpsertRequest.RuleItem> items = r.getRules() == null ? List.of() : r.getRules();
        List<QuizRuleEntity> out = new ArrayList<>();
        int i = 0;
        for (QuizUpsertRequest.RuleItem it : items) {
            requirePoints(it.getPointsEach());
            out.add(QuizRuleEntity.builder().topic(it.getTopic().strip()).difficulty(it.getDifficulty())
                    .questionCount(it.getQuestionCount()).pointsEach(it.getPointsEach()).sortOrder(i++).build());
        }
        return out;
    }

    private static void requirePoints(BigDecimal p) {
        if (p == null || p.signum() <= 0 || !QuizRules.isStep(p)) {
            throw new OracleBusinessException(QuizErrorCodes.VALIDATION_ERROR, "Điểm câu phải > 0 và là bội số của 0.25.");
        }
    }

    static BigDecimal maxScore(List<QuizQuestionEntity> qs, List<QuizRuleEntity> rules) {
        BigDecimal m = BigDecimal.ZERO;
        for (QuizQuestionEntity x : qs) {
            m = m.add(x.getPoints());
        }
        for (QuizRuleEntity x : rules) {
            m = m.add(x.getPointsEach().multiply(BigDecimal.valueOf(x.getQuestionCount())));
        }
        return m;
    }

    private void saveChildren(Long quizId, List<QuizQuestionEntity> qs, List<QuizRuleEntity> rules) {
        qs.forEach(x -> x.setQuizId(quizId));
        rules.forEach(x -> x.setQuizId(quizId));
        quizQuestionRepository.saveAll(qs);
        ruleRepository.saveAll(rules);
    }

    /** Attempt SUBMITTED/AUTO_SUBMITTED còn ít nhất 1 câu tự luận chưa chấm (POINTS_AWARDED null). */
    Set<Long> pendingEssayIds(List<QuizAttemptEntity> attempts) {
        List<Long> ids = attempts.stream().filter(a -> AttemptStatus.SUBMITTED.equals(a.getStatus())
                || AttemptStatus.AUTO_SUBMITTED.equals(a.getStatus())).map(QuizAttemptEntity::getId).toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        Set<Long> out = new java.util.HashSet<>();
        for (int i = 0; i < ids.size(); i += 900) {
            for (QuizAnswerEntity x : answerRepository.findByAttemptIdIn(ids.subList(i, Math.min(ids.size(), i + 900)))) {
                if (x.getPointsAwarded() == null) {
                    out.add(x.getAttemptId());
                }
            }
        }
        return out;
    }

    private Map<Long, ClassEntity> classesOf(List<Long> ids) {
        List<Long> d = ids.stream().distinct().toList();
        return d.isEmpty() ? Map.of() : classRepository.findAllById(d).stream()
                .collect(Collectors.toMap(ClassEntity::getId, Function.identity()));
    }

    QuizListItemDto toListItem(QuizEntity q, ClassEntity c, LocalDateTime now) {
        List<QuizAttemptEntity> attempts = attemptRepository.findByQuizIdOrderByStudentIdAscAttemptNoAsc(q.getId());
        long enrolled = classStudentRepository.countByClassIdAndStatusAndIsDeleted(q.getClassId(),
                DomainConstants.ENROLLMENT_STATUS_ENROLLED, PersistenceFlags.NOT_DELETED);
        Map<Long, List<QuizAttemptEntity>> by = new HashMap<>();
        attempts.forEach(a -> by.computeIfAbsent(a.getStudentId(), k -> new ArrayList<>()).add(a));
        long submitted = by.values().stream().filter(l -> l.stream().anyMatch(QuizRules::finished)).count();
        long pending = pendingEssayIds(attempts).size();
        List<BigDecimal> finals = by.values().stream().map(l -> QuizRules.finalScore(q.getScorePolicy(), l))
                .filter(Objects::nonNull).map(s -> QuizRules.score10(s, q.getMaxScore())).toList();
        BigDecimal avg = finals.isEmpty() ? null : finals.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(finals.size()), 2, java.math.RoundingMode.HALF_UP);
        int questionCount = quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(q.getId()).size()
                + ruleRepository.findByQuizIdOrderBySortOrderAscIdAsc(q.getId()).stream()
                .mapToInt(QuizRuleEntity::getQuestionCount).sum();
        return QuizListItemDto.builder().id(q.getId()).classId(q.getClassId()).className(c == null ? null : c.getClassName())
                .title(q.getTitle()).status(q.getStatus()).isOpen(QuizRules.isOpen(q, now)).openAt(q.getOpenAt())
                .closeAt(q.getCloseAt()).durationMin(q.getDurationMin()).maxScore(q.getMaxScore())
                .questionCount(questionCount)
                .stats(QuizListItemDto.Stats.builder().enrolled(enrolled).started((long) by.size()).submitted(submitted)
                        .pendingEssay(pending).avgScore10(avg).build())
                .build();
    }

    QuizDto toDto(QuizEntity q, ClassEntity c) {
        LocalDateTime now = QuizRules.now(clock);
        QuizListItemDto li = toListItem(q, c, now);
        List<QuizQuestionEntity> qs = quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(q.getId());
        Map<Long, QuestionEntity> bank = qs.isEmpty() ? Map.of() : questionRepository.findByIdIn(
                qs.stream().map(QuizQuestionEntity::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(QuestionEntity::getId, Function.identity()));
        List<QuizDto.QuestionItem> items = qs.stream().map(x -> {
            QuizJson.Snapshot snap = json.snapshot(x.getSnapshot());
            QuestionEntity b = bank.get(x.getQuestionId());
            String type = snap != null ? snap.type() : b == null ? null : b.getQuestionType();
            String content = snap != null ? snap.content() : b == null ? null : b.getContent();
            return QuizDto.QuestionItem.builder().questionId(x.getQuestionId()).points(x.getPoints())
                    .sortOrder(x.getSortOrder()).type(type).contentPreview(QuizRules.preview(content)).build();
        }).toList();
        Long ownerScope = access.questionScopeOwnerId();
        List<QuizDto.RuleItem> rules = ruleRepository.findByQuizIdOrderBySortOrderAscIdAsc(q.getId()).stream()
                .map(x -> QuizDto.RuleItem.builder().topic(x.getTopic()).difficulty(x.getDifficulty())
                        .questionCount(x.getQuestionCount()).pointsEach(x.getPointsEach())
                        .candidateCount(x.getCandidateSnapshot() != null ? json.snapshots(x.getCandidateSnapshot()).size()
                                : questionRepository.findCandidates(x.getTopic(), x.getDifficulty(), ownerScope).size())
                        .build()).toList();
        boolean hasAttempts = attemptRepository.existsByQuizId(q.getId());
        return QuizDto.builder().id(li.getId()).classId(li.getClassId()).className(li.getClassName()).title(li.getTitle())
                .status(li.getStatus()).isOpen(li.getIsOpen()).openAt(li.getOpenAt()).closeAt(li.getCloseAt())
                .durationMin(li.getDurationMin()).maxScore(li.getMaxScore()).questionCount(li.getQuestionCount())
                .stats(QuizDto.Stats.builder().enrolled(li.getStats().getEnrolled()).started(li.getStats().getStarted())
                        .submitted(li.getStats().getSubmitted()).pendingEssay(li.getStats().getPendingEssay())
                        .avgScore10(li.getStats().getAvgScore10()).build())
                .lessonId(q.getLessonId()).description(q.getDescription()).maxAttempts(q.getMaxAttempts())
                .shuffleQuestions(QuizRules.flag(q.getShuffleQuestions())).shuffleOptions(QuizRules.flag(q.getShuffleOptions()))
                .resultMode(q.getResultMode()).scorePolicy(q.getScorePolicy()).passScore(q.getPassScore())
                .syncToGradebook(QuizRules.flag(q.getSyncToGradebook())).resultsReleasedAt(q.getResultsReleasedAt())
                .questions(items).rules(rules).hasAttempts(hasAttempts)
                .canEdit(QuizStatus.DRAFT.equals(q.getStatus()) && !hasAttempts).version(q.getVersion()).build();
    }

    QuizAttemptDetailDto toDetail(QuizAttemptEntity a) {
        QuizEntity q = requireQuiz(a.getQuizId());
        StudentEntity s = studentRepository.findById(a.getStudentId()).orElse(null);
        List<QuizAttemptDetailDto.Answer> answers = answerRepository.findByAttemptIdOrderBySortOrderAscIdAsc(a.getId())
                .stream().map(x -> {
                    QuizJson.Snapshot snap = json.snapshot(x.getSnapshot());
                    return QuizAttemptDetailDto.Answer.builder().answerId(x.getId()).questionId(x.getQuestionId())
                            .type(snap == null ? null : snap.type()).content(snap == null ? null : snap.content())
                            .options(snap == null ? List.of() : snap.options().stream()
                                    .map(o -> QuizAttemptDetailDto.Option.builder().id(o.id()).content(o.content())
                                            .isCorrect(o.correct()).build()).toList())
                            .acceptedAnswers(snap == null || snap.acceptedAnswers() == null ? List.of() : snap.acceptedAnswers())
                            .selectedOptionIds(json.longs(x.getSelectedOptionIds())).textAnswer(x.getTextAnswer())
                            .isCorrect(x.getIsCorrect() == null ? null : x.getIsCorrect() == 1).points(x.getPoints())
                            .pointsAwarded(x.getPointsAwarded()).graderComment(x.getGraderComment())
                            .overrideReason(x.getOverrideReason()).gradedBy(x.getGradedBy()).version(x.getVersion()).build();
                }).toList();
        List<QuizAttemptDetailDto.Event> events = eventRepository.findByAttemptIdOrderByOccurredAtAscIdAsc(a.getId()).stream()
                .map(e -> QuizAttemptDetailDto.Event.builder().type(e.getEventType()).occurredAt(e.getOccurredAt())
                        .ipAddress(e.getIpAddress()).build()).toList();
        return QuizAttemptDetailDto.builder().attemptId(a.getId()).quizId(a.getQuizId()).studentId(a.getStudentId())
                .fullName(s == null ? null : s.getFullName()).attemptNo(a.getAttemptNo()).status(a.getStatus())
                .startedAt(a.getStartedAt()).deadlineAt(a.getDeadlineAt()).submittedAt(a.getSubmittedAt())
                .score(a.getScore()).score10(QuizRules.score10(a.getScore(), a.getMaxScore() != null ? a.getMaxScore() : q.getMaxScore()))
                .ipAddress(a.getIpAddress()).userAgent(a.getUserAgent())
                .focusLostCount(a.getFocusLostCount()).flags(flagsOf(a)).answers(answers).events(events).build();
    }

    static List<String> flagsOf(QuizAttemptEntity a) {
        if (a.getFlags() == null || a.getFlags().isBlank()) {
            return List.of();
        }
        return List.of(a.getFlags().split(","));
    }

    private String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String clean = htmlContentSanitizer.sanitize(html);
        return clean == null || clean.isBlank() ? null : clean;
    }

    private void audit(String action, QuizEntity q, Map<String, ?> extra) {
        Map<String, Object> d = new LinkedHashMap<>(extra);
        d.put("status", q.getStatus());
        auditService.success(action, AuditActions.RESOURCE_QUIZ, q.getId(), d);
    }

    private static OracleBusinessException locked(String msg) {
        return new OracleBusinessException(QuizErrorCodes.QUIZ_LOCKED, msg);
    }
}
