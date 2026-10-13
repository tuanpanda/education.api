package com.education.base.service.homework.impl;

import com.education.base.audit.AuditActions;
import com.education.base.common.HtmlContentSanitizer;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.file.FileConstants;
import com.education.base.common.file.FileErrorCodes;
import com.education.base.common.homework.HomeworkConstants;
import com.education.base.common.homework.HomeworkConstants.AssignmentStatus;
import com.education.base.common.homework.HomeworkConstants.HistoryOutcome;
import com.education.base.common.homework.HomeworkConstants.MatrixState;
import com.education.base.common.homework.HomeworkConstants.SubmissionStatus;
import com.education.base.common.homework.HomeworkConstants.SubmissionType;
import com.education.base.common.homework.HomeworkErrorCodes;
import com.education.base.dto.request.AssignmentFilterRequest;
import com.education.base.dto.request.AssignmentUpsertRequest;
import com.education.base.dto.request.GradeSubmissionRequest;
import com.education.base.dto.request.ReturnSubmissionRequest;
import com.education.base.dto.response.AnnouncementClassOptionDto;
import com.education.base.dto.response.AssignmentDto;
import com.education.base.dto.response.AssignmentStatsDto;
import com.education.base.dto.response.FileResponseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.SubmissionDetailDto;
import com.education.base.dto.response.SubmissionHistoryDto;
import com.education.base.dto.response.SubmissionMatrixDto;
import com.education.base.dto.response.SubmissionRowDto;
import com.education.base.entity.AssignmentEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassSessionEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.FileEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.SubmissionEntity;
import com.education.base.entity.SubmissionHistoryEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapper;
import com.education.base.repository.AssignmentRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.SubmissionHistoryRepository;
import com.education.base.repository.SubmissionRepository;
import com.education.base.repository.UserRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.Permissions;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AuditService;
import com.education.base.service.FileStorageService;
import com.education.base.service.file.FileLifecycleService;
import com.education.base.service.file.FileLimitService;
import com.education.base.service.homework.HomeworkGradebookSync;
import com.education.base.service.homework.HomeworkService;
import com.education.base.service.impl.TeachingAssignmentGuard;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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

