package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.GradeBatchRequest;
import com.education.base.dto.request.GradeUpsertRequest;
import com.education.base.dto.response.GradeResponseDto;
import com.education.base.dto.response.StudentGradeSummaryDto;
import com.education.base.entity.AttendanceEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.GradeEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FinanceAcademicMapper;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.GradeRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.service.GradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GradeServiceImpl implements GradeService {

    private final GradeRepository gradeRepository;
    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final FinanceAcademicMapper financeAcademicMapper;
    private final TeachingAssignmentGuard teachingAssignmentGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<GradeResponseDto> upsertBatch(GradeBatchRequest request) {
        List<GradeResponseDto> saved = new ArrayList<>();
        for (GradeUpsertRequest item : request.getGrades()) {
            ClassEntity clazz = requireClass(item.getClassId());
            teachingAssignmentGuard.requireCanWrite(clazz,
                    "Bạn chỉ được nhập điểm cho lớp mình phụ trách (lớp " + clazz.getClassCode() + ").");
            StudentEntity student = requireStudent(item.getStudentId());
            requireEnrolled(item.getClassId(), item.getStudentId());

            GradeEntity entity = gradeRepository
                    .findByClassIdAndStudentIdAndGradeType(item.getClassId(), item.getStudentId(), item.getGradeType())
                    .orElseGet(() -> GradeEntity.builder()
                            .classId(item.getClassId())
                            .studentId(item.getStudentId())
                            .gradeType(item.getGradeType())
                            .isDeleted(PersistenceFlags.NOT_DELETED)
                            .build());
            entity.setScore(item.getScore());
            entity.setWeight(item.getWeight() == null ? BigDecimal.ONE : item.getWeight());
            entity.setExamDate(item.getExamDate());
            entity.setNote(item.getNote());
            entity.setIsDeleted(PersistenceFlags.NOT_DELETED);
            saved.add(toDto(gradeRepository.save(entity), clazz, student));
        }
        log.info("Đã nhập {} đầu điểm", saved.size());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GradeResponseDto> listByClass(Long classId, Long studentId) {
        if (classId == null && studentId == null) {
            throw new OracleBusinessException("GRADE_FILTER_REQUIRED",
                    "Cần truyền ID lớp hoặc ID học sinh khi tra cứu điểm.");
        }
        List<GradeEntity> rows;
        if (classId != null && studentId != null) {
            rows = gradeRepository.findByClassIdAndStudentIdAndIsDeleted(
                    classId, studentId, PersistenceFlags.NOT_DELETED);
        } else if (classId != null) {
            requireClass(classId);
            rows = gradeRepository.findByClassIdAndIsDeleted(classId, PersistenceFlags.NOT_DELETED);
        } else {
            requireStudent(studentId);
            rows = gradeRepository.findByStudentIdAndIsDeleted(studentId, PersistenceFlags.NOT_DELETED);
        }
        List<GradeResponseDto> result = new ArrayList<>();
        for (GradeEntity row : rows) {
            ClassEntity clazz = classRepository.findById(row.getClassId()).orElse(null);
            StudentEntity student = studentRepository.findById(row.getStudentId()).orElse(null);
            if (!DomainConstants.isListedStudent(
                    student == null ? null : student.getStatus(),
                    student == null ? null : student.getIsDeleted())) {
                continue;
            }
            result.add(toDto(row, clazz, student));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentGradeSummaryDto summarize(Long classId, Long studentId) {
        ClassEntity clazz = requireClass(classId);
        StudentEntity student = requireStudent(studentId);
        List<GradeResponseDto> grades = listByClass(classId, studentId);

        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;
        for (GradeResponseDto grade : grades) {
            BigDecimal weight = grade.getWeight() == null ? BigDecimal.ONE : grade.getWeight();
            BigDecimal score = grade.getScore() == null ? BigDecimal.ZERO : grade.getScore();
            weightedSum = weightedSum.add(score.multiply(weight));
            weightSum = weightSum.add(weight);
        }
        BigDecimal average = weightSum.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : weightedSum.divide(weightSum, 2, RoundingMode.HALF_UP);

        List<AttendanceEntity> sessions = attendanceRepository.findByClassIdAndStudentIdAndIsDeleted(
                classId, studentId, PersistenceFlags.NOT_DELETED);
        int present = (int) sessions.stream()
                .filter(a -> "PRESENT".equals(a.getStatus()) || "LATE".equals(a.getStatus()))
                .count();

        return StudentGradeSummaryDto.builder()
                .classId(clazz.getId())
                .classCode(clazz.getClassCode())
                .className(clazz.getClassName())
                .studentId(student.getId())
                .studentCode(student.getStudentCode())
                .fullName(student.getFullName())
                .grades(grades)
                .weightedAverage(average)
                .presentSessions(present)
                .totalSessions(sessions.size())
                .build();
    }

    private ClassEntity requireClass(Long classId) {
        return classRepository.findByIdAndIsDeleted(classId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "CLASS_NOT_FOUND", "Không tìm thấy lớp học với ID: " + classId));
    }

    private StudentEntity requireStudent(Long studentId) {
        return studentRepository.findByIdAndIsDeleted(studentId, PersistenceFlags.NOT_DELETED)
                .filter(s -> DomainConstants.STUDENT_STATUS_ACTIVE.equals(s.getStatus()))
                .orElseThrow(() -> new OracleBusinessException(
                        "STUDENT_NOT_FOUND", "Không tìm thấy học sinh với ID: " + studentId));
    }

    private void requireEnrolled(Long classId, Long studentId) {
        classStudentRepository.findByClassIdAndStudentIdAndIsDeleted(
                        classId, studentId, PersistenceFlags.NOT_DELETED)
                .filter(e -> "ENROLLED".equals(e.getStatus()) || "COMPLETED".equals(e.getStatus()))
                .orElseThrow(() -> new OracleBusinessException(
                        "STUDENT_NOT_ENROLLED",
                        "Học sinh ID " + studentId + " chưa ghi danh lớp ID " + classId));
    }

    private GradeResponseDto toDto(GradeEntity entity, ClassEntity clazz, StudentEntity student) {
        GradeResponseDto dto = financeAcademicMapper.toGradeDto(entity);
        if (clazz != null) {
            dto.setClassCode(clazz.getClassCode());
            dto.setClassName(clazz.getClassName());
        }
        if (student != null) {
            dto.setStudentCode(student.getStudentCode());
            dto.setFullName(student.getFullName());
        }
        return dto;
    }
}
