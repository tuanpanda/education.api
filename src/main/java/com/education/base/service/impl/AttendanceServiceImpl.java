package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.AttendanceFilterRequest;
import com.education.base.dto.request.AttendanceMarkRequest;
import com.education.base.dto.response.AttendanceResponseDto;
import com.education.base.entity.AttendanceEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
    private final StudentRepository studentRepository;
    private final TeachingAssignmentGuard teachingAssignmentGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AttendanceResponseDto> markBatch(AttendanceMarkRequest request) {
        ClassEntity clazz = requireClass(request.getClassId());
        teachingAssignmentGuard.requireCanWrite(clazz,
                "Bạn chỉ được điểm danh cho lớp mình phụ trách (lớp " + clazz.getClassCode() + ").");
        List<AttendanceResponseDto> saved = new ArrayList<>();
        for (AttendanceMarkRequest.Entry entry : request.getEntries()) {
            requireListedStudent(entry.getStudentId());
            requireEnrolled(request.getClassId(), entry.getStudentId());
            AttendanceEntity entity = attendanceRepository
                    .findByClassIdAndStudentIdAndAttendanceDateAndIsDeleted(
                            request.getClassId(), entry.getStudentId(),
                            request.getAttendanceDate(), PersistenceFlags.NOT_DELETED)
                    .orElseGet(() -> AttendanceEntity.builder()
                            .classId(request.getClassId())
                            .studentId(entry.getStudentId())
                            .attendanceDate(request.getAttendanceDate())
                            .isDeleted(PersistenceFlags.NOT_DELETED)
                            .build());
            entity.setStatus(entry.getStatus());
            entity.setNote(entry.getNote());
            saved.add(toDto(attendanceRepository.save(entity), clazz));
        }
        log.info("Đã điểm danh {} học sinh lớp id={} ngày={}",
                saved.size(), request.getClassId(), request.getAttendanceDate());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponseDto> search(AttendanceFilterRequest filter) {
        AttendanceFilterRequest criteria = filter == null ? new AttendanceFilterRequest() : filter;
        if (criteria.getClassId() == null && criteria.getStudentId() == null) {
            throw new OracleBusinessException("ATTENDANCE_FILTER_REQUIRED",
                    "Cần truyền ID lớp hoặc ID học sinh khi tra cứu điểm danh.");
        }

        List<AttendanceEntity> rows = loadRows(criteria);
        if (criteria.getStudentId() != null) {
            rows = rows.stream().filter(a -> criteria.getStudentId().equals(a.getStudentId())).toList();
        }
        if (criteria.getClassId() != null) {
            rows = rows.stream().filter(a -> criteria.getClassId().equals(a.getClassId())).toList();
        }
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank()) {
            rows = rows.stream().filter(a -> criteria.getStatus().equals(a.getStatus())).toList();
        }

        List<AttendanceResponseDto> result = new ArrayList<>();
        for (AttendanceEntity row : rows) {
            StudentEntity student = studentRepository.findById(row.getStudentId()).orElse(null);
            if (!DomainConstants.isListedStudent(
                    student == null ? null : student.getStatus(),
                    student == null ? null : student.getIsDeleted())) {
                continue;
            }
            ClassEntity clazz = classRepository.findById(row.getClassId()).orElse(null);
            result.add(toDto(row, clazz));
        }
        return result;
    }

    private List<AttendanceEntity> loadRows(AttendanceFilterRequest criteria) {
        LocalDate from = criteria.getFromDate();
        LocalDate to = criteria.getToDate();
        if (from != null && to != null && to.isBefore(from)) {
            throw new OracleBusinessException("INVALID_DATE_RANGE",
                    "Ngày kết thúc không được nhỏ hơn ngày bắt đầu.");
        }

        if (criteria.getClassId() != null && from != null && to != null) {
            return attendanceRepository.findByClassIdAndAttendanceDateBetweenAndIsDeleted(
                    criteria.getClassId(), from, to, PersistenceFlags.NOT_DELETED);
        }
        if (criteria.getClassId() != null && from != null) {
            return attendanceRepository.findByClassIdAndAttendanceDateAndIsDeleted(
                    criteria.getClassId(), from, PersistenceFlags.NOT_DELETED);
        }
        if (criteria.getClassId() != null && to != null) {
            return attendanceRepository.findByClassIdAndAttendanceDateAndIsDeleted(
                    criteria.getClassId(), to, PersistenceFlags.NOT_DELETED);
        }
        if (criteria.getClassId() != null) {
            return attendanceRepository.findByClassIdAndIsDeleted(criteria.getClassId(), PersistenceFlags.NOT_DELETED);
        }
        List<AttendanceEntity> rows = attendanceRepository.findByStudentIdAndIsDeleted(
                criteria.getStudentId(), PersistenceFlags.NOT_DELETED);
        if (from != null && to != null) {
            return rows.stream()
                    .filter(a -> !a.getAttendanceDate().isBefore(from) && !a.getAttendanceDate().isAfter(to))
                    .toList();
        }
        if (from != null) {
            return rows.stream().filter(a -> !a.getAttendanceDate().isBefore(from)).toList();
        }
        if (to != null) {
            return rows.stream().filter(a -> !a.getAttendanceDate().isAfter(to)).toList();
        }
        return rows;
    }

    private ClassEntity requireClass(Long classId) {
        return classRepository.findByIdAndIsDeleted(classId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "CLASS_NOT_FOUND", "Không tìm thấy lớp học với ID: " + classId));
    }

    private void requireListedStudent(Long studentId) {
        studentRepository.findByIdAndIsDeleted(studentId, PersistenceFlags.NOT_DELETED)
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

    private AttendanceResponseDto toDto(AttendanceEntity entity, ClassEntity clazz) {
        StudentEntity student = studentRepository.findById(entity.getStudentId()).orElse(null);
        return AttendanceResponseDto.builder()
                .id(entity.getId())
                .classId(entity.getClassId())
                .classCode(clazz == null ? null : clazz.getClassCode())
                .className(clazz == null ? null : clazz.getClassName())
                .studentId(entity.getStudentId())
                .studentCode(student == null ? null : student.getStudentCode())
                .fullName(student == null ? null : student.getFullName())
                .attendanceDate(entity.getAttendanceDate())
                .status(entity.getStatus())
                .note(entity.getNote())
                .recordedById(entity.getRecordedById())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
