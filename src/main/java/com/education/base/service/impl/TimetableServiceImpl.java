package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.TimeSlots;
import com.education.base.common.WeekdayCodes;
import com.education.base.dto.request.CancelSessionRequest;
import com.education.base.dto.request.GenerateSessionsRequest;
import com.education.base.dto.request.SaveClassScheduleRequest;
import com.education.base.dto.request.TimetableFilterRequest;
import com.education.base.dto.request.UpdateSessionRequest;
import com.education.base.dto.request.WeeklySlotRequest;
import com.education.base.dto.response.ClassScheduleResponse;
import com.education.base.dto.response.GenerateSessionsResponse;
import com.education.base.dto.response.TimetableDayDto;
import com.education.base.dto.response.TimetableItemDto;
import com.education.base.dto.response.TimetableResponse;
import com.education.base.dto.response.TimetableWeekDto;
import com.education.base.entity.AttendanceEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassScheduleEntity;
import com.education.base.entity.ClassSessionEntity;
import com.education.base.entity.UserEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassScheduleRepository;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.UserRepository;
import com.education.base.service.TimetableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimetableServiceImpl implements TimetableService {

    static final int MAX_GENERATE_DAYS = 180;

    private final ClassRepository classRepository;
    private final ClassScheduleRepository classScheduleRepository;
    private final ClassSessionRepository classSessionRepository;
    private final UserRepository userRepository;
    private final ClassStudentRepository classStudentRepository;
    private final AttendanceRepository attendanceRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ClassScheduleResponse> getClassSchedules(Long classId) {
        ClassEntity clazz = requireActiveClass(classId);
        return classScheduleRepository
                .findByClassIdAndStatusAndIsDeletedOrderByDayOfWeekAscStartTimeAsc(
                        clazz.getId(), "ACTIVE", PersistenceFlags.NOT_DELETED)
                .stream()
                .map(this::toScheduleResponse)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ClassScheduleResponse> saveClassSchedules(Long classId, SaveClassScheduleRequest request) {
        ClassEntity clazz = requireActiveClass(classId);
        List<WeeklySlotRequest> slots = request.getSlots() == null ? List.of() : request.getSlots();
        List<NormalizedSlot> normalized = normalizeSlots(clazz, slots);
        assertNoInternalOverlap(normalized);
        for (NormalizedSlot slot : normalized) {
            assertWeeklyConflicts(clazz.getId(), slot);
        }

        List<ClassScheduleEntity> existing = classScheduleRepository
                .findByClassIdAndIsDeletedOrderByDayOfWeekAscStartTimeAsc(
                        clazz.getId(), PersistenceFlags.NOT_DELETED);
        for (ClassScheduleEntity row : existing) {
            row.setIsDeleted(PersistenceFlags.DELETED);
            row.setStatus("INACTIVE");
        }
        classScheduleRepository.saveAll(existing);

        List<ClassScheduleEntity> saved = new ArrayList<>();
        for (NormalizedSlot slot : normalized) {
            saved.add(classScheduleRepository.save(ClassScheduleEntity.builder()
                    .classId(clazz.getId())
                    .dayOfWeek(slot.dayOfWeek())
                    .startTime(slot.startTime())
                    .endTime(slot.endTime())
                    .roomName(slot.roomName())
                    .teacherId(slot.teacherId())
                    .status("ACTIVE")
                    .isDeleted(PersistenceFlags.NOT_DELETED)
                    .build()));
        }
        log.info("Đã cấu hình {} khung lịch tuần cho lớp id={}", saved.size(), clazz.getId());
        return saved.stream().map(this::toScheduleResponse).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenerateSessionsResponse generateSessions(Long classId, GenerateSessionsRequest request) {
        ClassEntity clazz = requireActiveClass(classId);
        LocalDate from = request.getFromDate();
        LocalDate to = request.getToDate();
        validateDateRange(from, to);

        List<ClassScheduleEntity> schedules = classScheduleRepository
                .findByClassIdAndStatusAndIsDeletedOrderByDayOfWeekAscStartTimeAsc(
                        clazz.getId(), "ACTIVE", PersistenceFlags.NOT_DELETED);
        if (schedules.isEmpty()) {
            throw new OracleBusinessException("NO_SCHEDULE_CONFIGURED",
                    "Lớp chưa có khung lịch tuần. Hãy cấu hình lịch trước khi sinh buổi học.");
        }

        int created = 0;
        int skipped = 0;
        List<TimetableItemDto> createdSessions = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            int dow = WeekdayCodes.from(date);
            for (ClassScheduleEntity schedule : schedules) {
                if (!Integer.valueOf(dow).equals(schedule.getDayOfWeek())) {
                    continue;
                }
                if (classSessionRepository.existsByClassIdAndScheduleIdAndSessionDateAndIsDeleted(
                        clazz.getId(), schedule.getId(), date, PersistenceFlags.NOT_DELETED)) {
                    skipped++;
                    continue;
                }
                assertSessionConflicts(date, schedule.getRoomName(), schedule.getTeacherId(),
                        schedule.getStartTime(), schedule.getEndTime(), null);
                ClassSessionEntity session = classSessionRepository.save(ClassSessionEntity.builder()
                        .classId(clazz.getId())
                        .scheduleId(schedule.getId())
                        .sessionDate(date)
                        .startTime(schedule.getStartTime())
                        .endTime(schedule.getEndTime())
                        .roomName(schedule.getRoomName())
                        .teacherId(schedule.getTeacherId())
                        .topic(blankToNull(clazz.getSubjectName()))
                        .status("SCHEDULED")
                        .isDeleted(PersistenceFlags.NOT_DELETED)
                        .build());
                createdSessions.add(toItem(session, clazz));
                created++;
            }
        }
        log.info("Sinh buổi học lớp id={}: created={}, skipped={}, range={}..{}",
                clazz.getId(), created, skipped, from, to);
        return GenerateSessionsResponse.builder()
                .classId(clazz.getId())
                .fromDate(from)
                .toDate(to)
                .createdCount(created)
                .skippedCount(skipped)
                .sessions(createdSessions)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TimetableResponse getTimetable(TimetableFilterRequest filter) {
        TimetableFilterRequest criteria = filter == null ? new TimetableFilterRequest() : filter;
        validateDateRange(criteria.getFromDate(), criteria.getToDate());
        List<TimetableItemDto> items = hideClosedClassesAndApplyColors(
                classSessionRepository.findTimetableByRange(criteria));
        applyAttendanceCounts(items);
        return TimetableResponse.builder()
                .fromDate(criteria.getFromDate())
                .toDate(criteria.getToDate())
                .items(items)
                .weeks(groupByWeek(criteria.getFromDate(), criteria.getToDate(), items))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TimetableItemDto updateSession(Long sessionId, UpdateSessionRequest request) {
        ClassSessionEntity session = requireActiveSession(sessionId);
        if (!"SCHEDULED".equals(session.getStatus())) {
            throw new OracleBusinessException("SESSION_NOT_SCHEDULABLE",
                    "Chỉ được sửa buổi học ở trạng thái SCHEDULED.");
        }
        if (request.getSessionDate() != null) {
            session.setSessionDate(request.getSessionDate());
        }
        if (request.getStartTime() != null) {
            session.setStartTime(TimeSlots.normalize(request.getStartTime(), "Giờ bắt đầu"));
        }
        if (request.getEndTime() != null) {
            session.setEndTime(TimeSlots.normalize(request.getEndTime(), "Giờ kết thúc"));
        }
        TimeSlots.requireStartBeforeEnd(session.getStartTime(), session.getEndTime());
        if (request.getRoomName() != null) {
            session.setRoomName(blankToNull(request.getRoomName()));
        }
        if (request.getTeacherId() != null) {
            validateTeacher(request.getTeacherId());
            session.setTeacherId(request.getTeacherId());
        }
        if (request.getTopic() != null) {
            session.setTopic(blankToNull(request.getTopic()));
        }
        assertSessionConflicts(session.getSessionDate(), session.getRoomName(), session.getTeacherId(),
                session.getStartTime(), session.getEndTime(), session.getId());
        ClassSessionEntity saved = classSessionRepository.save(session);
        ClassEntity clazz = requireActiveClass(saved.getClassId());
        log.info("Đã cập nhật buổi học id={} lớp={}", saved.getId(), saved.getClassId());
        return toItem(saved, clazz);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TimetableItemDto cancelSession(Long sessionId, CancelSessionRequest request) {
        ClassSessionEntity session = requireActiveSession(sessionId);
        if ("COMPLETED".equals(session.getStatus())) {
            throw new OracleBusinessException("SESSION_NOT_SCHEDULABLE",
                    "Không thể hủy buổi học đã COMPLETED.");
        }
        if ("CANCELLED".equals(session.getStatus())) {
            throw new OracleBusinessException("SESSION_ALREADY_CANCELLED",
                    "Buổi học đã bị hủy trước đó.");
        }
        session.setStatus("CANCELLED");
        session.setNote(request.getReason().trim());
        ClassSessionEntity saved = classSessionRepository.save(session);
        ClassEntity clazz = requireActiveClass(saved.getClassId());
        log.info("Đã hủy buổi học id={} lớp={}", saved.getId(), saved.getClassId());
        return toItem(saved, clazz);
    }

    private List<NormalizedSlot> normalizeSlots(ClassEntity clazz, List<WeeklySlotRequest> slots) {
        List<NormalizedSlot> result = new ArrayList<>();
        for (WeeklySlotRequest raw : slots) {
            int dow = raw.getDayOfWeek();
            WeekdayCodes.toDayOfWeek(dow);
            String start = TimeSlots.normalize(raw.getStartTime(), "Giờ bắt đầu");
            String end = TimeSlots.normalize(raw.getEndTime(), "Giờ kết thúc");
            TimeSlots.requireStartBeforeEnd(start, end);
            Long teacherId = raw.getTeacherId() != null ? raw.getTeacherId() : clazz.getTeacherId();
            validateTeacher(teacherId);
            String room = blankToNull(raw.getRoomName());
            if (room == null) {
                room = blankToNull(clazz.getRoomName());
            }
            result.add(new NormalizedSlot(dow, start, end, room, teacherId));
        }
        return result;
    }

    private void assertNoInternalOverlap(List<NormalizedSlot> slots) {
        Map<Integer, List<NormalizedSlot>> byDay = new LinkedHashMap<>();
        for (NormalizedSlot slot : slots) {
            byDay.computeIfAbsent(slot.dayOfWeek(), key -> new ArrayList<>()).add(slot);
        }
        for (List<NormalizedSlot> sameDay : byDay.values()) {
            sameDay.sort(Comparator.comparing(NormalizedSlot::startTime));
            for (int i = 0; i < sameDay.size(); i++) {
                for (int j = i + 1; j < sameDay.size(); j++) {
                    NormalizedSlot a = sameDay.get(i);
                    NormalizedSlot b = sameDay.get(j);
                    if (a.startTime().equals(b.startTime())) {
                        throw new OracleBusinessException("DUPLICATE_WEEKLY_SLOT",
                                "Trùng khung giờ " + a.startTime() + " vào thứ " + a.dayOfWeek() + ".");
                    }
                    if (TimeSlots.overlaps(a.startTime(), a.endTime(), b.startTime(), b.endTime())) {
                        throw new OracleBusinessException("SCHEDULE_SLOT_OVERLAP",
                                "Hai khung lịch cùng thứ " + a.dayOfWeek()
                                        + " bị chồng giờ (" + a.startTime() + "-" + a.endTime()
                                        + " và " + b.startTime() + "-" + b.endTime() + ").");
                    }
                }
            }
        }
    }

    private void assertWeeklyConflicts(Long classId, NormalizedSlot slot) {
        if (slot.roomName() != null
                && classScheduleRepository.existsRoomConflict(
                classId, slot.dayOfWeek(), slot.roomName(), slot.startTime(), slot.endTime())) {
            throw new OracleBusinessException("ROOM_TIME_CONFLICT",
                    "Phòng " + slot.roomName() + " đã có lớp khác vào thứ " + slot.dayOfWeek()
                            + " khung " + slot.startTime() + "-" + slot.endTime() + ".");
        }
        if (slot.teacherId() != null
                && classScheduleRepository.existsTeacherConflict(
                classId, slot.dayOfWeek(), slot.teacherId(), slot.startTime(), slot.endTime())) {
            throw new OracleBusinessException("TEACHER_TIME_CONFLICT",
                    "Giảng viên ID " + slot.teacherId() + " đã có lớp khác vào thứ " + slot.dayOfWeek()
                            + " khung " + slot.startTime() + "-" + slot.endTime() + ".");
        }
    }

    private void assertSessionConflicts(LocalDate date, String roomName, Long teacherId,
                                        String startTime, String endTime, Long excludeId) {
        if (roomName != null
                && classSessionRepository.existsRoomConflict(date, roomName, startTime, endTime, excludeId)) {
            throw new OracleBusinessException("ROOM_TIME_CONFLICT",
                    "Phòng " + roomName + " đã có buổi học ngày " + date
                            + " khung " + startTime + "-" + endTime + ".");
        }
        if (teacherId != null
                && classSessionRepository.existsTeacherConflict(date, teacherId, startTime, endTime, excludeId)) {
            throw new OracleBusinessException("TEACHER_TIME_CONFLICT",
                    "Giảng viên ID " + teacherId + " đã có buổi học ngày " + date
                            + " khung " + startTime + "-" + endTime + ".");
        }
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new OracleBusinessException("INVALID_DATE_RANGE",
                    "Từ ngày và đến ngày không được để trống.");
        }
        if (to.isBefore(from)) {
            throw new OracleBusinessException("INVALID_DATE_RANGE",
                    "Đến ngày không được nhỏ hơn từ ngày.");
        }
        if (from.plusDays(MAX_GENERATE_DAYS).isBefore(to)) {
            throw new OracleBusinessException("GENERATE_RANGE_TOO_LONG",
                    "Khoảng ngày tối đa " + MAX_GENERATE_DAYS + " ngày.");
        }
    }

    private ClassEntity requireActiveClass(Long id) {
        if (id == null) {
            throw new OracleBusinessException("CLASS_ID_REQUIRED", "ID lớp học không được để trống.");
        }
        return classRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "CLASS_NOT_FOUND", "Không tìm thấy lớp học với ID: " + id));
    }

    private ClassSessionEntity requireActiveSession(Long id) {
        if (id == null) {
            throw new OracleBusinessException("SESSION_ID_REQUIRED", "ID buổi học không được để trống.");
        }
        return classSessionRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "SESSION_NOT_FOUND", "Không tìm thấy buổi học với ID: " + id));
    }

    private void validateTeacher(Long teacherId) {
        if (teacherId == null) {
            return;
        }
        userRepository.findByIdAndIsDeleted(teacherId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "TEACHER_NOT_FOUND", "Không tìm thấy giảng viên với ID: " + teacherId));
    }

    private ClassScheduleResponse toScheduleResponse(ClassScheduleEntity entity) {
        String teacherName = null;
        if (entity.getTeacherId() != null) {
            teacherName = userRepository.findByIdAndIsDeleted(entity.getTeacherId(), PersistenceFlags.NOT_DELETED)
                    .map(UserEntity::getFullName)
                    .orElse(null);
        }
        return ClassScheduleResponse.builder()
                .id(entity.getId())
                .classId(entity.getClassId())
                .dayOfWeek(entity.getDayOfWeek())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .roomName(entity.getRoomName())
                .teacherId(entity.getTeacherId())
                .teacherName(teacherName)
                .status(entity.getStatus())
                .build();
    }

    private TimetableItemDto toItem(ClassSessionEntity session, ClassEntity clazz) {
        String teacherName = null;
        if (session.getTeacherId() != null) {
            teacherName = userRepository.findByIdAndIsDeleted(session.getTeacherId(), PersistenceFlags.NOT_DELETED)
                    .map(UserEntity::getFullName)
                    .orElse(null);
        }
        return TimetableItemDto.builder()
                .id(session.getId())
                .classId(session.getClassId())
                .classCode(clazz.getClassCode())
                .className(clazz.getClassName())
                .scheduleId(session.getScheduleId())
                .sessionDate(session.getSessionDate())
                .dayOfWeek(WeekdayCodes.from(session.getSessionDate()))
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .roomName(session.getRoomName())
                .teacherId(session.getTeacherId())
                .teacherName(teacherName)
                .topic(session.getTopic())
                .status(session.getStatus())
                .note(session.getNote())
                .calendarColor(clazz.getCalendarColor())
                .build();
    }

    /**
     * Bỏ buổi học của lớp đã đóng / đã hủy ({@link DomainConstants#isHiddenFromTimetable}) và gắn màu lịch.
     * {@code PRC_GET_TIMETABLE_BY_RANGE} (V15) đã lọc ở Database; lọc lại ở đây để thời khóa biểu vẫn đúng
     * khi Database chưa chạy V15 (dùng chung một lần đọc lớp với phần gắn màu).
     */
    private List<TimetableItemDto> hideClosedClassesAndApplyColors(List<TimetableItemDto> items) {
        Set<Long> classIds = new HashSet<>();
        for (TimetableItemDto item : items) {
            if (item.getClassId() != null) {
                classIds.add(item.getClassId());
            }
        }
        if (classIds.isEmpty()) {
            return items;
        }
        Map<Long, String> colors = new HashMap<>();
        Set<Long> hiddenClassIds = new HashSet<>();
        for (ClassEntity clazz : classRepository.findAllById(classIds)) {
            if (DomainConstants.isHiddenFromTimetable(clazz.getStatus())) {
                hiddenClassIds.add(clazz.getId());
            } else if (clazz.getCalendarColor() != null && !clazz.getCalendarColor().isBlank()) {
                colors.put(clazz.getId(), clazz.getCalendarColor());
            }
        }
        List<TimetableItemDto> visible = new ArrayList<>(items.size());
        for (TimetableItemDto item : items) {
            if (item.getClassId() != null) {
                if (hiddenClassIds.contains(item.getClassId())) {
                    continue;
                }
                item.setCalendarColor(colors.get(item.getClassId()));
            }
            visible.add(item);
        }
        return visible;
    }

    private void applyAttendanceCounts(List<TimetableItemDto> items) {
        Set<Long> classIds = new HashSet<>();
        LocalDate from = null;
        LocalDate to = null;
        for (TimetableItemDto item : items) {
            if (item.getClassId() != null) {
                classIds.add(item.getClassId());
            }
            LocalDate date = item.getSessionDate();
            if (date == null) {
                continue;
            }
            if (from == null || date.isBefore(from)) {
                from = date;
            }
            if (to == null || date.isAfter(to)) {
                to = date;
            }
        }
        if (classIds.isEmpty()) {
            return;
        }
        Map<Long, Long> enrolled = new HashMap<>();
        for (Long classId : classIds) {
            enrolled.put(classId, classStudentRepository.countByClassIdAndStatusAndIsDeleted(
                    classId, "ENROLLED", PersistenceFlags.NOT_DELETED));
        }
        Map<String, long[]> dayStats = new HashMap<>();
        if (from != null && to != null) {
            List<AttendanceEntity> rows = attendanceRepository
                    .findByClassIdInAndAttendanceDateBetweenAndIsDeleted(
                            classIds, from, to, PersistenceFlags.NOT_DELETED);
            for (AttendanceEntity row : rows) {
                if (row.getClassId() == null || row.getAttendanceDate() == null) {
                    continue;
                }
                String key = row.getClassId() + "|" + row.getAttendanceDate();
                long[] stats = dayStats.computeIfAbsent(key, ignored -> new long[2]);
                stats[0]++;
                if (DomainConstants.ATTENDANCE_PRESENT.equals(row.getStatus())
                        || DomainConstants.ATTENDANCE_LATE.equals(row.getStatus())) {
                    stats[1]++;
                }
            }
        }
        for (TimetableItemDto item : items) {
            if (item.getClassId() == null) {
                continue;
            }
            item.setEnrolledCount(enrolled.getOrDefault(item.getClassId(), 0L));
            if (item.getSessionDate() == null) {
                item.setAttendanceMarkedCount(0L);
                item.setAttendedCount(0L);
                continue;
            }
            long[] stats = dayStats.getOrDefault(item.getClassId() + "|" + item.getSessionDate(), new long[2]);
            item.setAttendanceMarkedCount(stats[0]);
            item.setAttendedCount(stats[1]);
        }
    }

    static List<TimetableWeekDto> groupByWeek(LocalDate from, LocalDate to, List<TimetableItemDto> items) {
        Map<LocalDate, List<TimetableItemDto>> byDate = new LinkedHashMap<>();
        for (TimetableItemDto item : items) {
            if (item.getSessionDate() == null) {
                continue;
            }
            byDate.computeIfAbsent(item.getSessionDate(), key -> new ArrayList<>()).add(item);
        }
        LocalDate weekCursor = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastWeekStart = to.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<TimetableWeekDto> weeks = new ArrayList<>();
        while (!weekCursor.isAfter(lastWeekStart)) {
            LocalDate weekEnd = weekCursor.plusDays(6);
            List<TimetableDayDto> days = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDate date = weekCursor.plusDays(i);
                if (date.isBefore(from) || date.isAfter(to)) {
                    continue;
                }
                List<TimetableItemDto> daySessions = byDate.getOrDefault(date, List.of()).stream()
                        .sorted(Comparator.comparing(TimetableItemDto::getStartTime,
                                Comparator.nullsLast(String::compareTo)))
                        .toList();
                days.add(TimetableDayDto.builder()
                        .date(date)
                        .dayOfWeek(WeekdayCodes.from(date))
                        .sessions(daySessions)
                        .build());
            }
            weeks.add(TimetableWeekDto.builder()
                    .weekStart(weekCursor)
                    .weekEnd(weekEnd)
                    .days(days)
                    .build());
            weekCursor = weekCursor.plusWeeks(1);
        }
        return weeks;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record NormalizedSlot(int dayOfWeek, String startTime, String endTime, String roomName, Long teacherId) {
    }
}
