package com.education.base.service.impl;

import com.education.base.dto.request.GradeBatchRequest;
import com.education.base.dto.request.GradeUpsertRequest;
import com.education.base.dto.response.GradeResponseDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.GradeEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FinanceAcademicMapper;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.GradeRepository;
import com.education.base.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** GĐ2: điểm đồng bộ từ bài tập (ASSIGNMENT_ID != null) không sửa tay được qua API điểm. */
class GradeServiceSyncedReadonlyTest {

    GradeRepository gradeRepository = mock(GradeRepository.class);
    ClassRepository classRepository = mock(ClassRepository.class);
    ClassStudentRepository classStudentRepository = mock(ClassStudentRepository.class);
    StudentRepository studentRepository = mock(StudentRepository.class);
    FinanceAcademicMapper mapper = mock(FinanceAcademicMapper.class);
    GradeServiceImpl service = new GradeServiceImpl(gradeRepository, classRepository, classStudentRepository,
            studentRepository, mock(AttendanceRepository.class), mapper, mock(TeachingAssignmentGuard.class));

    @BeforeEach
    void setUp() {
        ClassEntity c = new ClassEntity();
        c.setId(2L);
        c.setClassCode("L2");
        when(classRepository.findByIdAndIsDeleted(2L, 0)).thenReturn(Optional.of(c));
        StudentEntity s = new StudentEntity();
        s.setId(8L);
        s.setStatus("ACTIVE");
        when(studentRepository.findByIdAndIsDeleted(8L, 0)).thenReturn(Optional.of(s));
        ClassStudentEntity e = new ClassStudentEntity();
        e.setStatus("ENROLLED");
        when(classStudentRepository.findByClassIdAndStudentIdAndIsDeleted(2L, 8L, 0)).thenReturn(Optional.of(e));
        when(gradeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(mapper.toGradeDto(any())).thenReturn(new GradeResponseDto());
    }

    static GradeBatchRequest batch(String type) {
        GradeUpsertRequest item = new GradeUpsertRequest();
        item.setClassId(2L);
        item.setStudentId(8L);
        item.setGradeType(type);
        item.setScore(BigDecimal.ONE);
        GradeBatchRequest r = new GradeBatchRequest();
        r.setGrades(List.of(item));
        return r;
    }

    static GradeEntity row(Long id, Long assignmentId) {
        return GradeEntity.builder().id(id).classId(2L).studentId(8L).gradeType("ASSIGNMENT").assignmentId(assignmentId)
                .score(BigDecimal.TEN).isDeleted(0).build();
    }

    @Test
    void onlySyncedRow_isReadonly() {
        when(gradeRepository.findByClassIdAndStudentIdAndGradeTypeOrderByIdAsc(2L, 8L, "ASSIGNMENT"))
                .thenReturn(List.of(row(1L, 99L)));
        assertThatThrownBy(() -> service.upsertBatch(batch("ASSIGNMENT"))).isInstanceOf(OracleBusinessException.class)
                .extracting(e -> ((OracleBusinessException) e).getErrorCode()).isEqualTo("GRADE_SYNCED_READONLY");
        verify(gradeRepository, never()).save(any());
    }

    @Test
    void manualRowNextToSyncedRows_isUpdated_syncedUntouched() {
        GradeEntity synced = row(1L, 99L);
        GradeEntity manual = row(2L, null);
        when(gradeRepository.findByClassIdAndStudentIdAndGradeTypeOrderByIdAsc(2L, 8L, "ASSIGNMENT"))
                .thenReturn(List.of(synced, manual));
        service.upsertBatch(batch("ASSIGNMENT"));
        assertThat(manual.getScore()).isEqualByComparingTo("1");
        assertThat(synced.getScore()).isEqualByComparingTo("10");
    }

    @Test
    void otherTypes_unchanged() {
        when(gradeRepository.findByClassIdAndStudentIdAndGradeType(2L, 8L, "MIDTERM")).thenReturn(Optional.empty());
        service.upsertBatch(batch("MIDTERM"));
        verify(gradeRepository).save(any());
    }
}
