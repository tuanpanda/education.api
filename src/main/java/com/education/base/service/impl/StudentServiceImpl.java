package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.QuickCreateClassRequest;
import com.education.base.dto.request.StudentCreateRequest;
import com.education.base.dto.request.StudentFilterRequest;
import com.education.base.dto.request.StudentUpdateRequest;
import com.education.base.dto.response.FileResponseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentDetailResponse;
import com.education.base.dto.response.StudentReportDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.FileEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapper;
import com.education.base.mapper.StudentMapper;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.UserRepository;
import com.education.base.service.FileStorageService;
import com.education.base.service.StudentService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Triển khai {@link StudentService} theo kiến trúc Hybrid:
 * tìm kiếm/phân trang dùng Standalone Procedure, CRUD dùng Spring Data JPA.
 * Thêm học sinh + ghi danh/tạo nhanh lớp nằm trong một giao dịch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    /** Giá trị cột {@code IS_DELETED} của bản ghi còn hiệu lực. */
    private static final int NOT_DELETED = PersistenceFlags.NOT_DELETED;

    /** Giá trị cột {@code IS_DELETED} của bản ghi đã xóa mềm. */
    private static final int DELETED = PersistenceFlags.DELETED;

    private static final String DEFAULT_STATUS = "ACTIVE";

    /** Trạng thái lớp mới tạo nhanh — khớp {@code CK_CLASSES_STATUS} (OPEN = đang mở). */
    private static final String QUICK_CLASS_STATUS = "OPEN";

    private static final String ENROLLMENT_STATUS = "ENROLLED";

    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final StudentMapper studentMapper;
    private final FileMapper fileMapper;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentReportDto> search(StudentFilterRequest filter) {
        return studentRepository.searchWithPaging(filter);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentDetailResponse getDetail(Long id) {
        StudentEntity entity = requireActiveStudent(id);
        return toDetailWithAttachments(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudentDetailResponse create(StudentCreateRequest request) {
        if (request.getClassId() != null && request.getNewClass() != null) {
            throw new OracleBusinessException(
                    "CLASS_ASSIGNMENT_CONFLICT",
                    "Chỉ chọn lớp có sẵn hoặc tạo nhanh lớp mới, không gửi đồng thời classId và newClass.");
        }

        StudentEntity entity = studentMapper.toEntity(request);
        entity.setIsDeleted(NOT_DELETED);
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus(DEFAULT_STATUS);
        }

        StudentEntity saved = studentRepository.saveAndFlush(entity);
        entityManager.refresh(saved);

        ClassEntity assignedClass = resolveAssignedClass(request);
        if (assignedClass != null) {
            enrollStudent(assignedClass, saved);
        }

        log.info("Đã thêm học sinh id={}, studentCode={}, classId={}",
                saved.getId(), saved.getStudentCode(), assignedClass == null ? null : assignedClass.getId());
        StudentDetailResponse detail = studentMapper.toDetail(saved);
        applyClass(detail, assignedClass);
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudentDetailResponse update(Long id, StudentUpdateRequest request) {
        StudentEntity entity = requireActiveStudent(id);

        studentMapper.updateEntity(request, entity);
        StudentEntity saved = studentRepository.save(entity);

        ClassEntity assignedClass = null;
        if (request.getClassId() != null) {
            assignedClass = assignOrTransferClass(saved, request.getClassId());
        }

        log.info("Đã cập nhật học sinh id={}, studentCode={}, classId={}",
                saved.getId(), saved.getStudentCode(),
                assignedClass == null ? null : assignedClass.getId());
        StudentDetailResponse detail = toDetailWithAttachments(saved);
        if (assignedClass != null) {
            applyClass(detail, assignedClass);
        }
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(Long id) {
        StudentEntity entity = requireActiveStudent(id);

        entity.setIsDeleted(DELETED);
        entity.setUpdatedAt(LocalDateTime.now());
        studentRepository.save(entity);

        log.info("Đã xóa mềm học sinh id={}, studentCode={}", entity.getId(), entity.getStudentCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileResponseDto uploadDocument(Long id, MultipartFile file) {
        StudentEntity entity = requireActiveStudent(id);

        FileEntity stored = fileStorageService.storeFile(file, MODULE_NAME, entity.getId());
        log.info("Đã đính kèm tài liệu fileId={} cho học sinh id={}", stored.getId(), entity.getId());
        return fileMapper.toDto(stored);
    }

    /**
     * Lấy học sinh còn hiệu lực hoặc ném lỗi nghiệp vụ 404 nếu không tồn tại/đã xóa mềm.
     */
    private StudentEntity requireActiveStudent(Long id) {
        if (id == null) {
            throw new OracleBusinessException("STUDENT_ID_REQUIRED", "ID học sinh không được để trống.");
        }
        return studentRepository.findByIdAndIsDeleted(id, NOT_DELETED)
                .filter(student -> DomainConstants.STUDENT_STATUS_ACTIVE.equals(student.getStatus()))
                .orElseThrow(() -> new OracleBusinessException(
                        "STUDENT_NOT_FOUND", "Không tìm thấy học sinh với ID: " + id));
    }

    /**
     * Ghép thông tin học sinh với danh sách tài liệu đính kèm lấy qua
     * Standalone Procedure {@code PRC_GET_FILES_BY_REF}.
     */
    private StudentDetailResponse toDetailWithAttachments(StudentEntity entity) {
        StudentDetailResponse detail = studentMapper.toDetail(entity);
        List<FileEntity> files = fileStorageService.getFilesByRef(MODULE_NAME, entity.getId());
        detail.setAttachments(fileMapper.toDtoList(files));
        classStudentRepository
                .findFirstByStudentIdAndIsDeletedAndStatusOrderByEnrolledAtDesc(
                        entity.getId(), NOT_DELETED, ENROLLMENT_STATUS)
                .map(ClassStudentEntity::getClassId)
                .flatMap(classId -> classRepository.findByIdAndIsDeleted(classId, NOT_DELETED))
                .ifPresent(clazz -> applyClass(detail, clazz));
        return detail;
    }

    private ClassEntity requireOpenClass(Long classId) {
        ClassEntity clazz = classRepository.findByIdAndIsDeleted(classId, NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException("404", "Không tìm thấy lớp học"));
        if (!"OPEN".equals(clazz.getStatus())) {
            throw new OracleBusinessException("CLASS_NOT_OPEN",
                    "Chỉ được thêm học sinh vào lớp đang mở.");
        }
        return clazz;
    }

    private ClassEntity resolveAssignedClass(StudentCreateRequest request) {
        if (request.getClassId() != null) {
            return requireOpenClass(request.getClassId());
        }
        if (request.getNewClass() == null) {
            return null;
        }
        return createQuickClass(request.getNewClass());
    }

    private ClassEntity createQuickClass(QuickCreateClassRequest neo) {
        if (neo.getTeacherId() != null) {
            userRepository.findByIdAndIsDeleted(neo.getTeacherId(), NOT_DELETED)
                    .orElseThrow(() -> new OracleBusinessException(
                            "TEACHER_NOT_FOUND", "Không tìm thấy giảng viên với ID: " + neo.getTeacherId()));
        }

        ClassEntity created = classRepository.saveAndFlush(ClassEntity.builder()
                .className(neo.getClassName().trim())
                .gradeLevel(neo.getGradeLevel())
                .subjectName(blankToNull(neo.getCourseName()))
                .teacherId(neo.getTeacherId())
                .roomName(blankToNull(neo.getRoomName()))
                .status(QUICK_CLASS_STATUS)
                .capacity(0)
                .tuitionAmount(BigDecimal.ZERO)
                .isDeleted(NOT_DELETED)
                .build());
        entityManager.refresh(created);
        log.info("Đã tạo nhanh lớp id={}, classCode={}", created.getId(), created.getClassCode());
        return created;
    }

    /** Một học sinh chỉ thuộc một lớp: rời lớp cũ rồi ghi danh lớp mới. */
    private ClassEntity assignOrTransferClass(StudentEntity student, Long classId) {
        ClassEntity clazz = requireOpenClass(classId);
        leaveOtherClasses(student.getId(), clazz.getId());

        ClassStudentEntity existing = classStudentRepository
                .findByClassIdAndStudentId(clazz.getId(), student.getId())
                .orElse(null);
        if (existing != null) {
            if (existing.getIsDeleted() == null || existing.getIsDeleted() == NOT_DELETED) {
                return clazz;
            }
            existing.setIsDeleted(NOT_DELETED);
            existing.setStatus(ENROLLMENT_STATUS);
            existing.setEnrolledAt(LocalDateTime.now());
            classStudentRepository.save(existing);
            return clazz;
        }

        enrollStudent(clazz, student);
        return clazz;
    }

    private void leaveOtherClasses(Long studentId, Long keepClassId) {
        for (ClassStudentEntity row : classStudentRepository.findByStudentIdAndIsDeleted(studentId, NOT_DELETED)) {
            if (row.getClassId() != null && !row.getClassId().equals(keepClassId)) {
                row.setIsDeleted(DELETED);
                classStudentRepository.save(row);
            }
        }
    }

    private void enrollStudent(ClassEntity clazz, StudentEntity student) {
        Long classId = clazz.getId();
        Long studentId = student.getId();
        if (classStudentRepository.existsByClassIdAndStudentId(classId, studentId)) {
            throw new OracleBusinessException(
                    "STUDENT_ALREADY_ENROLLED",
                    "Học sinh " + student.getStudentCode() + " đã ghi danh lớp này.");
        }

        long enrolled = classStudentRepository.countByClassIdAndStatusAndIsDeleted(
                classId, ENROLLMENT_STATUS, NOT_DELETED);
        if (clazz.getCapacity() != null && clazz.getCapacity() > 0 && enrolled >= clazz.getCapacity()) {
            throw new OracleBusinessException(
                    "CLASS_FULL", "Lớp " + clazz.getClassCode() + " đã đủ sĩ số.");
        }

        classStudentRepository.save(ClassStudentEntity.builder()
                .classId(classId)
                .studentId(studentId)
                .status(ENROLLMENT_STATUS)
                .enrolledAt(LocalDateTime.now())
                .isDeleted(NOT_DELETED)
                .build());
    }

    private static void applyClass(StudentDetailResponse detail, ClassEntity clazz) {
        if (clazz == null) {
            return;
        }
        detail.setClassId(clazz.getId());
        detail.setClassCode(clazz.getClassCode());
        detail.setClassName(clazz.getClassName());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