/** GĐ2 - nghiệp vụ bài tập phía nhân viên ({@code /api/v1/lms/assignments}). Hợp đồng: docs/homework-api.md §2. */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeworkServiceImpl implements HomeworkService {

    static final String MSG_NOT_CLASS_TEACHER = "Bạn không phụ trách lớp này.";
    static final String CLASS_NOT_FOUND = "CLASS_NOT_FOUND";
    static final List<String> ACTIVE_CLASS_STATUSES = List.of("PLANNED", "OPEN", "ONGOING");
    static final int MAX_PAGE_SIZE = 100;
    private static final String SESSION_CANCELLED = "CANCELLED";

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionHistoryRepository historyRepository;
    private final ClassRepository classRepository;
    private final ClassSessionRepository classSessionRepository;
    private final ClassStudentRepository classStudentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final FileLifecycleService fileLifecycleService;
    private final FileLimitService fileLimitService;
    private final FileMapper fileMapper;
    private final TeachingAssignmentGuard teachingAssignmentGuard;
    private final HtmlContentSanitizer htmlContentSanitizer;
    private final AuditService auditService;
    private final HomeworkGradebookSync gradebookSync;
    private final Clock clock;

    // ============================================================================================ bài tập

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AssignmentDto> search(AssignmentFilterRequest filter) {
        AssignmentFilterRequest f = filter == null ? new AssignmentFilterRequest() : filter;
        int pageNo = Math.max(f.getPageNo() == null ? 1 : f.getPageNo(), 1);
        int pageSize = Math.min(Math.max(f.getPageSize() == null ? 20 : f.getPageSize(), 1), MAX_PAGE_SIZE);
        Optional<Set<Long>> taught = teachingAssignmentGuard.taughtClassIdsIfRestricted();
        if (taught.isPresent() && (taught.get().isEmpty()
                || (f.getClassId() != null && !taught.get().contains(f.getClassId())))) {
            return PageResponse.of(List.of(), pageNo, pageSize, 0);
        }
        Specification<AssignmentEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("isDeleted"), PersistenceFlags.NOT_DELETED));
            if (f.getClassId() != null) {
                p.add(cb.equal(root.get("classId"), f.getClassId()));
            }
            if (f.getStatus() != null && !f.getStatus().isBlank()) {
                p.add(cb.equal(root.get("status"), f.getStatus().trim().toUpperCase(Locale.ROOT)));
            }
            if (f.getKeyword() != null && !f.getKeyword().isBlank()) {
                p.add(cb.like(cb.lower(root.get("title")),
                        "%" + f.getKeyword().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            taught.ifPresent(ids -> p.add(root.get("classId").in(ids)));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<AssignmentEntity> page = assignmentRepository.findAll(spec,
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Order.desc("dueAt"), Sort.Order.desc("id"))));
        LocalDateTime now = HomeworkRules.now(clock);
        List<AssignmentDto> content = page.getContent().stream()
                .map(a -> {
                    AssignmentDto dto = toDto(a, false, taught);
                    dto.setStats(buildMatrixRows(a, now).stats());
                    return dto;
                })
                .toList();
        return PageResponse.of(content, pageNo, pageSize, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentDto getById(Long id) {
        AssignmentEntity a = requireAssignment(id);
        requireScope(a.getClassId());
        AssignmentDto dto = toDto(a, true, teachingAssignmentGuard.taughtClassIdsIfRestricted());
        dto.setStats(buildMatrixRows(a, HomeworkRules.now(clock)).stats());
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssignmentDto create(AssignmentUpsertRequest request) {
        AssignmentUpsertRequest r = requirePayload(request);
        ClassEntity clazz = requireScope(r.getClassId());
        AssignmentEntity a = new AssignmentEntity();
        a.setClassId(clazz.getId());
        apply(a, r);
        a.setStatus(AssignmentStatus.DRAFT);
        a.setIsDeleted(PersistenceFlags.NOT_DELETED);
        a.setCreatedBy(SecurityUtils.currentUsername());
        a.setCreatedAt(HomeworkRules.now(clock));
        AssignmentEntity saved = assignmentRepository.saveAndFlush(a);
        audit(AuditActions.HOMEWORK_ASSIGNMENT_CREATED, saved, Map.of("classId", saved.getClassId()));
        return toDto(saved, true, teachingAssignmentGuard.taughtClassIdsIfRestricted());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssignmentDto update(Long id, AssignmentUpsertRequest request) {
        AssignmentUpsertRequest r = requirePayload(request);
        AssignmentEntity a = requireAssignment(id);
        requireScope(a.getClassId());
        boolean classChanged = !Objects.equals(a.getClassId(), r.getClassId());
        if (classChanged) {
            if (!AssignmentStatus.DRAFT.equals(a.getStatus())) {
                throw invalidState("Không đổi được lớp sau khi đã đăng bài.");
            }
            requireScope(r.getClassId());
        }
        String newType = r.getSubmissionType() == null ? null : r.getSubmissionType().trim().toUpperCase(Locale.ROOT);
        if (!Objects.equals(a.getSubmissionType(), newType)
                && submissionRepository.existsByAssignmentIdAndIsDeleted(a.getId(), PersistenceFlags.NOT_DELETED)) {
            throw invalidState("Không đổi được kiểu nộp bài khi đã có học sinh làm bài.");
        }
        BigDecimal oldMax = a.getMaxScore();
        BigDecimal oldWeight = a.getGradeWeight();
        Integer oldSync = a.getSyncToGradebook();
        String oldTitle = a.getTitle();
        LocalDateTime oldDue = a.getDueAt();
        a.setClassId(r.getClassId());
        apply(a, r);
        a.setUpdatedBy(SecurityUtils.currentUsername());
        AssignmentEntity saved = assignmentRepository.saveAndFlush(a);
        boolean gradebookChanged = !sameNumber(oldMax, saved.getMaxScore()) || !sameNumber(oldWeight, saved.getGradeWeight())
                || !Objects.equals(oldSync, saved.getSyncToGradebook()) || !Objects.equals(oldTitle, saved.getTitle())
                || !Objects.equals(oldDue, saved.getDueAt());
        if (gradebookChanged && (HomeworkRules.flag(saved.getSyncToGradebook()) || HomeworkRules.flag(oldSync))) {
            safeSync(saved, null, () -> gradebookSync.resyncAssignment(saved));
        }
        audit(AuditActions.HOMEWORK_ASSIGNMENT_UPDATED, saved, Map.of("classId", saved.getClassId()));
        return toDto(saved, true, teachingAssignmentGuard.taughtClassIdsIfRestricted());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssignmentDto publish(Long id) {
        AssignmentEntity a = requireAssignment(id);
        requireScope(a.getClassId());
        String before = a.getStatus();
        if (!AssignmentStatus.DRAFT.equals(before) && !AssignmentStatus.CLOSED.equals(before)) {
            throw invalidState("Chỉ đăng được bài nháp hoặc mở lại bài đã đóng.");
        }
        a.setStatus(AssignmentStatus.PUBLISHED);
        if (a.getPublishedAt() == null) {
            a.setPublishedAt(HomeworkRules.now(clock));
        }
        a.setClosedAt(null);
        a.setUpdatedBy(SecurityUtils.currentUsername());
        AssignmentEntity saved = assignmentRepository.saveAndFlush(a);
        audit(AssignmentStatus.CLOSED.equals(before) ? AuditActions.HOMEWORK_ASSIGNMENT_REOPENED
                : AuditActions.HOMEWORK_ASSIGNMENT_PUBLISHED, saved, Map.of("classId", saved.getClassId()));
        return toDto(saved, true, teachingAssignmentGuard.taughtClassIdsIfRestricted());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssignmentDto close(Long id) {
        AssignmentEntity a = requireAssignment(id);
        requireScope(a.getClassId());
        if (!AssignmentStatus.PUBLISHED.equals(a.getStatus())) {
            throw invalidState("Chỉ đóng được bài đang mở.");
        }
        a.setStatus(AssignmentStatus.CLOSED);
        a.setClosedAt(HomeworkRules.now(clock));
        a.setUpdatedBy(SecurityUtils.currentUsername());
        AssignmentEntity saved = assignmentRepository.saveAndFlush(a);
        audit(AuditActions.HOMEWORK_ASSIGNMENT_CLOSED, saved, Map.of("classId", saved.getClassId()));
        return toDto(saved, true, teachingAssignmentGuard.taughtClassIdsIfRestricted());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(Long id) {
        AssignmentEntity a = requireAssignment(id);
        requireScope(a.getClassId());
        List<SubmissionEntity> subs = submissionRepository.findByAssignmentIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED);
        if (subs.stream().anyMatch(s -> !SubmissionStatus.DRAFT.equals(s.getStatus()))) {
            throw new OracleBusinessException(HomeworkErrorCodes.ASSIGNMENT_HAS_SUBMISSIONS,
                    "Bài đã có học sinh nộp, hãy đóng bài thay vì xoá.");
        }
        String user = SecurityUtils.currentUsername();
        for (SubmissionEntity s : subs) {
            softDeleteFiles(FileConstants.Module.SUBMISSION, s.getId(), "Xoá bài tập " + id);
            s.setIsDeleted(PersistenceFlags.DELETED);
            s.setUpdatedBy(user);
            submissionRepository.save(s);
        }
        softDeleteFiles(FileConstants.Module.ASSIGNMENT, id, "Xoá bài tập " + id);
        a.setIsDeleted(PersistenceFlags.DELETED);
        a.setUpdatedBy(user);
        AssignmentEntity saved = assignmentRepository.saveAndFlush(a);
        if (HomeworkRules.flag(saved.getSyncToGradebook())) {
            safeSync(saved, null, () -> gradebookSync.resyncAssignment(saved));
        }
        audit(AuditActions.HOMEWORK_ASSIGNMENT_DELETED, saved, Map.of("classId", saved.getClassId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnnouncementClassOptionDto> listManageableClasses() {
        List<ClassEntity> classes = teachingAssignmentGuard.taughtClassesIfRestricted()
                .orElseGet(() -> classRepository.findByStatusInAndIsDeletedOrderByCreatedAtDesc(
                        ACTIVE_CLASS_STATUSES, PersistenceFlags.NOT_DELETED));
        return classes.stream()
                .map(c -> AnnouncementClassOptionDto.builder()
                        .id(c.getId()).classCode(c.getClassCode()).className(c.getClassName()).build())
                .toList();
    }

    // ============================================================================================ file đề

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileResponseDto uploadAttachment(Long id, MultipartFile file) {
        AssignmentEntity a = requireAssignment(id);
        requireScope(a.getClassId());
        FileEntity saved = fileStorageService.storeFile(file, FileConstants.Module.ASSIGNMENT, a.getId());
        return toFileDto(saved, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAttachment(Long id, Long fileId) {
        AssignmentEntity a = requireAssignment(id);
        requireScope(a.getClassId());
        deleteOwnedFile(FileConstants.Module.ASSIGNMENT, a.getId(), fileId, "Gỡ file đề bài");
    }

    // ============================================================================================ bài nộp

    @Override
    @Transactional(readOnly = true)
    public SubmissionMatrixDto getSubmissionMatrix(Long id) {
        AssignmentEntity a = requireAssignment(id);
        requireScope(a.getClassId());
        Matrix m = buildMatrixRows(a, HomeworkRules.now(clock));
        AssignmentDto dto = toDto(a, false, teachingAssignmentGuard.taughtClassIdsIfRestricted());
        dto.setStats(m.stats());
        return SubmissionMatrixDto.builder().assignment(dto).stats(m.stats()).rows(m.rows()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public SubmissionDetailDto getSubmission(Long submissionId) {
        SubmissionEntity s = requireSubmission(submissionId);
        AssignmentEntity a = requireAssignmentOfSubmission(s);
        requireScope(a.getClassId());
        return toDetail(s, a);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmissionDetailDto grade(Long submissionId, GradeSubmissionRequest request) {
        if (request == null || request.getScore() == null || request.getExpectedVersion() == null) {
            throw new OracleBusinessException("VALIDATION_ERROR", "Thiếu điểm hoặc phiên bản bài nộp.");
        }
        SubmissionEntity s = requireSubmission(submissionId);
        AssignmentEntity a = requireAssignmentOfSubmission(s);
        requireScope(a.getClassId());
        requireVersion(s, request.getExpectedVersion());
        if (!List.of(SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED, SubmissionStatus.RETURNED)
                .contains(s.getStatus())) {
            throw new OracleBusinessException(HomeworkErrorCodes.SUBMISSION_INVALID_STATE, "Học sinh chưa nộp bài.");
        }
        BigDecimal score = request.getScore();
        if (score.signum() < 0 || score.compareTo(a.getMaxScore()) > 0) {
            throw new OracleBusinessException(HomeworkErrorCodes.GRADE_SCORE_OUT_OF_RANGE,
                    "Điểm phải từ 0 đến " + a.getMaxScore().stripTrailingZeros().toPlainString() + ".");
        }
        LocalDateTime now = HomeworkRules.now(clock);
        Long userId = SecurityUtils.currentUser().map(AuthUserPrincipal::getId).orElse(null);
        String feedback = sanitizeOrNull(request.getFeedback());
        s.setScore(score);
        s.setFeedback(feedback);
        s.setStatus(SubmissionStatus.GRADED);
        s.setGradedBy(userId);
        s.setGradedAt(now);
        s.setUpdatedBy(SecurityUtils.currentUsername());
        SubmissionEntity saved = submissionRepository.saveAndFlush(s);
        latestHistory(saved.getId()).ifPresent(h -> {
            h.setOutcome(HistoryOutcome.GRADED);
            h.setScore(score);
            h.setFeedback(feedback);
            h.setActedBy(userId);
            h.setActedAt(now);
            historyRepository.save(h);
        });
        auditService.success(AuditActions.HOMEWORK_GRADED, AuditActions.RESOURCE_SUBMISSION, saved.getId(),
                details(a, saved, Map.of("score", score)));
        safeSync(a, saved, () -> gradebookSync.onGraded(a, saved));
        return toDetail(saved, a);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmissionDetailDto returnSubmission(Long submissionId, ReturnSubmissionRequest request) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()
                || request.getExpectedVersion() == null) {
            throw new OracleBusinessException("VALIDATION_ERROR", "Vui lòng nhập lý do trả bài.");
        }
        SubmissionEntity s = requireSubmission(submissionId);
        AssignmentEntity a = requireAssignmentOfSubmission(s);
        requireScope(a.getClassId());
        requireVersion(s, request.getExpectedVersion());
        if (!SubmissionStatus.SUBMITTED.equals(s.getStatus()) && !SubmissionStatus.GRADED.equals(s.getStatus())) {
            throw new OracleBusinessException(HomeworkErrorCodes.SUBMISSION_INVALID_STATE,
                    "Chỉ trả được bài đã nộp hoặc đã chấm.");
        }
        LocalDateTime now = HomeworkRules.now(clock);
        Long userId = SecurityUtils.currentUser().map(AuthUserPrincipal::getId).orElse(null);
        String reason = truncate(request.getReason().strip(), HomeworkConstants.RETURN_REASON_MAX);
        s.setStatus(SubmissionStatus.RETURNED);
        s.setScore(null);
        s.setReturnReason(reason);
        s.setReturnedAt(now);
        s.setUpdatedBy(SecurityUtils.currentUsername());
        SubmissionEntity saved = submissionRepository.saveAndFlush(s);
        latestHistory(saved.getId()).ifPresent(h -> {
            h.setOutcome(HistoryOutcome.RETURNED);
            h.setScore(null);
            h.setActedBy(userId);
            h.setActedAt(now);
            historyRepository.save(h);
        });
        auditService.success(AuditActions.HOMEWORK_RETURNED, AuditActions.RESOURCE_SUBMISSION, saved.getId(),
                details(a, saved, Map.of()));
        safeSync(a, saved, () -> gradebookSync.onUngraded(a, saved));
        return toDetail(saved, a);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileResponseDto uploadFeedbackFile(Long submissionId, MultipartFile file) {
        SubmissionEntity s = requireSubmission(submissionId);
        AssignmentEntity a = requireAssignmentOfSubmission(s);
        requireScope(a.getClassId());
        if (SubmissionStatus.DRAFT.equals(s.getStatus())) {
            throw new OracleBusinessException(HomeworkErrorCodes.SUBMISSION_INVALID_STATE, "Học sinh chưa nộp bài.");
        }
        FileEntity saved = fileStorageService.storeFile(file, FileConstants.Module.FEEDBACK, s.getId());
        return toFileDto(saved, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFeedbackFile(Long submissionId, Long fileId) {
        SubmissionEntity s = requireSubmission(submissionId);
        AssignmentEntity a = requireAssignmentOfSubmission(s);
        requireScope(a.getClassId());
        deleteOwnedFile(FileConstants.Module.FEEDBACK, s.getId(), fileId, "Gỡ file nhận xét");
    }

    // ============================================================================================ nội bộ

    record Matrix(List<SubmissionRowDto> rows, AssignmentStatsDto stats) {
    }

    Matrix buildMatrixRows(AssignmentEntity a, LocalDateTime now) {
        List<SubmissionEntity> subs = submissionRepository.findByAssignmentIdAndIsDeleted(a.getId(),
                PersistenceFlags.NOT_DELETED);
        Map<Long, SubmissionEntity> byStudent = new LinkedHashMap<>();
        for (SubmissionEntity s : subs) {
            byStudent.put(s.getStudentId(), s);
        }
        Set<Long> studentIds = new LinkedHashSet<>();
        for (ClassStudentEntity cs : classStudentRepository.findByClassIdAndIsDeleted(a.getClassId(),
                PersistenceFlags.NOT_DELETED)) {
            if ("ENROLLED".equals(cs.getStatus())) {
                studentIds.add(cs.getStudentId());
            }
        }
        studentIds.addAll(byStudent.keySet());
        Map<Long, StudentEntity> students = studentIds.isEmpty() ? Map.of()
                : studentRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(StudentEntity::getId, Function.identity(), (x, y) -> x));
        Map<Long, Long> fileCounts = fileStorageService.countActiveFilesByRefs(FileConstants.Module.SUBMISSION,
                subs.stream().map(SubmissionEntity::getId).toList());
        List<SubmissionRowDto> rows = new ArrayList<>();
        int[] counts = new int[6];
        for (Long sid : studentIds) {
            StudentEntity st = students.get(sid);
            SubmissionEntity s = byStudent.get(sid);
            String state = HomeworkRules.matrixState(a, s, now);
            counts[stateIndex(state)]++;
            rows.add(SubmissionRowDto.builder()
                    .studentId(sid)
                    .studentCode(st == null ? null : st.getStudentCode())
                    .studentName(st == null ? null : st.getFullName())
                    .submissionId(s == null ? null : s.getId())
                    .state(state)
                    .status(s == null ? null : s.getStatus())
                    .attemptCount(s == null ? 0 : s.getAttemptCount())
                    .submittedAt(s == null ? null : s.getSubmittedAt())
                    .late(s != null && HomeworkRules.flag(s.getIsLate()))
                    .score(s != null && SubmissionStatus.GRADED.equals(s.getStatus()) ? s.getScore() : null)
                    .gradedAt(s == null ? null : s.getGradedAt())
                    .fileCount(s == null ? 0 : fileCounts.getOrDefault(s.getId(), 0L).intValue())
                    .build());
        }
        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi-VN"));
        rows.sort(Comparator.comparing((SubmissionRowDto r) -> r.getStudentName() == null ? "" : r.getStudentName(),
                collator).thenComparing(r -> r.getStudentCode() == null ? "" : r.getStudentCode()));
        AssignmentStatsDto stats = AssignmentStatsDto.builder()
                .totalStudents(rows.size())
                .notSubmitted(counts[0]).missing(counts[1]).submitted(counts[2]).late(counts[3])
                .returned(counts[4]).graded(counts[5])
                .build();
        return new Matrix(rows, stats);
    }

    private static int stateIndex(String state) {
        return switch (state) {
            case MatrixState.NOT_SUBMITTED -> 0;
            case MatrixState.MISSING -> 1;
            case MatrixState.SUBMITTED -> 2;
            case MatrixState.LATE -> 3;
            case MatrixState.RETURNED -> 4;
            default -> 5;
        };
    }

    private void apply(AssignmentEntity a, AssignmentUpsertRequest r) {
        String type = r.getSubmissionType().trim().toUpperCase(Locale.ROOT);
        if (!Arrays.asList(SubmissionType.TEXT, SubmissionType.FILE, SubmissionType.BOTH).contains(type)) {
            throw invalid("kiểu nộp bài phải là TEXT, FILE hoặc BOTH");
        }
        boolean allowLate = Boolean.TRUE.equals(r.getAllowLate());
        if (r.getLateUntil() != null) {
            if (!allowLate) {
                throw invalid("chỉ đặt hạn nộp muộn khi cho phép nộp muộn");
            }
            if (!r.getLateUntil().isAfter(r.getDueAt())) {
                throw invalid("hạn nộp muộn phải sau hạn nộp");
            }
        }
        int maxFiles;
        if (SubmissionType.TEXT.equals(type)) {
            maxFiles = 0;
        } else {
            maxFiles = r.getMaxFiles() == null ? 5 : r.getMaxFiles();
            Integer moduleMax = fileLimitService.limits(FileConstants.Module.SUBMISSION).maxFilesPerRef();
            int cap = Math.min(HomeworkConstants.MAX_FILES_LIMIT, moduleMax == null ? Integer.MAX_VALUE : moduleMax);
            if (r.getMaxFiles() == null) {
                maxFiles = Math.min(maxFiles, cap);
            }
            if (maxFiles < 1) {
                throw invalid("bài nộp file cần cho phép ít nhất 1 file");
            }
            if (maxFiles > cap) {
                throw invalid("số file tối đa vượt giới hạn " + cap + " file của module bài nộp");
            }
        }
        if (r.getSessionId() != null) {
            ClassSessionEntity session = classSessionRepository.findByIdAndIsDeleted(r.getSessionId(),
                            PersistenceFlags.NOT_DELETED)
                    .filter(se -> Objects.equals(se.getClassId(), a.getClassId()))
                    .filter(se -> !SESSION_CANCELLED.equals(se.getStatus()))
                    .orElseThrow(() -> new OracleBusinessException(HomeworkErrorCodes.ASSIGNMENT_SESSION_INVALID,
                            "Buổi học không thuộc lớp đã chọn."));
            a.setSessionId(session.getId());
        } else {
            a.setSessionId(null);
        }
        a.setTitle(r.getTitle().strip());
        a.setDescription(sanitizeOrNull(r.getDescription()));
        a.setSubmissionType(type);
        a.setMaxScore(r.getMaxScore());
        a.setDueAt(r.getDueAt());
        a.setAllowLate(allowLate ? 1 : 0);
        a.setLateUntil(allowLate ? r.getLateUntil() : null);
        a.setMaxFiles(maxFiles);
        a.setAllowResubmit(r.getAllowResubmit() == null ? 1 : HomeworkRules.flag(r.getAllowResubmit()));
        a.setSyncToGradebook(HomeworkRules.flag(r.getSyncToGradebook()));
        a.setGradeWeight(r.getGradeWeight() == null ? BigDecimal.ONE : r.getGradeWeight());
    }

    private AssignmentUpsertRequest requirePayload(AssignmentUpsertRequest r) {
        if (r == null || r.getClassId() == null || r.getTitle() == null || r.getTitle().isBlank()
                || r.getSubmissionType() == null || r.getMaxScore() == null || r.getDueAt() == null) {
            throw new OracleBusinessException("VALIDATION_ERROR", "Thiếu thông tin bắt buộc của bài tập.");
        }
        return r;
    }

    AssignmentEntity requireAssignment(Long id) {
        if (id == null) {
            throw assignmentNotFound();
        }
        return assignmentRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(HomeworkServiceImpl::assignmentNotFound);
    }

    private SubmissionEntity requireSubmission(Long id) {
        if (id == null) {
            throw submissionNotFound();
        }
        return submissionRepository.findById(id)
                .filter(s -> s.getIsDeleted() == null || s.getIsDeleted() == PersistenceFlags.NOT_DELETED)
                .orElseThrow(HomeworkServiceImpl::submissionNotFound);
    }

    private AssignmentEntity requireAssignmentOfSubmission(SubmissionEntity s) {
        return assignmentRepository.findByIdAndIsDeleted(s.getAssignmentId(), PersistenceFlags.NOT_DELETED)
                .orElseThrow(HomeworkServiceImpl::submissionNotFound);
    }

    /** Lớp tồn tại + giáo viên phải phụ trách lớp (403 NOT_CLASS_TEACHER). */
    ClassEntity requireScope(Long classId) {
        ClassEntity clazz = classRepository.findByIdAndIsDeleted(classId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(CLASS_NOT_FOUND,
                        "Không tìm thấy lớp học với ID: " + classId));
        teachingAssignmentGuard.requireCanWrite(clazz, MSG_NOT_CLASS_TEACHER);
        return clazz;
    }

    private static void requireVersion(SubmissionEntity s, Long expected) {
        if (!Objects.equals(s.getVersion(), expected)) {
            throw new OracleBusinessException(HomeworkErrorCodes.GRADE_CONFLICT,
                    "Bài vừa được người khác chấm/cập nhật. Tải lại để xem.");
        }
    }

    private Optional<SubmissionHistoryEntity> latestHistory(Long submissionId) {
        return historyRepository.findBySubmissionIdOrderByAttemptNoDesc(submissionId).stream().findFirst();
    }

    private void safeSync(AssignmentEntity a, SubmissionEntity s, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("Đồng bộ sổ điểm lỗi assignment={} submission={}: {}", a.getId(),
                    s == null ? null : s.getId(), e.getMessage(), e);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("assignmentId", a.getId());
            if (s != null) {
                d.put("submissionId", s.getId());
            }
            d.put("code", HomeworkErrorCodes.GRADEBOOK_SYNC_FAILED);
            d.put("error", truncate(String.valueOf(e.getMessage()), 200));
            try {
                auditService.failure(AuditActions.HOMEWORK_GRADEBOOK_SYNC_FAILED, AuditActions.RESOURCE_ASSIGNMENT,
                        a.getId(), d);
            } catch (RuntimeException ignored) {
                log.warn("Không ghi được audit đồng bộ sổ điểm: {}", ignored.getMessage());
            }
        }
    }

    private void softDeleteFiles(String module, Long refId, String reason) {
        for (FileEntity f : fileStorageService.getFilesByRef(module, refId)) {
            fileLifecycleService.softDelete(f.getId(), reason);
        }
    }

    private void deleteOwnedFile(String module, Long refId, Long fileId, String reason) {
        FileEntity f;
        try {
            f = fileStorageService.getFileEntity(fileId);
        } catch (OracleBusinessException e) {
            throw fileNotFound(fileId);
        }
        if (!module.equals(f.getModuleName()) || !Objects.equals(refId, f.getReferenceId())) {
            throw fileNotFound(fileId);
        }
        fileLifecycleService.softDelete(fileId, reason);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("module", module);
        d.put("refId", refId);
        d.put("size", f.getFileSize());
        auditService.success(AuditActions.FILE_DELETED, AuditActions.RESOURCE_FILE, fileId, d);
    }

    private SubmissionDetailDto toDetail(SubmissionEntity s, AssignmentEntity a) {
        StudentEntity st = studentRepository.findById(s.getStudentId()).orElse(null);
        String gradedByName = s.getGradedBy() == null ? null
                : userRepository.findById(s.getGradedBy()).map(u -> u.getFullName() != null ? u.getFullName()
                : u.getUsername()).orElse(null);
        boolean canGrade = SecurityUtils.currentUserHasPermission(Permissions.HOMEWORK_GRADE);
        return SubmissionDetailDto.builder()
                .id(s.getId())
                .assignmentId(a.getId())
                .assignmentTitle(a.getTitle())
                .classId(a.getClassId())
                .studentId(s.getStudentId())
                .studentCode(st == null ? null : st.getStudentCode())
                .studentName(st == null ? null : st.getFullName())
                .status(s.getStatus())
                .contentText(s.getContentText())
                .attemptCount(s.getAttemptCount())
                .firstSubmittedAt(s.getFirstSubmittedAt())
                .submittedAt(s.getSubmittedAt())
                .late(HomeworkRules.flag(s.getIsLate()))
                .score(s.getScore())
                .maxScore(a.getMaxScore())
                .feedback(s.getFeedback())
                .gradedById(s.getGradedBy())
                .gradedByName(gradedByName)
                .gradedAt(s.getGradedAt())
                .returnedAt(s.getReturnedAt())
                .returnReason(s.getReturnReason())
                .version(s.getVersion())
                .files(files(FileConstants.Module.SUBMISSION, s.getId(), false))
                .feedbackFiles(files(FileConstants.Module.FEEDBACK, s.getId(), canGrade))
                .history(historyRepository.findBySubmissionIdOrderByAttemptNoDesc(s.getId()).stream()
                        .map(HomeworkServiceImpl::toHistoryDto).toList())
                .canGrade(canGrade)
                .build();
    }

    static SubmissionHistoryDto toHistoryDto(SubmissionHistoryEntity h) {
        List<Long> ids = new ArrayList<>();
        if (h.getFileIds() != null && !h.getFileIds().isBlank()) {
            for (String part : h.getFileIds().split(",")) {
                try {
                    ids.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException ignored) {
                    // bỏ qua phần tử hỏng
                }
            }
        }
        return SubmissionHistoryDto.builder()
                .attemptNo(h.getAttemptNo())
                .contentSnapshot(h.getContentSnapshot())
                .fileIds(ids)
                .submittedAt(h.getSubmittedAt())
                .late(HomeworkRules.flag(h.getIsLate()))
                .outcome(h.getOutcome())
                .score(h.getScore())
                .feedback(h.getFeedback())
                .actedAt(h.getActedAt())
                .build();
    }

    private List<FileResponseDto> files(String module, Long refId, boolean canDelete) {
        return fileStorageService.getFilesByRef(module, refId).stream().map(f -> toFileDto(f, canDelete)).toList();
    }

    private FileResponseDto toFileDto(FileEntity f, boolean canDelete) {
        FileResponseDto dto = fileMapper.toDto(f);
        dto.setCanDelete(canDelete);
        return dto;
    }

    private AssignmentDto toDto(AssignmentEntity a, boolean full, Optional<Set<Long>> taught) {
        ClassEntity clazz = classRepository.findById(a.getClassId()).orElse(null);
        ClassSessionEntity session = a.getSessionId() == null ? null
                : classSessionRepository.findById(a.getSessionId()).orElse(null);
        boolean inScope = taught.isEmpty() || taught.get().contains(a.getClassId());
        boolean canManage = inScope && SecurityUtils.currentUserHasPermission(Permissions.HOMEWORK_UPDATE);
        return AssignmentDto.builder()
                .id(a.getId())
                .classId(a.getClassId())
                .classCode(clazz == null ? null : clazz.getClassCode())
                .className(clazz == null ? null : clazz.getClassName())
                .sessionId(a.getSessionId())
                .sessionDate(session == null ? null : session.getSessionDate())
                .sessionLabel(sessionLabel(session))
                .title(a.getTitle())
                .description(full ? a.getDescription() : null)
                .submissionType(a.getSubmissionType())
                .maxScore(a.getMaxScore())
                .dueAt(a.getDueAt())
                .allowLate(HomeworkRules.flag(a.getAllowLate()))
                .lateUntil(a.getLateUntil())
                .maxFiles(a.getMaxFiles())
                .allowResubmit(HomeworkRules.flag(a.getAllowResubmit()))
                .syncToGradebook(HomeworkRules.flag(a.getSyncToGradebook()))
                .gradeWeight(a.getGradeWeight())
                .status(a.getStatus())
                .publishedAt(a.getPublishedAt())
                .closedAt(a.getClosedAt())
                .version(a.getVersion())
                .attachments(full ? files(FileConstants.Module.ASSIGNMENT, a.getId(), canManage) : List.of())
                .canManage(canManage)
                .canGrade(inScope && SecurityUtils.currentUserHasPermission(Permissions.HOMEWORK_GRADE))
                .createdAt(a.getCreatedAt())
                .createdBy(a.getCreatedBy())
                .updatedAt(a.getUpdatedAt())
                .updatedBy(a.getUpdatedBy())
                .build();
    }

    static String sessionLabel(ClassSessionEntity s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (s.getSessionDate() != null) {
            sb.append(s.getSessionDate());
        }
        if (s.getStartTime() != null) {
            sb.append(' ').append(s.getStartTime());
            if (s.getEndTime() != null) {
                sb.append('-').append(s.getEndTime());
            }
        }
        if (s.getTopic() != null && !s.getTopic().isBlank()) {
            sb.append(" - ").append(s.getTopic().strip());
        }
        return sb.toString().strip();
    }

    private String sanitizeOrNull(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String clean = htmlContentSanitizer.sanitize(html);
        return clean == null || clean.isBlank() ? null : clean;
    }

    private void audit(String action, AssignmentEntity a, Map<String, ?> extra) {
        Map<String, Object> d = new LinkedHashMap<>(extra);
        d.put("status", a.getStatus());
        auditService.success(action, AuditActions.RESOURCE_ASSIGNMENT, a.getId(), d);
    }

    private static Map<String, Object> details(AssignmentEntity a, SubmissionEntity s, Map<String, ?> extra) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("assignmentId", a.getId());
        d.put("studentId", s.getStudentId());
        d.put("status", s.getStatus());
        d.putAll(extra);
        return d;
    }

    private static boolean sameNumber(BigDecimal x, BigDecimal y) {
        return x == null ? y == null : y != null && x.compareTo(y) == 0;
    }

    static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    static OracleBusinessException assignmentNotFound() {
        return new OracleBusinessException(HomeworkErrorCodes.ASSIGNMENT_NOT_FOUND, "Không tìm thấy bài tập.");
    }

    static OracleBusinessException submissionNotFound() {
        return new OracleBusinessException(HomeworkErrorCodes.SUBMISSION_NOT_FOUND, "Không tìm thấy bài nộp.");
    }

    private static OracleBusinessException fileNotFound(Long fileId) {
        return new OracleBusinessException(FileErrorCodes.FILE_NOT_FOUND, "Không tìm thấy file với ID: " + fileId);
    }

    private static OracleBusinessException invalidState(String msg) {
        return new OracleBusinessException(HomeworkErrorCodes.ASSIGNMENT_INVALID_STATE, msg);
    }

    private static OracleBusinessException invalid(String msg) {
        return new OracleBusinessException(HomeworkErrorCodes.ASSIGNMENT_INVALID, "Thông tin bài tập không hợp lệ: " + msg + ".");
    }
}
