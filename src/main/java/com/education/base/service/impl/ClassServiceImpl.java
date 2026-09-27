package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.ClassCreateRequest;
import com.education.base.dto.request.ClassFilterRequest;
import com.education.base.dto.request.ClassUpdateRequest;
import com.education.base.dto.request.EnrollStudentsRequest;
import com.education.base.dto.response.ClassDetailResponse;
import com.education.base.dto.response.ClassOptionResponse;
import com.education.base.dto.response.ClassReportDto;
import com.education.base.dto.response.EnrolledStudentDto;
import com.education.base.dto.response.NextClassCodeResponse;
import com.education.base.dto.response.PageResponse;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.CodeRuleEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.ClassMapper;
import com.education.base.mapper.FileMapper;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.CodeRuleRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.UserRepository;
import com.education.base.service.ClassService;
import com.education.base.service.FileStorageService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassServiceImpl implements ClassService {

    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
    private final StudentRepository studentRepository;
    private final CodeRuleRepository codeRuleRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final ClassMapper classMapper;
    private final FileMapper fileMapper;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ClassReportDto> search(ClassFilterRequest filter) {
        return classRepository.searchWithPaging(filter);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassDetailResponse getDetail(Long id) {
        return toDetail(requireActiveClass(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassDetailResponse create(ClassCreateRequest request) {
        validatePeriod(request.getStartDate(), request.getEndDate());
        validateTeacher(request.getTeacherId());

        ClassEntity entity = classMapper.toEntity(request);
        entity.setIsDeleted(PersistenceFlags.NOT_DELETED);
        entity.setCalendarColor(normalizeCalendarColor(request.getCalendarColor(), "#3b82f6"));
        ClassEntity saved = classRepository.saveAndFlush(entity);
        entityManager.refresh(saved);
        log.info("Đã tạo lớp id={}, classCode={}", saved.getId(), saved.getClassCode());
        return toDetail(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassDetailResponse update(Long id, ClassUpdateRequest request) {
        ClassEntity entity = requireActiveClass(id);
        validatePeriod(request.getStartDate(), request.getEndDate());
        validateTeacher(request.getTeacherId());
        classMapper.updateEntity(request, entity);
        entity.setCalendarColor(normalizeCalendarColor(request.getCalendarColor(), entity.getCalendarColor()));
        return toDetail(classRepository.save(entity));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(Long id) {
        ClassEntity entity = requireActiveClass(id);
        entity.setIsDeleted(PersistenceFlags.DELETED);
        entity.setUpdatedAt(LocalDateTime.now());
        classRepository.save(entity);
        log.info("Đã xóa mềm lớp id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<EnrolledStudentDto> enroll(Long classId, EnrollStudentsRequest request) {
        ClassEntity clazz = requireActiveClass(classId);
        requireOpenForEnrollment(clazz);
        if (request.getClassId() != null && !request.getClassId().equals(classId)) {
            throw new OracleBusinessException("CLASS_ID_MISMATCH",
                    "ID lớp trên đường dẫn và trong request không khớp.");
        }

        List<EnrolledStudentDto> result = new ArrayList<>();
        for (Long studentId : new LinkedHashSet<>(request.getStudentIds())) {
            StudentEntity student = studentRepository.findByIdAndIsDeleted(studentId, PersistenceFlags.NOT_DELETED)
                    .filter(s -> DomainConstants.STUDENT_STATUS_ACTIVE.equals(s.getStatus()))
                    .orElseThrow(() -> new OracleBusinessException(
                            "STUDENT_NOT_FOUND", "Không tìm thấy học sinh với ID: " + studentId));

            boolean inOtherClass = classStudentRepository
                    .findByStudentIdAndIsDeleted(studentId, PersistenceFlags.NOT_DELETED)
                    .stream()
                    .anyMatch(row -> row.getClassId() != null && !row.getClassId().equals(classId));
            if (inOtherClass) {
                throw new OracleBusinessException(
                        "STUDENT_ALREADY_IN_OTHER_CLASS",
                        "Học sinh " + student.getStudentCode()
                                + " đã thuộc một lớp khác. Chuyển lớp từ hồ sơ học sinh.");
            }

            ClassStudentEntity existing = classStudentRepository
                    .findByClassIdAndStudentId(classId, studentId)
                    .orElse(null);
            if (existing != null && (existing.getIsDeleted() == null
                    || existing.getIsDeleted() == PersistenceFlags.NOT_DELETED)) {
                throw new OracleBusinessException("STUDENT_ALREADY_ENROLLED",
                        "Học sinh " + student.getStudentCode() + " đã ghi danh lớp này.");
            }

            long enrolled = classStudentRepository.countByClassIdAndStatusAndIsDeleted(
                    classId, "ENROLLED", PersistenceFlags.NOT_DELETED);
            if (clazz.getCapacity() != null && clazz.getCapacity() > 0 && enrolled >= clazz.getCapacity()) {
                throw new OracleBusinessException("CLASS_FULL",
                        "Lớp " + clazz.getClassCode() + " đã đủ sĩ số.");
            }

            if (existing != null) {
                existing.setIsDeleted(PersistenceFlags.NOT_DELETED);
                existing.setStatus("ENROLLED");
                existing.setEnrolledAt(LocalDateTime.now());
                result.add(classMapper.toEnrolledStudent(classStudentRepository.save(existing), student));
                continue;
            }

            ClassStudentEntity saved = classStudentRepository.save(ClassStudentEntity.builder()
                    .classId(classId)
                    .studentId(studentId)
                    .status("ENROLLED")
                    .isDeleted(PersistenceFlags.NOT_DELETED)
                    .build());
            result.add(classMapper.toEnrolledStudent(saved, student));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unenroll(Long classId, Long studentId) {
        requireActiveClass(classId);
        if (studentId == null) {
            throw new OracleBusinessException("STUDENT_ID_REQUIRED", "ID học sinh không được để trống.");
        }
        ClassStudentEntity enrollment = classStudentRepository
                .findByClassIdAndStudentIdAndIsDeleted(classId, studentId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "ENROLLMENT_NOT_FOUND", "Học sinh không thuộc lớp này."));
        enrollment.setIsDeleted(PersistenceFlags.DELETED);
        classStudentRepository.save(enrollment);
        log.info("Đã xóa học sinh id={} khỏi lớp id={}", studentId, classId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassOptionResponse> listOpenOptions() {
        List<ClassEntity> classes = classRepository.findByStatusInAndIsDeletedOrderByCreatedAtDesc(
                List.of("OPEN"), PersistenceFlags.NOT_DELETED);
        return classes.stream().map(classMapper::toOption).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NextClassCodeResponse peekNextClassCode(Integer gradeLevel) {
        if (gradeLevel == null || gradeLevel < 1 || gradeLevel > 12) {
            throw new OracleBusinessException("INVALID_GRADE_LEVEL", "Khối lớp phải từ 1 đến 12.");
        }

        CodeRuleEntity rule = codeRuleRepository
                .findByRuleCodeAndIsDeleted("CLASS", PersistenceFlags.NOT_DELETED)
                .filter(item -> item.getIsActive() != null && item.getIsActive() == 1)
                .orElseThrow(() -> new OracleBusinessException(
                        "CODE_RULE_NOT_FOUND",
                        "Không tìm thấy quy luật sinh mã CLASS trong SYS_CODE_RULES."));

        LocalDateTime now = LocalDateTime.now();
        String resetKey = switch (String.valueOf(rule.getResetCycle()).toUpperCase()) {
            case "YEAR" -> now.format(DateTimeFormatter.ofPattern("yyyy"));
            case "MONTH" -> now.format(DateTimeFormatter.ofPattern("yyyyMM"));
            case "DAY" -> now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            default -> "*";
        };
        long seq = rule.getLastResetKey() == null || !resetKey.equals(rule.getLastResetKey())
                ? 1L
                : (rule.getLastSeq() == null ? 0L : rule.getLastSeq()) + 1L;
        int seqLen = rule.getSeqLength() == null || rule.getSeqLength() < 1 ? 4 : rule.getSeqLength();
        String pattern = rule.getPattern() == null || rule.getPattern().isBlank()
                ? "{PREFIX}{GRADE}{YYYY}{SEQ}"
                : rule.getPattern();
        String code = pattern
                .replace("{PREFIX}", rule.getPrefix() == null ? "" : rule.getPrefix())
                .replace("{GRADE}", String.valueOf(gradeLevel))
                .replace("{YYYY}", now.format(DateTimeFormatter.ofPattern("yyyy")))
                .replace("{YY}", now.format(DateTimeFormatter.ofPattern("yy")))
                .replace("{MM}", now.format(DateTimeFormatter.ofPattern("MM")))
                .replace("{DD}", now.format(DateTimeFormatter.ofPattern("dd")))
                .replace("{SEQ}", String.format("%0" + seqLen + "d", seq));

        return NextClassCodeResponse.builder()
                .classCode(code)
                .gradeLevel(gradeLevel)
                .preview(true)
                .build();
    }

    private ClassEntity requireActiveClass(Long id) {
        if (id == null) {
            throw new OracleBusinessException("CLASS_ID_REQUIRED", "ID lớp học không được để trống.");
        }
        return classRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "CLASS_NOT_FOUND", "Không tìm thấy lớp học với ID: " + id));
    }

    private static void requireOpenForEnrollment(ClassEntity clazz) {
        if (!"OPEN".equals(clazz.getStatus())) {
            throw new OracleBusinessException("CLASS_NOT_OPEN",
                    "Chỉ được ghi danh vào lớp đang mở.");
        }
    }

    private void validateTeacher(Long teacherId) {
        if (teacherId == null) {
            return;
        }
        userRepository.findByIdAndIsDeleted(teacherId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "TEACHER_NOT_FOUND", "Không tìm thấy giảng viên với ID: " + teacherId));
    }

    private void validatePeriod(java.time.LocalDate start, java.time.LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new OracleBusinessException("INVALID_CLASS_PERIOD",
                    "Ngày kết thúc không được nhỏ hơn ngày bắt đầu.");
        }
    }

    private static String normalizeCalendarColor(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback == null || fallback.isBlank() ? null : fallback.trim().toLowerCase();
        }
        return raw.trim().toLowerCase();
    }

    private ClassDetailResponse toDetail(ClassEntity entity) {
        ClassDetailResponse detail = classMapper.toDetail(entity);
        if (entity.getTeacherId() != null) {
            userRepository.findById(entity.getTeacherId())
                    .ifPresent(user -> detail.setTeacherName(user.getFullName()));
        }
        List<ClassStudentEntity> enrollments =
                classStudentRepository.findByClassIdAndIsDeleted(entity.getId(), PersistenceFlags.NOT_DELETED);
        List<EnrolledStudentDto> students = new ArrayList<>();
        for (ClassStudentEntity enrollment : enrollments) {
            studentRepository.findById(enrollment.getStudentId())
                    .filter(student -> DomainConstants.isListedStudent(student.getStatus(), student.getIsDeleted()))
                    .ifPresent(student -> students.add(classMapper.toEnrolledStudent(enrollment, student)));
        }
        detail.setStudents(students);
        detail.setAttachments(fileMapper.toDtoList(
                fileStorageService.getFilesByRef(MODULE_NAME, entity.getId())));
        return detail;
    }
}
