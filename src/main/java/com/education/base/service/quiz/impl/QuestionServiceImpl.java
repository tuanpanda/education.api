package com.education.base.service.quiz.impl;

import com.education.base.audit.AuditActions;
import com.education.base.common.HtmlContentSanitizer;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.quiz.QuizConstants;
import com.education.base.common.quiz.QuizConstants.QuestionType;
import com.education.base.common.quiz.QuizConstants.Visibility;
import com.education.base.common.quiz.QuizErrorCodes;
import com.education.base.dto.request.QuestionUpsertRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.QuestionDto;
import com.education.base.dto.response.QuestionListItemDto;
import com.education.base.dto.response.QuestionTopicCountDto;
import com.education.base.entity.QuestionEntity;
import com.education.base.entity.QuestionOptionEntity;
import com.education.base.entity.UserEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.QuestionOptionRepository;
import com.education.base.repository.QuestionRepository;
import com.education.base.repository.QuizQuestionRepository;
import com.education.base.repository.UserRepository;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AuditService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** GĐ4 - ngân hàng câu hỏi ({@code /api/v1/lms/questions}). Hợp đồng: docs/contracts/quiz-api.md §2.1. */
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl {

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final UserRepository userRepository;
    private final HtmlContentSanitizer htmlContentSanitizer;
    private final QuizAccess access;
    private final QuizJson json;
    private final AuditService auditService;
    private final Clock clock;

    // ============================================================================ đọc

    @Transactional(readOnly = true)
    public PageResponse<QuestionListItemDto> search(String subjectName, String gradeLevel, String topic, String type,
                                                    Integer difficulty, String visibility, Boolean mine, String keyword,
                                                    Integer pageNo, Integer pageSize) {
        int pn = Math.max(pageNo == null ? 1 : pageNo, 1);
        int ps = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), QuizConstants.MAX_PAGE_SIZE);
        Long scopeOwner = access.questionScopeOwnerId();
        Long me = access.currentUserId();
        Specification<QuestionEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("isDeleted"), PersistenceFlags.NOT_DELETED));
            if (notBlank(subjectName)) {
                p.add(cb.equal(root.get("subjectName"), subjectName.strip()));
            }
            if (notBlank(gradeLevel)) {
                p.add(cb.equal(root.get("gradeLevel"), gradeLevel.strip()));
            }
            if (notBlank(topic)) {
                p.add(cb.equal(cb.lower(root.get("topic")), topic.strip().toLowerCase(Locale.ROOT)));
            }
            if (notBlank(type)) {
                p.add(cb.equal(root.get("questionType"), type.strip().toUpperCase(Locale.ROOT)));
            }
            if (difficulty != null) {
                p.add(cb.equal(root.get("difficulty"), difficulty));
            }
            if (notBlank(visibility)) {
                p.add(cb.equal(root.get("visibility"), visibility.strip().toUpperCase(Locale.ROOT)));
            }
            if (Boolean.TRUE.equals(mine)) {
                p.add(cb.equal(root.get("ownerId"), me == null ? -1L : me));
            }
            if (notBlank(keyword)) {
                String k = "%" + keyword.strip().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("topic")), k), cb.like(cb.lower(root.get("content")), k)));
            }
            if (scopeOwner != null) {
                p.add(cb.or(cb.equal(root.get("ownerId"), scopeOwner), cb.equal(root.get("visibility"), Visibility.SHARED)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<QuestionEntity> page = questionRepository.findAll(spec,
                PageRequest.of(pn - 1, ps, Sort.by(Sort.Order.desc("id"))));
        List<QuestionEntity> rows = page.getContent();
        Map<Long, Long> used = usedCounts(rows.stream().map(QuestionEntity::getId).toList());
        Map<Long, String> owners = ownerNames(rows.stream().map(QuestionEntity::getOwnerId).toList());
        List<QuestionListItemDto> content = rows.stream().map(q -> toListItem(q, used, owners)).toList();
        return PageResponse.of(content, pn, ps, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<QuestionTopicCountDto> topics(String subjectName, String gradeLevel) {
        return questionRepository.countByTopic(blankToNull(subjectName), blankToNull(gradeLevel),
                        access.questionScopeOwnerId()).stream()
                .map(r -> QuestionTopicCountDto.builder().topic((String) r[0])
                        .difficulty(r[1] == null ? null : ((Number) r[1]).intValue())
                        .count(((Number) r[2]).longValue()).build())
                .toList();
    }

    @Transactional(readOnly = true)
    public QuestionDto get(Long id) {
        QuestionEntity q = requireVisible(id);
        return toDto(q);
    }

    // ============================================================================ ghi

    @Transactional(rollbackFor = Exception.class)
    public QuestionDto create(QuestionUpsertRequest r) {
        QuestionEntity q = new QuestionEntity();
        q.setOwnerId(access.currentUserId());
        q.setCreatedBy(SecurityUtils.currentUsername());
        q.setCreatedAt(QuizRules.now(clock));
        q.setIsDeleted(PersistenceFlags.NOT_DELETED);
        List<QuestionUpsertRequest.Option> opts = validate(r);
        apply(q, r);
        QuestionEntity saved = questionRepository.saveAndFlush(q);
        saveOptions(saved.getId(), opts);
        auditService.success(AuditActions.QUIZ_QUESTION_CREATED, AuditActions.RESOURCE_QUESTION, saved.getId(),
                Map.of("type", saved.getQuestionType()));
        return toDto(saved);
    }

    @Transactional(rollbackFor = Exception.class)
    public QuestionDto update(Long id, QuestionUpsertRequest r) {
        QuestionEntity q = requireVisible(id);
        access.requireOwner(q);
        requireVersion(q.getVersion(), r.getExpectedVersion(), QuestionEntity.class, id);
        List<QuestionUpsertRequest.Option> opts = validate(r);
        apply(q, r);
        q.setUpdatedBy(SecurityUtils.currentUsername());
        QuestionEntity saved = questionRepository.saveAndFlush(q);
        optionRepository.deleteByQuestionId(saved.getId());
        optionRepository.flush();
        saveOptions(saved.getId(), opts);
        auditService.success(AuditActions.QUIZ_QUESTION_UPDATED, AuditActions.RESOURCE_QUESTION, saved.getId(),
                Map.of("type", saved.getQuestionType()));
        return toDto(saved);
    }

    /** Xoá mềm - kể cả khi đang dùng trong đề đã xuất bản (đề dùng SNAPSHOT). */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        QuestionEntity q = requireVisible(id);
        access.requireOwner(q);
        q.setIsDeleted(PersistenceFlags.DELETED);
        q.setUpdatedBy(SecurityUtils.currentUsername());
        questionRepository.save(q);
        auditService.success(AuditActions.QUIZ_QUESTION_DELETED, AuditActions.RESOURCE_QUESTION, id, Map.of());
    }

    /** Tạo nhiều câu (đã kiểm tra) cho nhập Excel - 1 transaction với bên gọi. */
    void createValidated(List<QuestionUpsertRequest> requests) {
        for (QuestionUpsertRequest r : requests) {
            QuestionEntity q = new QuestionEntity();
            q.setOwnerId(access.currentUserId());
            q.setCreatedBy(SecurityUtils.currentUsername());
            q.setCreatedAt(QuizRules.now(clock));
            q.setIsDeleted(PersistenceFlags.NOT_DELETED);
            List<QuestionUpsertRequest.Option> opts = validate(r);
            apply(q, r);
            QuestionEntity saved = questionRepository.save(q);
            saveOptions(saved.getId(), opts);
        }
    }

    // ============================================================================ luật

    /**
     * BR-01..04: SINGLE 2-10 lựa chọn, đúng 1 đáp án đúng; TRUE_FALSE đúng 2 lựa chọn, 1 đúng; MULTI 2-10, >= 1 đúng;
     * SHORT_TEXT 0 lựa chọn, 1-10 đáp án chấp nhận; ESSAY không lựa chọn / đáp án. Sai -> 400 QUESTION_INVALID_OPTIONS.
     *
     * @return lựa chọn đã sắp xếp theo sortOrder.
     */
    static List<QuestionUpsertRequest.Option> validateOptions(QuestionUpsertRequest r) {
        String type = r.getType() == null ? "" : r.getType().strip().toUpperCase(Locale.ROOT);
        List<QuestionUpsertRequest.Option> opts = r.getOptions() == null ? List.of() : r.getOptions().stream()
                .filter(Objects::nonNull)
                .sorted((a, b) -> Integer.compare(a.getSortOrder() == null ? 0 : a.getSortOrder(),
                        b.getSortOrder() == null ? 0 : b.getSortOrder()))
                .toList();
        List<String> accepted = r.getAcceptedAnswers() == null ? List.of()
                : r.getAcceptedAnswers().stream().filter(s -> s != null && !s.isBlank()).toList();
        long correct = opts.stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).count();
        boolean ok = switch (type) {
            case QuestionType.SINGLE -> between(opts.size()) && correct == 1 && accepted.isEmpty();
            case QuestionType.TRUE_FALSE -> opts.size() == 2 && correct == 1 && accepted.isEmpty();
            case QuestionType.MULTI -> between(opts.size()) && correct >= 1 && accepted.isEmpty();
            case QuestionType.SHORT_TEXT -> opts.isEmpty() && !accepted.isEmpty()
                    && accepted.size() <= QuizConstants.ACCEPTED_ANSWERS_MAX
                    && accepted.stream().allMatch(a -> a.length() <= 500);
            case QuestionType.ESSAY -> opts.isEmpty() && accepted.isEmpty();
            default -> false;
        };
        if (!ok) {
            throw new OracleBusinessException(QuizErrorCodes.QUESTION_INVALID_OPTIONS, "Đáp án của câu hỏi không hợp lệ.");
        }
        return opts;
    }

    private List<QuestionUpsertRequest.Option> validate(QuestionUpsertRequest r) {
        if (r == null) {
            throw new OracleBusinessException(QuizErrorCodes.VALIDATION_ERROR, "Thiếu dữ liệu câu hỏi.");
        }
        return validateOptions(r);
    }

    private static boolean between(int n) {
        return n >= QuizConstants.OPTIONS_MIN && n <= QuizConstants.OPTIONS_MAX;
    }

    private void apply(QuestionEntity q, QuestionUpsertRequest r) {
        String type = r.getType().strip().toUpperCase(Locale.ROOT);
        q.setQuestionType(type);
        q.setSubjectName(r.getSubjectName().strip());
        q.setGradeLevel(r.getGradeLevel().strip());
        q.setTopic(r.getTopic().strip());
        q.setDifficulty(r.getDifficulty());
        q.setVisibility(r.getVisibility() == null || r.getVisibility().isBlank() ? Visibility.PRIVATE
                : r.getVisibility().strip().toUpperCase(Locale.ROOT));
        q.setCourseId(r.getCourseId());
        String content = sanitize(r.getContent());
        if (content == null) {
            throw new OracleBusinessException(QuizErrorCodes.VALIDATION_ERROR, "Nội dung câu hỏi không được để trống.");
        }
        q.setContent(content);
        q.setExplanation(sanitize(r.getExplanation()));
        q.setAcceptedAnswers(QuestionType.SHORT_TEXT.equals(type)
                ? json.write(r.getAcceptedAnswers().stream().filter(s -> s != null && !s.isBlank()).map(String::strip).toList())
                : null);
    }

    private void saveOptions(Long questionId, List<QuestionUpsertRequest.Option> opts) {
        int i = 0;
        List<QuestionOptionEntity> rows = new ArrayList<>();
        for (QuestionUpsertRequest.Option o : opts) {
            String c = sanitize(o.getContent());
            rows.add(QuestionOptionEntity.builder().questionId(questionId).content(c == null ? "" : c)
                    .isCorrect(QuizRules.flag(o.getIsCorrect())).sortOrder(i++).build());
        }
        if (!rows.isEmpty()) {
            optionRepository.saveAll(rows);
        }
    }

    QuestionEntity requireVisible(Long id) {
        QuestionEntity q = questionRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(QuestionServiceImpl::notFound);
        if (!access.canSee(q)) {
            throw notFound();
        }
        return q;
    }

    static OracleBusinessException notFound() {
        return new OracleBusinessException(QuizErrorCodes.QUESTION_NOT_FOUND, "Không tìm thấy câu hỏi.");
    }

    static void requireVersion(Long current, Long expected, Class<?> type, Long id) {
        if (expected == null) {
            throw new OracleBusinessException(QuizErrorCodes.VALIDATION_ERROR, "Thiếu expectedVersion.");
        }
        if (!Objects.equals(current, expected)) {
            throw new ObjectOptimisticLockingFailureException(type, id);
        }
    }

    /** Ảnh chụp câu hỏi (dùng khi publish). */
    QuizJson.Snapshot snapshot(QuestionEntity q, List<QuestionOptionEntity> options) {
        List<QuizJson.SnapOption> opts = options.stream()
                .map(o -> new QuizJson.SnapOption(o.getId(), o.getContent(), QuizRules.flag(o.getIsCorrect()))).toList();
        return new QuizJson.Snapshot(q.getId(), q.getQuestionType(), q.getTopic(), q.getDifficulty(), q.getContent(),
                q.getExplanation(), opts, json.strings(q.getAcceptedAnswers()), null);
    }

    Map<Long, List<QuestionOptionEntity>> optionsOf(Collection<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<QuestionOptionEntity>> out = new HashMap<>();
        for (List<Long> chunk : chunks(new ArrayList<>(questionIds))) {
            for (QuestionOptionEntity o : optionRepository.findByQuestionIdInOrderBySortOrderAscIdAsc(chunk)) {
                out.computeIfAbsent(o.getQuestionId(), k -> new ArrayList<>()).add(o);
            }
        }
        return out;
    }

    // ============================================================================ map

    private QuestionListItemDto toListItem(QuestionEntity q, Map<Long, Long> used, Map<Long, String> owners) {
        return QuestionListItemDto.builder().id(q.getId()).type(q.getQuestionType()).subjectName(q.getSubjectName())
                .gradeLevel(q.getGradeLevel()).topic(q.getTopic()).difficulty(q.getDifficulty())
                .visibility(q.getVisibility()).ownerId(q.getOwnerId()).ownerName(owners.get(q.getOwnerId()))
                .contentPreview(QuizRules.preview(q.getContent())).usedInQuizCount(used.getOrDefault(q.getId(), 0L))
                .updatedAt(q.getUpdatedAt() != null ? q.getUpdatedAt() : q.getCreatedAt()).build();
    }

    private QuestionDto toDto(QuestionEntity q) {
        Map<Long, Long> used = usedCounts(List.of(q.getId()));
        Map<Long, String> owners = ownerNames(List.of(q.getOwnerId()));
        List<QuestionDto.Option> options = optionRepository.findByQuestionIdOrderBySortOrderAscIdAsc(q.getId()).stream()
                .map(o -> QuestionDto.Option.builder().id(o.getId()).content(o.getContent())
                        .isCorrect(QuizRules.flag(o.getIsCorrect())).sortOrder(o.getSortOrder()).build())
                .toList();
        return QuestionDto.builder().id(q.getId()).type(q.getQuestionType()).subjectName(q.getSubjectName())
                .gradeLevel(q.getGradeLevel()).topic(q.getTopic()).difficulty(q.getDifficulty())
                .visibility(q.getVisibility()).ownerId(q.getOwnerId()).ownerName(owners.get(q.getOwnerId()))
                .contentPreview(QuizRules.preview(q.getContent())).usedInQuizCount(used.getOrDefault(q.getId(), 0L))
                .updatedAt(q.getUpdatedAt() != null ? q.getUpdatedAt() : q.getCreatedAt())
                .courseId(q.getCourseId()).content(q.getContent()).explanation(q.getExplanation()).options(options)
                .acceptedAnswers(json.strings(q.getAcceptedAnswers())).canEdit(access.canEdit(q)).version(q.getVersion())
                .build();
    }

    private Map<Long, Long> usedCounts(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> out = new HashMap<>();
        for (Object[] r : quizQuestionRepository.countQuizzesByQuestionIds(ids)) {
            out.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }
        return out;
    }

    private Map<Long, String> ownerNames(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(distinct).stream()
                .collect(Collectors.toMap(UserEntity::getId, u -> u.getFullName() == null ? u.getUsername() : u.getFullName(),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String clean = htmlContentSanitizer.sanitize(html);
        return clean == null || clean.isBlank() ? null : clean;
    }

    static <T> List<List<T>> chunks(List<T> list) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += QuizConstants.IN_BATCH) {
            out.add(list.subList(i, Math.min(list.size(), i + QuizConstants.IN_BATCH)));
        }
        return out;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
