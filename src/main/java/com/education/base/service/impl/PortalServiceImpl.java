package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.FeeStatusCalculator;
import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.TimetableFilterRequest;
import com.education.base.dto.response.PortalAttendanceDto;
import com.education.base.dto.response.PortalAttendanceSummaryDto;
import com.education.base.dto.response.PortalClassDetailDto;
import com.education.base.dto.response.PortalClassDto;
import com.education.base.dto.response.PortalDashboardResponse;
import com.education.base.dto.response.PortalFeeDetailDto;
import com.education.base.dto.response.PortalFeeListItemDto;
import com.education.base.dto.response.PortalGradeDto;
import com.education.base.dto.response.PortalMeResponse;
import com.education.base.dto.response.PortalNextSessionDto;
import com.education.base.dto.response.PortalOutstandingFeesSummaryDto;
import com.education.base.dto.response.PortalPaymentDto;
import com.education.base.dto.response.PortalTimetableItemDto;
import com.education.base.dto.response.PortalTimetableResponse;
import com.education.base.dto.response.TimetableItemDto;
import com.education.base.entity.AttendanceEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassSessionEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.GradeEntity;
import com.education.base.entity.PaymentTransactionEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.entity.UserEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassSessionRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.GradeRepository;
import com.education.base.repository.PaymentTransactionRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.security.AuthUserPrincipal;
import com.education.base.security.PortalStudentContext;
import com.education.base.security.SecurityUtils;
import com.education.base.service.AnnouncementQueryService;
import com.education.base.service.PortalService;
import com.education.base.service.TuitionSlipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortalServiceImpl implements PortalService {

    static final String STUDENT_NOT_FOUND = "STUDENT_NOT_FOUND";
    static final String INVALID_DATE_RANGE = "INVALID_DATE_RANGE";
    static final String DATE_RANGE_REQUIRED = "DATE_RANGE_REQUIRED";
    static final String CLASS_ID_REQUIRED = "CLASS_ID_REQUIRED";

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> OUTSTANDING_FEE_STATUSES = Set.of(
            DomainConstants.FEE_STATUS_UNPAID,
            DomainConstants.FEE_STATUS_PARTIAL,
            DomainConstants.FEE_STATUS_OVERDUE);

    private final PortalStudentContext portalStudentContext;
    private final StudentScopeGuard studentScopeGuard;
    private final StudentRepository studentRepository;
    private final ClassStudentRepository classStudentRepository;
    private final ClassSessionRepository classSessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final GradeRepository gradeRepository;
    private final TuitionFeeRepository tuitionFeeRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final TuitionSlipService tuitionSlipService;
    private final AnnouncementQueryService announcementQueryService;

    @Override
    @Transactional(readOnly = true)
    public PortalMeResponse me() {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        StudentEntity student = requireStudent(studentId);
        List<PortalClassDto> classes = new ArrayList<>();
        for (ClassStudentEntity enrollment : classStudentRepository.findActiveEnrollmentsWithClass(studentId)) {
            ClassEntity clazz = enrollment.getClazz();
            if (clazz == null) {
                continue;
            }
            classes.add(PortalClassDto.builder()
                    .classId(clazz.getId())
                    .classCode(clazz.getClassCode())
                    .className(clazz.getClassName())
                    .status(clazz.getStatus())
                    .build());
        }
        return PortalMeResponse.builder()
                .studentId(student.getId())
                .studentCode(student.getStudentCode())
                .fullName(student.getFullName())
                .dateOfBirth(student.getDateOfBirth())
                .gender(null)
                .classes(classes)
                .parentName(student.getParentName())
                .phone(student.getPhone())
                .email(student.getEmail())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PortalDashboardResponse dashboard() {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        List<ClassStudentEntity> enrollments = classStudentRepository.findActiveEnrollmentsWithClass(studentId);
        List<Long> classIds = enrollments.stream()
                .map(ClassStudentEntity::getClassId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        PortalNextSessionDto nextSession = null;
        if (!classIds.isEmpty()) {
            LocalDate today = LocalDate.now();
            String nowTime = LocalTime.now().format(HH_MM);
            List<ClassSessionEntity> upcoming = classSessionRepository
                    .findUpcomingScheduledSessions(classIds, today);
            for (ClassSessionEntity session : upcoming) {
                if (session.getSessionDate().isAfter(today)
                        || (session.getSessionDate().equals(today)
                        && session.getStartTime() != null
                        && session.getStartTime().compareTo(nowTime) >= 0)) {
                    nextSession = toNextSession(session);
                    break;
                }
            }
        }

        List<TuitionFeeEntity> fees = tuitionFeeRepository.findByStudentIdAndIsDeleted(
                studentId, PersistenceFlags.NOT_DELETED);
        long outstandingCount = 0L;
        BigDecimal totalRemaining = BigDecimal.ZERO;
        for (TuitionFeeEntity fee : fees) {
            if (fee.getStatus() == null || !OUTSTANDING_FEE_STATUSES.contains(fee.getStatus())) {
                continue;
            }
            outstandingCount++;
            totalRemaining = totalRemaining.add(FeeStatusCalculator.remainingOf(fee));
        }

        return PortalDashboardResponse.builder()
                .nextSession(nextSession)
                .outstandingFees(PortalOutstandingFeesSummaryDto.builder()
                        .outstandingCount(outstandingCount)
                        .totalRemaining(totalRemaining)
                        .build())
                .unreadAnnouncements(announcementQueryService.countUnread(currentUserId()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortalClassDetailDto> myClasses() {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        List<PortalClassDetailDto> result = new ArrayList<>();
        for (ClassStudentEntity enrollment : classStudentRepository.findActiveEnrollmentsWithClass(studentId)) {
            ClassEntity clazz = enrollment.getClazz();
            if (clazz == null) {
                continue;
            }
            UserEntity teacher = clazz.getTeacher();
            result.add(PortalClassDetailDto.builder()
                    .classId(clazz.getId())
                    .classCode(clazz.getClassCode())
                    .className(clazz.getClassName())
                    .status(clazz.getStatus())
                    .subjectName(clazz.getSubjectName())
                    .teacherId(clazz.getTeacherId())
                    .teacherName(teacher == null ? null : teacher.getFullName())
                    .roomName(clazz.getRoomName())
                    .startDate(clazz.getStartDate())
                    .endDate(clazz.getEndDate())
                    .build());
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PortalTimetableResponse timetable(LocalDate from, LocalDate to) {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        requireDateRange(from, to);
        TimetableFilterRequest filter = TimetableFilterRequest.builder()
                .fromDate(from)
                .toDate(to)
                .studentId(studentId)
                .build();
        List<TimetableItemDto> rows = classSessionRepository.findTimetableByRange(filter);
        List<PortalTimetableItemDto> items = rows.stream()
                .map(this::toPortalTimetableItem)
                .toList();
        return PortalTimetableResponse.builder()
                .fromDate(from)
                .toDate(to)
                .items(items)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortalAttendanceDto> attendance(Long classId, LocalDate from, LocalDate to) {
        if (classId == null) {
            throw new OracleBusinessException(CLASS_ID_REQUIRED, "Mã lớp không được để trống.");
        }
        studentScopeGuard.requireEnrolled(classId);
        Long studentId = portalStudentContext.requireCurrentStudentId();
        List<AttendanceEntity> rows = attendanceRepository
                .findByClassIdAndStudentIdAndIsDeleted(classId, studentId, PersistenceFlags.NOT_DELETED);
        return rows.stream()
                .filter(row -> inDateRange(row.getAttendanceDate(), from, to))
                .sorted(Comparator.comparing(AttendanceEntity::getAttendanceDate,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toPortalAttendance)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PortalAttendanceSummaryDto attendanceSummary(Long classId) {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        List<AttendanceEntity> rows;
        String classCode = null;
        String className = null;
        if (classId != null) {
            ClassStudentEntity enrollment = studentScopeGuard.requireEnrolled(classId);
            ClassEntity clazz = enrollment.getClazz();
            if (clazz != null) {
                classCode = clazz.getClassCode();
                className = clazz.getClassName();
            }
            rows = attendanceRepository.findByClassIdAndStudentIdAndIsDeleted(
                    classId, studentId, PersistenceFlags.NOT_DELETED);
        } else {
            rows = attendanceRepository.findByStudentIdAndIsDeleted(studentId, PersistenceFlags.NOT_DELETED);
            // Chỉ tính các lớp đang ENROLLED để tránh lộ điểm danh lớp đã DROPPED của học sinh khác ngữ cảnh
            Set<Long> enrolledClassIds = classStudentRepository.findActiveEnrollmentsWithClass(studentId).stream()
                    .map(ClassStudentEntity::getClassId)
                    .collect(Collectors.toCollection(HashSet::new));
            rows = rows.stream().filter(r -> enrolledClassIds.contains(r.getClassId())).toList();
        }
        long present = 0;
        long absent = 0;
        long late = 0;
        long excused = 0;
        for (AttendanceEntity row : rows) {
            if (DomainConstants.ATTENDANCE_PRESENT.equals(row.getStatus())) {
                present++;
            } else if ("ABSENT".equals(row.getStatus())) {
                absent++;
            } else if (DomainConstants.ATTENDANCE_LATE.equals(row.getStatus())) {
                late++;
            } else if ("EXCUSED".equals(row.getStatus())) {
                excused++;
            }
        }
        return PortalAttendanceSummaryDto.builder()
                .classId(classId)
                .classCode(classCode)
                .className(className)
                .presentCount(present)
                .absentCount(absent)
                .lateCount(late)
                .excusedCount(excused)
                .totalCount(present + absent + late + excused)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortalGradeDto> grades() {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        return gradeRepository.findByStudentIdAndIsDeleted(studentId, PersistenceFlags.NOT_DELETED).stream()
                .sorted(Comparator
                        .comparing(GradeEntity::getExamDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed()
                        .thenComparing(GradeEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toPortalGrade)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortalFeeListItemDto> fees() {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        return tuitionFeeRepository.findByStudentIdAndIsDeleted(studentId, PersistenceFlags.NOT_DELETED).stream()
                .sorted(Comparator.comparing(TuitionFeeEntity::getDueDate,
                                Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                        .thenComparing(TuitionFeeEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toPortalFeeListItem)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PortalFeeDetailDto feeDetail(Long feeId) {
        TuitionFeeEntity fee = studentScopeGuard.requireOwnFee(feeId);
        List<PortalPaymentDto> payments = paymentTransactionRepository
                .findByTuitionFeeIdAndIsDeleted(fee.getId(), PersistenceFlags.NOT_DELETED).stream()
                .sorted(Comparator.comparing(PaymentTransactionEntity::getPaymentDate,
                                Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                        .thenComparing(PaymentTransactionEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(tx -> toPortalPayment(tx, fee))
                .toList();
        ClassEntity clazz = fee.getClazz();
        return PortalFeeDetailDto.builder()
                .id(fee.getId())
                .feeCode(fee.getFeeCode())
                .classId(fee.getClassId())
                .classCode(clazz == null ? null : clazz.getClassCode())
                .className(clazz == null ? null : clazz.getClassName())
                .feeMonth(fee.getFeeMonth())
                .feeYear(fee.getFeeYear())
                .totalAmount(nvl(fee.getTotalAmount()))
                .discountAmount(nvl(fee.getDiscountAmount()))
                .paidAmount(nvl(fee.getPaidAmount()))
                .remainingAmount(FeeStatusCalculator.remainingOf(fee))
                .dueDate(fee.getDueDate())
                .status(fee.getStatus())
                .payments(payments)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public String feeSlipHtml(Long feeId) {
        studentScopeGuard.requireOwnFee(feeId);
        return tuitionSlipService.generateSlipHtml(feeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortalPaymentDto> payments() {
        Long studentId = portalStudentContext.requireCurrentStudentId();
        List<TuitionFeeEntity> fees = tuitionFeeRepository.findByStudentIdAndIsDeleted(
                studentId, PersistenceFlags.NOT_DELETED);
        if (fees.isEmpty()) {
            return List.of();
        }
        // Map feeId -> fee for display fields
        var feeById = fees.stream().collect(Collectors.toMap(TuitionFeeEntity::getId, f -> f, (a, b) -> a));
        List<PortalPaymentDto> result = new ArrayList<>();
        for (TuitionFeeEntity fee : fees) {
            for (PaymentTransactionEntity tx : paymentTransactionRepository
                    .findByTuitionFeeIdAndIsDeleted(fee.getId(), PersistenceFlags.NOT_DELETED)) {
                result.add(toPortalPayment(tx, feeById.get(fee.getId())));
            }
        }
        result.sort(Comparator.comparing(PortalPaymentDto::getPaymentDate,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                .thenComparing(PortalPaymentDto::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    // ------------------------------------------------------------------ helpers

    /** SYS_USERS.ID của tài khoản học sinh đang đăng nhập (đọc trạng thái đã đọc thông báo theo USER_ID). */
    private static Long currentUserId() {
        return SecurityUtils.currentUser().map(AuthUserPrincipal::getId).orElse(null);
    }

    private StudentEntity requireStudent(Long studentId) {
        return studentRepository.findByIdAndIsDeleted(studentId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(STUDENT_NOT_FOUND,
                        "Không tìm thấy hồ sơ học sinh. Vui lòng liên hệ trung tâm."));
    }

    private static void requireDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new OracleBusinessException(DATE_RANGE_REQUIRED,
                    "Khoảng ngày from/to không được để trống.");
        }
        if (to.isBefore(from)) {
            throw new OracleBusinessException(INVALID_DATE_RANGE,
                    "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        }
    }

    private static boolean inDateRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) {
            return false;
        }
        if (from != null && date.isBefore(from)) {
            return false;
        }
        if (to != null && date.isAfter(to)) {
            return false;
        }
        return true;
    }

    private PortalNextSessionDto toNextSession(ClassSessionEntity session) {
        ClassEntity clazz = session.getClazz();
        UserEntity teacher = session.getTeacher();
        return PortalNextSessionDto.builder()
                .sessionId(session.getId())
                .classId(session.getClassId())
                .classCode(clazz == null ? null : clazz.getClassCode())
                .className(clazz == null ? null : clazz.getClassName())
                .sessionDate(session.getSessionDate())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .roomName(session.getRoomName())
                .teacherName(teacher == null ? null : teacher.getFullName())
                .topic(session.getTopic())
                .status(session.getStatus())
                .build();
    }

    private PortalTimetableItemDto toPortalTimetableItem(TimetableItemDto row) {
        return PortalTimetableItemDto.builder()
                .sessionId(row.getId())
                .classId(row.getClassId())
                .classCode(row.getClassCode())
                .className(row.getClassName())
                .sessionDate(row.getSessionDate())
                .dayOfWeek(row.getDayOfWeek())
                .startTime(row.getStartTime())
                .endTime(row.getEndTime())
                .roomName(row.getRoomName())
                .teacherId(row.getTeacherId())
                .teacherName(row.getTeacherName())
                .topic(row.getTopic())
                .status(row.getStatus())
                .calendarColor(row.getCalendarColor())
                .build();
    }

    private PortalAttendanceDto toPortalAttendance(AttendanceEntity row) {
        ClassEntity clazz = row.getClazz();
        return PortalAttendanceDto.builder()
                .id(row.getId())
                .classId(row.getClassId())
                .classCode(clazz == null ? null : clazz.getClassCode())
                .className(clazz == null ? null : clazz.getClassName())
                .attendanceDate(row.getAttendanceDate())
                .status(row.getStatus())
                .build();
    }

    private PortalGradeDto toPortalGrade(GradeEntity row) {
        ClassEntity clazz = row.getClazz();
        return PortalGradeDto.builder()
                .id(row.getId())
                .classId(row.getClassId())
                .classCode(clazz == null ? null : clazz.getClassCode())
                .className(clazz == null ? null : clazz.getClassName())
                .gradeType(row.getGradeType())
                .score(row.getScore())
                .weight(row.getWeight())
                .examDate(row.getExamDate())
                .build();
    }

    private PortalFeeListItemDto toPortalFeeListItem(TuitionFeeEntity fee) {
        ClassEntity clazz = fee.getClazz();
        return PortalFeeListItemDto.builder()
                .id(fee.getId())
                .feeCode(fee.getFeeCode())
                .classId(fee.getClassId())
                .classCode(clazz == null ? null : clazz.getClassCode())
                .className(clazz == null ? null : clazz.getClassName())
                .feeMonth(fee.getFeeMonth())
                .feeYear(fee.getFeeYear())
                .totalAmount(nvl(fee.getTotalAmount()))
                .discountAmount(nvl(fee.getDiscountAmount()))
                .paidAmount(nvl(fee.getPaidAmount()))
                .remainingAmount(FeeStatusCalculator.remainingOf(fee))
                .dueDate(fee.getDueDate())
                .status(fee.getStatus())
                .build();
    }

    private PortalPaymentDto toPortalPayment(PaymentTransactionEntity tx, TuitionFeeEntity fee) {
        ClassEntity clazz = fee == null ? null : fee.getClazz();
        return PortalPaymentDto.builder()
                .id(tx.getId())
                .transactionCode(tx.getTransactionCode())
                .tuitionFeeId(tx.getTuitionFeeId())
                .feeCode(fee == null ? null : fee.getFeeCode())
                .classId(fee == null ? null : fee.getClassId())
                .classCode(clazz == null ? null : clazz.getClassCode())
                .className(clazz == null ? null : clazz.getClassName())
                .amount(tx.getAmount())
                .paymentMethod(tx.getPaymentMethod())
                .paymentDate(tx.getPaymentDate())
                .status(tx.getStatus())
                .receiptNo(tx.getReceiptNo())
                .transactionType(tx.getTransactionType())
                .payerName(tx.getPayerName())
                .refTransactionId(tx.getRefTransactionId())
                .build();
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
