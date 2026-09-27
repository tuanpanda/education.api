package com.education.base.service.impl;

import com.education.base.dto.request.GenerateSessionsRequest;
import com.education.base.dto.request.SaveClassScheduleRequest;
import com.education.base.dto.request.WeeklySlotRequest;
import com.education.base.dto.response.GenerateSessionsResponse;
import com.education.base.dto.response.TimetableItemDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassScheduleEntity;
import com.education.base.entity.ClassSessionEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassScheduleRepository;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimetableServiceImplTest {

    @Mock
    private ClassRepository classRepository;
    @Mock
    private ClassScheduleRepository classScheduleRepository;
    @Mock
    private ClassSessionRepository classSessionRepository;
    @Mock
    private UserRepository userRepository;

    private TimetableServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TimetableServiceImpl(
                classRepository, classScheduleRepository, classSessionRepository, userRepository);
    }

    @Test
    void generateSessions_createsMatchingWeekdaysAndSkipsExisting() {
        ClassEntity clazz = ClassEntity.builder()
                .id(1L).classCode("EC920260001").className("Lớp 9A").subjectName("Toán")
                .isDeleted(0).build();
        when(classRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(clazz));
        ClassScheduleEntity monday = ClassScheduleEntity.builder()
                .id(50L).classId(1L).dayOfWeek(2).startTime("08:00").endTime("09:30")
                .roomName("A101").teacherId(7L).status("ACTIVE").isDeleted(0).build();
        when(classScheduleRepository.findByClassIdAndStatusAndIsDeletedOrderByDayOfWeekAscStartTimeAsc(
                1L, "ACTIVE", 0)).thenReturn(List.of(monday));
        when(classSessionRepository.existsByClassIdAndScheduleIdAndSessionDateAndIsDeleted(
                eq(1L), eq(50L), eq(LocalDate.of(2026, 9, 21)), eq(0))).thenReturn(false);
        when(classSessionRepository.existsByClassIdAndScheduleIdAndSessionDateAndIsDeleted(
                eq(1L), eq(50L), eq(LocalDate.of(2026, 9, 28)), eq(0))).thenReturn(true);
        when(classSessionRepository.existsRoomConflict(any(), any(), any(), any(), isNull())).thenReturn(false);
        when(classSessionRepository.existsTeacherConflict(any(), any(), any(), any(), isNull())).thenReturn(false);
        when(classSessionRepository.save(any(ClassSessionEntity.class))).thenAnswer(inv -> {
            ClassSessionEntity e = inv.getArgument(0);
            e.setId(100L);
            return e;
        });

        GenerateSessionsResponse result = service.generateSessions(1L, GenerateSessionsRequest.builder()
                .fromDate(LocalDate.of(2026, 9, 21))
                .toDate(LocalDate.of(2026, 9, 28))
                .build());

        ArgumentCaptor<ClassSessionEntity> captor = ArgumentCaptor.forClass(ClassSessionEntity.class);
        verify(classSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getSessionDate()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(captor.getValue().getStartTime()).isEqualTo("08:00");
        assertThat(captor.getValue().getStatus()).isEqualTo("SCHEDULED");
        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getSessions()).extracting(TimetableItemDto::getId).containsExactly(100L);
    }

    @Test
    void generateSessions_roomConflict_throwsWithoutSaving() {
        ClassEntity clazz = ClassEntity.builder().id(1L).className("Lớp 9A").isDeleted(0).build();
        when(classRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(clazz));
        when(classScheduleRepository.findByClassIdAndStatusAndIsDeletedOrderByDayOfWeekAscStartTimeAsc(
                1L, "ACTIVE", 0)).thenReturn(List.of(ClassScheduleEntity.builder()
                .id(50L).classId(1L).dayOfWeek(2).startTime("08:00").endTime("09:30")
                .roomName("A101").status("ACTIVE").isDeleted(0).build()));
        when(classSessionRepository.existsByClassIdAndScheduleIdAndSessionDateAndIsDeleted(
                1L, 50L, LocalDate.of(2026, 9, 21), 0)).thenReturn(false);
        when(classSessionRepository.existsRoomConflict(
                LocalDate.of(2026, 9, 21), "A101", "08:00", "09:30", null)).thenReturn(true);

        assertThatThrownBy(() -> service.generateSessions(1L, GenerateSessionsRequest.builder()
                .fromDate(LocalDate.of(2026, 9, 21))
                .toDate(LocalDate.of(2026, 9, 21))
                .build()))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("ROOM_TIME_CONFLICT");
        verify(classSessionRepository, never()).save(any());
    }

    @Test
    void saveClassSchedules_rejectsOverlappingSlotsSameDay() {
        when(classRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(
                ClassEntity.builder().id(1L).className("Lớp 9A").isDeleted(0).build()));

        SaveClassScheduleRequest request = SaveClassScheduleRequest.builder()
                .slots(List.of(
                        WeeklySlotRequest.builder().dayOfWeek(2).startTime("08:00").endTime("09:30").build(),
                        WeeklySlotRequest.builder().dayOfWeek(2).startTime("09:00").endTime("10:00").build()))
                .build();

        assertThatThrownBy(() -> service.saveClassSchedules(1L, request))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("SCHEDULE_SLOT_OVERLAP");
        verify(classScheduleRepository, never()).save(any());
    }
}
