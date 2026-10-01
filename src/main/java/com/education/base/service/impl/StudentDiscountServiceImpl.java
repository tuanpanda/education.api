package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.StudentDiscountFilterRequest;
import com.education.base.dto.request.StudentDiscountUpsertRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentDiscountDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.StudentDiscountEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.StudentDiscountMapper;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.StudentDiscountRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.spec.StudentDiscountSpecifications;
import com.education.base.security.SecurityUtils;
import com.education.base.service.StudentDiscountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentDiscountServiceImpl implements StudentDiscountService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final StudentDiscountRepository studentDiscountRepository;
    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final StudentDiscountMapper studentDiscountMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentDiscountDto> search(StudentDiscountFilterRequest filter) {
        StudentDiscountFilterRequest criteria = filter == null ? new StudentDiscountFilterRequest() : filter;
        Page<StudentDiscountEntity> page = studentDiscountRepository.findAll(
                StudentDiscountSpecifications.fromFilter(criteria),
                PageRequest.of(criteria.resolvePageNo() - 1, criteria.resolvePageSize(),
                        Sort.by(Sort.Direction.DESC, "id")));
        List<StudentDiscountEntity> rows = page.getContent();
        Map<Long, StudentEntity> students = loadStudents(rows);
        Map<Long, ClassEntity> classes = loadClasses(rows);
        List<StudentDiscountDto> content = new ArrayList<>(rows.size());
        for (StudentDiscountEntity row : rows) {
            content.add(toDto(row, students, classes));
        }
        return PageResponse.of(content, criteria.resolvePageNo(), criteria.resolvePageSize(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public StudentDiscountDto getById(Long id) {
        return toDto(requireExisting(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudentDiscountDto create(StudentDiscountUpsertRequest request) {
        StudentDiscountUpsertRequest payload = validate(request);
        StudentDiscountEntity entity = new StudentDiscountEntity();
        studentDiscountMapper.apply(payload, entity);
        normalize(entity);
        entity.setIsDeleted(PersistenceFlags.NOT_DELETED);
        entity.setCreatedBy(SecurityUtils.currentUsername());
        StudentDiscountEntity saved = studentDiscountRepository.saveAndFlush(entity);
        log.info("Đã tạo miễn giảm id={} cho học sinh id={}", saved.getId(), saved.getStudentId());
        return toDto(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudentDiscountDto update(Long id, StudentDiscountUpsertRequest request) {
        StudentDiscountEntity entity = requireExisting(id);
        StudentDiscountUpsertRequest payload = validate(request);
        studentDiscountMapper.apply(payload, entity);
        normalize(entity);
        entity.setUpdatedBy(SecurityUtils.currentUsername());
        StudentDiscountEntity saved = studentDiscountRepository.saveAndFlush(entity);
        log.info("Đã cập nhật miễn giảm id={}", saved.getId());
        return toDto(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(Long id) {
        StudentDiscountEntity entity = requireExisting(id);
        entity.setIsDeleted(PersistenceFlags.DELETED);
        entity.setUpdatedBy(SecurityUtils.currentUsername());
        studentDiscountRepository.save(entity);
        log.info("Đã xóa mềm miễn giảm id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<StudentDiscountEntity>> findApplicable(Collection<Long> studentIds, Long classId,
                                                                 LocalDate periodStart, LocalDate periodEnd) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<StudentDiscountEntity>> result = new LinkedHashMap<>();
        for (StudentDiscountEntity row : studentDiscountRepository.findApplicable(
                studentIds, classId, periodStart, periodEnd)) {
            result.computeIfAbsent(row.getStudentId(), key -> new ArrayList<>()).add(row);
        }
        return result;
    }

    @Override
    public BigDecimal computeDiscount(BigDecimal totalAmount, Collection<StudentDiscountEntity> discounts) {
        BigDecimal total = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        if (discounts == null || discounts.isEmpty() || total.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (StudentDiscountEntity discount : discounts) {
            BigDecimal value = discount.getDiscountValue();
            if (value == null || value.signum() <= 0) {
                continue;
            }
            if (DomainConstants.DISCOUNT_TYPE_PERCENT.equals(discount.getDiscountType())) {
                sum = sum.add(total.multiply(value).divide(HUNDRED, 0, RoundingMode.HALF_UP));
            } else if (DomainConstants.DISCOUNT_TYPE_AMOUNT.equals(discount.getDiscountType())) {
                sum = sum.add(value);
            }
        }
        return sum.min(total).max(BigDecimal.ZERO);
    }

    private StudentDiscountUpsertRequest validate(StudentDiscountUpsertRequest request) {
        if (request == null) {
            throw new OracleBusinessException("DISCOUNT_REQUIRED", "Thiếu dữ liệu miễn giảm.");
        }
        studentRepository.findByIdAndIsDeleted(request.getStudentId(), PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "STUDENT_NOT_FOUND", "Không tìm thấy học sinh với ID: " + request.getStudentId()));
        if (request.getClassId() != null) {
            classRepository.findByIdAndIsDeleted(request.getClassId(), PersistenceFlags.NOT_DELETED)
                    .orElseThrow(() -> new OracleBusinessException(
                            "CLASS_NOT_FOUND", "Không tìm thấy lớp học với ID: " + request.getClassId()));
        }
        String type = request.getDiscountType() == null ? null : request.getDiscountType().trim();
        if (!DomainConstants.DISCOUNT_TYPE_PERCENT.equals(type) && !DomainConstants.DISCOUNT_TYPE_AMOUNT.equals(type)) {
            throw new OracleBusinessException("INVALID_DISCOUNT_TYPE", "Loại miễn giảm chỉ nhận: PERCENT, AMOUNT.");
        }
        BigDecimal value = request.getDiscountValue();
        if (value == null || value.signum() <= 0
                || (DomainConstants.DISCOUNT_TYPE_PERCENT.equals(type) && value.compareTo(HUNDRED) > 0)) {
            throw new OracleBusinessException("INVALID_DISCOUNT_VALUE",
                    "Giá trị miễn giảm phải lớn hơn 0 (theo %: tối đa 100).");
        }
        if (request.getValidFrom() == null) {
            throw new OracleBusinessException("INVALID_DISCOUNT_PERIOD", "Ngày bắt đầu hiệu lực không được để trống.");
        }
        if (request.getValidTo() != null && request.getValidTo().isBefore(request.getValidFrom())) {
            throw new OracleBusinessException("INVALID_DISCOUNT_PERIOD",
                    "Ngày hết hiệu lực không được trước ngày bắt đầu.");
        }
        return request;
    }

    private static void normalize(StudentDiscountEntity entity) {
        entity.setDiscountType(entity.getDiscountType().trim());
        if (entity.getReason() != null) {
            String reason = entity.getReason().trim();
            entity.setReason(reason.isEmpty() ? null : reason);
        }
    }

    private StudentDiscountEntity requireExisting(Long id) {
        if (id == null) {
            throw new OracleBusinessException("DISCOUNT_ID_REQUIRED", "ID miễn giảm không được để trống.");
        }
        return studentDiscountRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "DISCOUNT_NOT_FOUND", "Không tìm thấy miễn giảm với ID: " + id));
    }

    private StudentDiscountDto toDto(StudentDiscountEntity entity) {
        Map<Long, StudentEntity> students = loadStudents(List.of(entity));
        Map<Long, ClassEntity> classes = loadClasses(List.of(entity));
        return toDto(entity, students, classes);
    }

    private StudentDiscountDto toDto(StudentDiscountEntity entity, Map<Long, StudentEntity> students,
                                     Map<Long, ClassEntity> classes) {
        StudentDiscountDto dto = studentDiscountMapper.toDto(entity);
        StudentEntity student = entity.getStudentId() == null ? null : students.get(entity.getStudentId());
        if (student != null) {
            dto.setStudentCode(student.getStudentCode());
            dto.setStudentName(student.getFullName());
        }
        ClassEntity clazz = entity.getClassId() == null ? null : classes.get(entity.getClassId());
        if (clazz != null) {
            dto.setClassCode(clazz.getClassCode());
            dto.setClassName(clazz.getClassName());
        }
        return dto;
    }

    /** Nạp một lần mọi học sinh của trang kết quả (tránh N+1). */
    private Map<Long, StudentEntity> loadStudents(Collection<StudentDiscountEntity> rows) {
        List<Long> ids = rows.stream().map(StudentDiscountEntity::getStudentId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return studentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(StudentEntity::getId, Function.identity(), (a, b) -> a));
    }

    /** Nạp một lần mọi lớp học của trang kết quả (tránh N+1). */
    private Map<Long, ClassEntity> loadClasses(Collection<StudentDiscountEntity> rows) {
        List<Long> ids = rows.stream().map(StudentDiscountEntity::getClassId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return classRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ClassEntity::getId, Function.identity(), (a, b) -> a));
    }
}
