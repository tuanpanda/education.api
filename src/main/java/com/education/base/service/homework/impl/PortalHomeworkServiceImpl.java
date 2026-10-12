package com.education.base.service.homework.impl;

import com.education.base.audit.AuditActions;
import com.education.base.common.HtmlContentSanitizer;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.file.FileConstants;
import com.education.base.common.homework.HomeworkConstants;
import com.education.base.common.homework.HomeworkConstants.AssignmentStatus;
import com.education.base.common.homework.HomeworkConstants.HistoryOutcome;
import com.education.base.common.homework.HomeworkConstants.PortalTab;
import com.education.base.common.homework.HomeworkConstants.SubmissionStatus;
import com.education.base.common.homework.HomeworkConstants.SubmissionType;
import com.education.base.common.homework.HomeworkErrorCodes;
import com.education.base.dto.request.PortalSubmissionDraftRequest;
import com.education.base.dto.response.PortalAssignmentDetailDto;
import com.education.base.dto.response.PortalAssignmentListItemDto;
import com.education.base.dto.response.PortalFileDto;
import com.education.base.dto.response.PortalHomeworkSummaryDto;
import com.education.base.dto.response.PortalSubmissionDto;
import com.education.base.dto.response.SubmissionHistoryDto;
import com.education.base.entity.AssignmentEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassSessionEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.FileEntity;
import com.education.base.entity.SubmissionEntity;
import com.education.base.entity.SubmissionHistoryEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AssignmentRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.SubmissionHistoryRepository;
import com.education.base.repository.SubmissionRepository;
import com.education.base.security.PortalStudentContext;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AuditService;
import com.education.base.service.FileStorageService;
import com.education.base.service.file.FileLifecycleService;
import com.education.base.service.homework.PortalHomeworkService;
import com.education.base.service.impl.StudentScopeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * GĐ2 - cổng học sinh ({@code /api/v1/portal/me/assignments}). Học sinh luôn lấy từ {@link PortalStudentContext};
 * mọi thứ không thuộc học sinh -> 404 {@code ASSIGNMENT_NOT_FOUND} / {@code PORTAL_FILE_NOT_FOUND}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalHomeworkServiceImpl implements PortalHomeworkService {

    static final String PORTAL_FILE_URL = "/api/v1/portal/me/files/";
    static final List<String> VISIBLE = List.of(AssignmentStatus.PUBLISHED, AssignmentStatus.CLOSED);
    static final int FILE_IDS_MAX = 400;

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionHistoryRepository historyRepository;
    private final ClassRepository classRepository;
    private final ClassSessionRepository classSessionRepository;
    private final ClassStudentRepository classStudentRepository;
    private final PortalStudentContext portalStudentContext;
    private final StudentScopeGuard studentScopeGuard;
    private final FileStorageService fileStorageService;
    private final FileLifecycleService fileLifecycleService;
    private final HtmlContentSanitizer htmlContentSanitizer;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<PortalAssignmentListItemDto> list(String tab) {
        String t = normalizeTab(tab);
        LocalDateTime now = HomeworkRules.now(clock);
        Comparator<Visible> order = switch (t) {
            case PortalTab.SUBMITTED -> Comparator.comparing((Visible v) -> v.s().getSubmittedAt(),
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case PortalTab.GRADED -> Comparator.comparing((Visible v) -> v.s().getGradedAt(),
                    Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing((Visible v) -> v.a().getDueAt());
        };
        return visibleItems(now).stream()
                .filter(v -> t.equals(v.tab()))
                .sorted(order.thenComparing(v -> v.a().getId()))
                .map(v -> toListItem(v, now))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PortalHomeworkSummaryDto summary() {
        LocalDateTime now = HomeworkRules.now(clock);
        LocalDateTime soon = now.plusDays(HomeworkConstants.DUE_SOON_DAYS);
        List<Visible> items = visibleItems(now);
        List<Visible> todo = items.stream().filter(v -> PortalTab.TODO.equals(v.tab())).toList();
        return PortalHomeworkSummaryDto.builder()
                .todoCount(todo.size())
                .dueSoonCount((int) todo.stream().filter(v -> !v.a().getDueAt().isAfter(soon)).count())
                .overdueCount((int) items.stream().filter(v -> PortalTab.OVERDUE.equals(v.tab())).count())
                .nextDueAt(todo.stream().map(v -> v.a().getDueAt()).filter(d -> !d.isBefore(now))
                        .min(Comparator.naturalOrder()).orElse(null))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PortalAssignmentDetailDto get(Long assignmentId) {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        AssignmentEntity a = requireVisible(assignmentId);
        SubmissionEntity s = findSubmission(a, studentId).orElse(null);
        return toDetail(a, s, HomeworkRules.now(clock));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortalSubmissionDto saveDraft(Long assignmentId, PortalSubmissionDraftRequest request) {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        AssignmentEntity a = requireVisible(assignmentId);
        LocalDateTime now = HomeworkRules.now(clock);
        SubmissionEntity s = findSubmission(a, studentId).orElse(null);
        requireEditable(a, s, now);
        String text = request == null ? null : request.getContentText();
        boolean hasText = text != null && !text.isBlank();
        if (SubmissionType.FILE.equals(a.getSubmissionType()) && hasText) {
            throw new OracleBusinessException(HomeworkErrorCodes.SUBMISSION_TYPE_MISMATCH,
                    "Bài này không nhận kiểu nộp này.");
        }
        if (text != null && text.length() > HomeworkConstants.CONTENT_TEXT_MAX) {
            throw new OracleBusinessException("VALIDATION_ERROR", "Bài làm tối đa 50.000 ký tự.");
        }
        if (s == null) {
            s = newDraft(a, studentId, now);
        }
        s.setContentText(hasText ? sanitizeOrNull(text) : null);
        s.setUpdatedBy(SecurityUtils.currentUsername());
        SubmissionEntity saved = submissionRepository.saveAndFlush(s);
        return toSubmissionDto(a, saved, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortalFileDto uploadFile(Long assignmentId, MultipartFile file) {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        AssignmentEntity a = requireVisible(assignmentId);
        if (SubmissionType.TEXT.equals(a.getSubmissionType())) {
            throw new OracleBusinessException(HomeworkErrorCodes.SUBMISSION_TYPE_MISMATCH,
                    "Bài này không nhận kiểu nộp này.");
        }
        LocalDateTime now = HomeworkRules.now(clock);
        SubmissionEntity s = findSubmission(a, studentId).orElse(null);
        requireEditable(a, s, now);
        if (s == null) {
            s = submissionRepository.saveAndFlush(newDraft(a, studentId, now));
        }
        int max = a.getMaxFiles() == null ? 0 : a.getMaxFiles();
        if (fileStorageService.getFilesByRef(FileConstants.Module.SUBMISSION, s.getId()).size() >= max) {
            throw new OracleBusinessException(HomeworkErrorCodes.SUBMISSION_TOO_MANY_FILES,
                    "Bài này chỉ cho tối đa " + max + " file.");
        }
        FileEntity saved = fileStorageService.storeFile(file, FileConstants.Module.SUBMISSION, s.getId());
        return toPortalFile(saved, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long assignmentId, Long fileId) {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        AssignmentEntity a = requireVisible(assignmentId);
        SubmissionEntity s = findSubmission(a, studentId).orElseThrow(PortalHomeworkServiceImpl::fileNotFound);
        FileEntity f;
        try {
            f = fileStorageService.getFileEntity(fileId);
        } catch (OracleBusinessException e) {
            throw fileNotFound();
        }
        if (!FileConstants.Module.SUBMISSION.equals(f.getModuleName()) || !Objects.equals(s.getId(), f.getReferenceId())) {
            throw fileNotFound();
        }
        requireEditable(a, s, HomeworkRules.now(clock));
        fileLifecycleService.softDelete(fileId, "Học sinh gỡ file bài làm");
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("module", FileConstants.Module.SUBMISSION);
        d.put("refId", s.getId());
        d.put("size", f.getFileSize());
        auditService.success(AuditActions.FILE_DELETED, AuditActions.RESOURCE_FILE, fileId, d);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortalSubmissionDto submit(Long assignmentId) {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        AssignmentEntity a = requireVisible(assignmentId);
        LocalDateTime now = HomeworkRules.now(clock);
        SubmissionEntity s = findSubmission(a, studentId).orElse(null);
        if (s != null && !HomeworkRules.editableStatus(s)) {
            throw locked();
        }
        if (!HomeworkRules.withinWindow(a, s, now)) {
            throw deadlinePassed();
        }
        if (s == null) {
            throw empty();
        }
        List<FileEntity> files = fileStorageService.getFilesByRef(FileConstants.Module.SUBMISSION, s.getId());
        boolean hasText = hasText(s.getContentText());
        boolean hasFiles = !files.isEmpty();
        boolean ok = switch (a.getSubmissionType()) {
            case SubmissionType.TEXT -> hasText;
            case SubmissionType.FILE -> hasFiles;
            default -> hasText || hasFiles;
        };
        if (!ok) {
            throw empty();
        }
        boolean late = HomeworkRules.late(a, now);
        int attempt = (s.getAttemptCount() == null ? 0 : s.getAttemptCount()) + 1;
        s.setAttemptCount(attempt);
        if (s.getFirstSubmittedAt() == null) {
            s.setFirstSubmittedAt(now);
        }
        s.setSubmittedAt(now);
        s.setIsLate(late ? 1 : 0);
        s.setStatus(SubmissionStatus.SUBMITTED);
        s.setScore(null);
        s.setUpdatedBy(SecurityUtils.currentUsername());
        SubmissionEntity saved = submissionRepository.saveAndFlush(s);
        String ids = files.stream().map(f -> String.valueOf(f.getId())).collect(Collectors.joining(","));
        historyRepository.save(SubmissionHistoryEntity.builder()
                .submissionId(saved.getId())
                .attemptNo(attempt)
                .contentSnapshot(saved.getContentText())
                .fileIds(ids.isEmpty() ? null : HomeworkServiceImpl.truncate(ids, FILE_IDS_MAX))
                .submittedAt(now)
                .isLate(late ? 1 : 0)
                .outcome(HistoryOutcome.SUBMITTED)
                .createdAt(now)
                .createdBy(SecurityUtils.currentUsername())
                .build());
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("assignmentId", a.getId());
        d.put("attempt", attempt);
        d.put("late", late);
        d.put("files", files.size());
        auditService.success(AuditActions.HOMEWORK_SUBMITTED, AuditActions.RESOURCE_SUBMISSION, saved.getId(), d);
        return toSubmissionDto(a, saved, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortalSubmissionDto unsubmit(Long assignmentId) {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        AssignmentEntity a = requireVisible(assignmentId);
        LocalDateTime now = HomeworkRules.now(clock);
        SubmissionEntity s = findSubmission(a, studentId).orElseThrow(PortalHomeworkServiceImpl::locked);
        if (!SubmissionStatus.SUBMITTED.equals(s.getStatus()) || !HomeworkRules.flag(a.getAllowResubmit())) {
            throw locked();
        }
        if (!HomeworkRules.withinWindow(a, s, now)) {
            throw deadlinePassed();
        }
        s.setStatus(SubmissionStatus.DRAFT);
        s.setUpdatedBy(SecurityUtils.currentUsername());
        return toSubmissionDto(a, submissionRepository.saveAndFlush(s), now);
    }

    // ============================================================================================ nội bộ

    record Visible(AssignmentEntity a, SubmissionEntity s, String tab, ClassEntity clazz) {
    }

    private List<Visible> visibleItems(LocalDateTime now) {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        Map<Long, ClassEntity> classes = new LinkedHashMap<>();
        for (ClassStudentEntity cs : classStudentRepository.findActiveEnrollmentsWithClass(studentId)) {
            classes.putIfAbsent(cs.getClassId(), cs.getClazz());
        }
        if (classes.isEmpty()) {
            return List.of();
        }
        List<AssignmentEntity> assignments = assignmentRepository.findByClassIdInAndIsDeletedAndStatusIn(
                classes.keySet(), PersistenceFlags.NOT_DELETED, VISIBLE);
        if (assignments.isEmpty()) {
            return List.of();
        }
        Map<Long, SubmissionEntity> subs = submissionRepository.findByStudentIdAndAssignmentIdInAndIsDeleted(
                        studentId, assignments.stream().map(AssignmentEntity::getId).toList(), PersistenceFlags.NOT_DELETED)
                .stream().collect(Collectors.toMap(SubmissionEntity::getAssignmentId, Function.identity(), (x, y) -> x));
        return assignments.stream().map(a -> {
            SubmissionEntity s = subs.get(a.getId());
            return new Visible(a, s, HomeworkRules.tab(a, s, now), classes.get(a.getClassId()));
        }).toList();
    }

    private PortalAssignmentListItemDto toListItem(Visible v, LocalDateTime now) {
        AssignmentEntity a = v.a();
        SubmissionEntity s = v.s();
        boolean graded = s != null && SubmissionStatus.GRADED.equals(s.getStatus());
        return PortalAssignmentListItemDto.builder()
                .id(a.getId())
                .classId(a.getClassId())
                .className(v.clazz() == null ? null : v.clazz().getClassName())
                .title(a.getTitle())
                .submissionType(a.getSubmissionType())
                .maxScore(a.getMaxScore())
                .dueAt(a.getDueAt())
                .allowLate(HomeworkRules.flag(a.getAllowLate()))
                .lateUntil(a.getLateUntil())
                .assignmentStatus(a.getStatus())
                .submissionStatus(s == null ? null : s.getStatus())
                .tab(v.tab())
                .submittedAt(s == null ? null : s.getSubmittedAt())
                .late(s != null && HomeworkRules.flag(s.getIsLate()))
                .score(graded ? s.getScore() : null)
                .overdue(now.isAfter(a.getDueAt()))
                .canSubmit(HomeworkRules.canEdit(a, s, now))
                .build();
    }

    private PortalAssignmentDetailDto toDetail(AssignmentEntity a, SubmissionEntity s, LocalDateTime now) {
        ClassEntity clazz = classRepository.findById(a.getClassId()).orElse(null);
        ClassSessionEntity session = a.getSessionId() == null ? null
                : classSessionRepository.findById(a.getSessionId()).orElse(null);
        boolean canEdit = HomeworkRules.canEdit(a, s, now);
        return PortalAssignmentDetailDto.builder()
                .id(a.getId())
                .classId(a.getClassId())
                .className(clazz == null ? null : clazz.getClassName())
                .sessionDate(session == null ? null : session.getSessionDate())
                .title(a.getTitle())
                .description(a.getDescription())
                .submissionType(a.getSubmissionType())
                .maxScore(a.getMaxScore())
                .dueAt(a.getDueAt())
                .allowLate(HomeworkRules.flag(a.getAllowLate()))
                .lateUntil(a.getLateUntil())
                .maxFiles(a.getMaxFiles())
                .allowResubmit(HomeworkRules.flag(a.getAllowResubmit()))
                .assignmentStatus(a.getStatus())
                .tab(HomeworkRules.tab(a, s, now))
                .overdue(now.isAfter(a.getDueAt()))
                .canEdit(canEdit)
                .canSubmit(canEdit)
                .willBeLate(now.isAfter(a.getDueAt()))
                .serverNow(now)
                .attachments(fileStorageService.getFilesByRef(FileConstants.Module.ASSIGNMENT, a.getId()).stream()
                        .map(f -> toPortalFile(f, false)).toList())
                .mySubmission(s == null ? null : toSubmissionDto(a, s, now))
                .build();
    }

    private PortalSubmissionDto toSubmissionDto(AssignmentEntity a, SubmissionEntity s, LocalDateTime now) {
        boolean canEdit = HomeworkRules.canEdit(a, s, now);
        boolean graded = SubmissionStatus.GRADED.equals(s.getStatus());
        boolean showFeedback = graded || SubmissionStatus.RETURNED.equals(s.getStatus());
        return PortalSubmissionDto.builder()
                .id(s.getId())
                .status(s.getStatus())
                .contentText(s.getContentText())
                .attemptCount(s.getAttemptCount())
                .submittedAt(s.getSubmittedAt())
                .late(HomeworkRules.flag(s.getIsLate()))
                .score(graded ? s.getScore() : null)
                .feedback(showFeedback ? s.getFeedback() : null)
                .returnReason(SubmissionStatus.RETURNED.equals(s.getStatus()) ? s.getReturnReason() : null)
                .gradedAt(graded ? s.getGradedAt() : null)
                .files(s.getId() == null ? List.of() : fileStorageService.getFilesByRef(FileConstants.Module.SUBMISSION,
                        s.getId()).stream().map(f -> toPortalFile(f, canEdit)).toList())
                .feedbackFiles(s.getId() == null || !showFeedback ? List.of()
                        : fileStorageService.getFilesByRef(FileConstants.Module.FEEDBACK, s.getId()).stream()
                        .map(f -> toPortalFile(f, false)).toList())
                .history(s.getId() == null ? List.of()
                        : historyRepository.findBySubmissionIdOrderByAttemptNoDesc(s.getId()).stream()
                        .map(PortalHomeworkServiceImpl::toPortalHistory).toList())
                .build();
    }

    /** Lịch sử phía cổng: điểm chỉ hiện ở lần đã chấm. */
    static SubmissionHistoryDto toPortalHistory(SubmissionHistoryEntity h) {
        SubmissionHistoryDto dto = HomeworkServiceImpl.toHistoryDto(h);
        if (!HistoryOutcome.GRADED.equals(h.getOutcome())) {
            dto.setScore(null);
        }
        return dto;
    }

    static PortalFileDto toPortalFile(FileEntity f, boolean canDelete) {
        return PortalFileDto.builder()
                .id(f.getId())
                .originalName(f.getOriginalName())
                .contentType(f.getContentType())
                .fileSize(f.getFileSize())
                .fileExt(f.getFileExt())
                .viewUrl(PORTAL_FILE_URL + f.getId())
                .downloadUrl(PORTAL_FILE_URL + f.getId() + "?download=true")
                .createdAt(f.getCreatedAt())
                .canDelete(canDelete)
                .build();
    }

    /** Bài PUBLISHED/CLOSED, chưa xoá, thuộc lớp học sinh đang ENROLLED; sai -> 404 ASSIGNMENT_NOT_FOUND. */
    AssignmentEntity requireVisible(Long assignmentId) {
        AssignmentEntity a = assignmentId == null ? null
                : assignmentRepository.findByIdAndIsDeleted(assignmentId, PersistenceFlags.NOT_DELETED)
                .filter(x -> VISIBLE.contains(x.getStatus())).orElse(null);
        if (a == null) {
            throw notFound();
        }
        try {
            studentScopeGuard.requireEnrolled(a.getClassId());
        } catch (OracleBusinessException e) {
            throw notFound();
        }
        return a;
    }

    private Optional<SubmissionEntity> findSubmission(AssignmentEntity a, Long studentId) {
        return submissionRepository.findByAssignmentIdAndStudentIdAndIsDeleted(a.getId(), studentId,
                PersistenceFlags.NOT_DELETED);
    }

    private SubmissionEntity newDraft(AssignmentEntity a, Long studentId, LocalDateTime now) {
        return SubmissionEntity.builder()
                .assignmentId(a.getId())
                .studentId(studentId)
                .status(SubmissionStatus.DRAFT)
                .attemptCount(0)
                .isLate(0)
                .isDeleted(PersistenceFlags.NOT_DELETED)
                .createdAt(now)
                .createdBy(SecurityUtils.currentUsername())
                .build();
    }

    private static void requireEditable(AssignmentEntity a, SubmissionEntity s, LocalDateTime now) {
        if (!HomeworkRules.editableStatus(s)) {
            throw locked();
        }
        if (!HomeworkRules.withinWindow(a, s, now)) {
            throw deadlinePassed();
        }
    }

    static String normalizeTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return PortalTab.TODO;
        }
        String t = tab.trim().toLowerCase(Locale.ROOT);
        if (!t.matches(PortalTab.PATTERN)) {
            throw new OracleBusinessException("VALIDATION_ERROR",
                    "Tab không hợp lệ: chỉ nhận todo, submitted, graded, overdue.");
        }
        return t;
    }

    private static boolean hasText(String html) {
        if (html == null) {
            return false;
        }
        String plain = html.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ").replace('\u00a0', ' ');
        return !plain.isBlank();
    }

    private String sanitizeOrNull(String html) {
        String clean = htmlContentSanitizer.sanitize(html);
        return clean == null || clean.isBlank() ? null : clean;
    }

    private static OracleBusinessException notFound() {
        return new OracleBusinessException(HomeworkErrorCodes.ASSIGNMENT_NOT_FOUND, "Không tìm thấy bài tập.");
    }

    private static OracleBusinessException fileNotFound() {
        return new OracleBusinessException(HomeworkErrorCodes.PORTAL_FILE_NOT_FOUND, "Không tìm thấy file.");
    }

    static OracleBusinessException locked() {
        return new OracleBusinessException(HomeworkErrorCodes.SUBMISSION_LOCKED,
                "Bài đã nộp/đã chấm, không sửa được.");
    }

    static OracleBusinessException deadlinePassed() {
        return new OracleBusinessException(HomeworkErrorCodes.SUBMISSION_DEADLINE_PASSED, "Đã hết hạn nộp bài.");
    }

    private static OracleBusinessException empty() {
        return new OracleBusinessException(HomeworkErrorCodes.SUBMISSION_EMPTY,
                "Em chưa làm bài hoặc chưa đính kèm file.");
    }
}
