package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.FeePeriods;
import com.education.base.common.FeeStatusCalculator;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.VietQrHelper;
import com.education.base.dto.request.GenerateRangeInvoicesRequestDto;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.dto.response.BillableSessionsPreviewDto;
import com.education.base.dto.response.BillableSessionsPreviewDto.StudentRow;
import com.education.base.dto.response.BillableSessionsPreviewDto.Totals;
import com.education.base.dto.response.FeeStudentErrorDto;
import com.education.base.dto.response.GenerateInvoicesResponseDto;
import com.education.base.dto.response.TuitionSlipResponseDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.entity.TuitionFeeSessionEntity;
import com.education.base.exception.BusinessDataException;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.repository.TuitionFeeSessionRepository;
import com.education.base.repository.projection.BillableAttendanceRow;
import com.education.base.repository.projection.FeeSessionConflictRow;
import com.education.base.security.SecurityUtils;
import com.education.base.service.BankAccountService;
import com.education.base.service.TuitionSlipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phiếu học phí theo khoảng ngày (SPEC-fee-date-range v1.1, contract fee-range-api v1.3).
 * <ul>
 *     <li>Buổi tính phí = {@code EDU_ATTENDANCE} của lớp, {@code PRESENT}/{@code LATE}, chưa xóa, trong
 *     {@code fromDate..toDate} (cả hai đầu). Thu cả học sinh đã nghỉ lớp nếu có buổi ({@code inactive}).</li>
 *     <li>Tiền = số buổi x đơn giá (scale 2, HALF_UP). Tạm thời KHÔNG giảm giá ({@code DISCOUNT_AMOUNT = 0}).</li>
 *     <li>Có buổi đã nằm trên phiếu chưa hủy / chưa xóa -> 409 {@code FEE_SESSION_CONFLICT}, không tạo phiếu nào.</li>
 *     <li>{@code FEE_MONTH}/{@code FEE_YEAR} = NULL (không đụng {@code UQ_FEES_MONTHLY}).</li>
 * </ul>
 */
@Slf4j
@Service
public class TuitionSlipServiceImpl implements TuitionSlipService {

    private static final int QR_SIZE = 360;
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter NOTE_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    /** {@code packageDates} trong preview (contract v1.4 §13). */
    private static final DateTimeFormatter PACKAGE_DATE = DAY_MONTH;
    /** {@code details[].code} (khung lỗi thống nhất với FE). */
    static final String CODE_STUDENTS_REQUIRED = "STUDENTS_REQUIRED";
    static final String CODE_STUDENT_ID_REQUIRED = "STUDENT_ID_REQUIRED";
    static final String CODE_DUPLICATE_STUDENT = "DUPLICATE_STUDENT";
    static final String CODE_PARTIAL_REASON_REQUIRED = "PARTIAL_REASON_REQUIRED";
    static final String CODE_PARTIAL_REASON_TOO_LONG = "PARTIAL_REASON_TOO_LONG";
    static final String CODE_NOT_IN_CLASS = "NOT_IN_CLASS";
    static final String CODE_NO_UNBILLED_SESSION = "NO_UNBILLED_SESSION";
    static final String CODE_STUDENT_NOT_ELIGIBLE = DomainConstants.STUDENT_NOT_ELIGIBLE;
    /** {@code details[].field}. */
    static final String FIELD_STUDENTS = "students";
    static final String FIELD_STUDENT_ID = "studentId";
    static final String FIELD_PARTIAL_REASON = "partialReason";

    private final TuitionFeeRepository tuitionFeeRepository;
    private final TuitionFeeSessionRepository tuitionFeeSessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final ClassStudentRepository classStudentRepository;
    private final ClassRepository classRepository;
    private final StudentRepository studentRepository;
    private final BankAccountService bankAccountService;

    public TuitionSlipServiceImpl(TuitionFeeRepository tuitionFeeRepository,
                                  TuitionFeeSessionRepository tuitionFeeSessionRepository,
                                  AttendanceRepository attendanceRepository,
                                  ClassStudentRepository classStudentRepository,
                                  ClassRepository classRepository,
                                  StudentRepository studentRepository,
                                  BankAccountService bankAccountService) {
        this.tuitionFeeRepository = tuitionFeeRepository;
        this.tuitionFeeSessionRepository = tuitionFeeSessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.classStudentRepository = classStudentRepository;
        this.classRepository = classRepository;
        this.studentRepository = studentRepository;
        this.bankAccountService = bankAccountService;
    }

    // ---------------------------------------------------------------------------------------------
    // Xem trước
    // ---------------------------------------------------------------------------------------------

    /**
     * Contract v1.4 §13: mỗi học sinh chỉ đếm buổi PRESENT/LATE trong khoảng <b>chưa</b> nằm trên phiếu hiệu lực
     * (phiếu đã hủy không giữ buổi - §13.4). {@code totalAmount} = giá x số buổi của phiếu sẽ tạo
     * ({@code min(unbilledCount, 10)}).
     */
    @Override
    @Transactional(readOnly = true)
    public BillableSessionsPreviewDto previewBillable(Long classId, LocalDate fromDate, LocalDate toDate,
                                                      BigDecimal pricePerSession) {
        validateRange(fromDate, toDate);
        BigDecimal price = pricePerSession == null ? null : validatePrice(pricePerSession);
        ClassEntity clazz = requireClass(classId);
        BillingPlan plan = plan(clazz, fromDate, toDate);

        List<StudentRow> rows = new ArrayList<>();
        int billable = 0;
        int skipped = 0;
        int sessions = 0;
        int eligibleStudents = 0;
        BigDecimal total = price == null ? null : BigDecimal.ZERO.setScale(2);
        for (PlannedStudent s : plan.students()) {
            int unbilled = s.sessions().size();
            List<BillableAttendanceRow> pack = packageOf(s.sessions());
            BigDecimal amount = price == null ? null : amountOf(price, pack.size());
            boolean eligible = unbilled >= DomainConstants.FEE_PACKAGE_SIZE;
            rows.add(StudentRow.builder()
                    .studentId(s.student().getId())
                    .studentCode(s.student().getStudentCode())
                    .studentName(s.student().getFullName())
                    .sessionDates(s.sessions().stream().map(BillableAttendanceRow::attendanceDate).toList())
                    .sessionCount(unbilled)
                    .unbilledCount(unbilled)
                    .eligible(eligible)
                    .packageDates(pack.stream().map(r -> r.attendanceDate().format(PACKAGE_DATE)).toList())
                    .totalAmount(amount)
                    .discountAmount(price == null ? null : BigDecimal.ZERO.setScale(2))
                    .netAmount(amount)
                    .conflictDates(new ArrayList<>())
                    .conflictFeeCodes(new ArrayList<>())
                    .inactive(s.inactive())
                    .build());
            if (unbilled == 0) {
                skipped++;
            } else {
                billable++;
                sessions += unbilled;
                if (total != null) {
                    total = total.add(amount);
                }
            }
            if (eligible) {
                eligibleStudents++;
            }
        }
        return BillableSessionsPreviewDto.builder()
                .classId(clazz.getId())
                .fromDate(fromDate)
                .toDate(toDate)
                .pricePerSession(price)
                .students(rows)
                .totals(Totals.builder()
                        .studentCount(rows.size())
                        .billableStudentCount(billable)
                        .skippedNoAttendance(skipped)
                        .sessionCount(sessions)
                        .totalAmount(total)
                        .discountAmount(price == null ? null : BigDecimal.ZERO.setScale(2))
                        .netAmount(total)
                        .conflictStudentCount(0)
                        .eligibleStudentCount(eligibleStudents)
                        .build())
                .build();
    }

    // ---------------------------------------------------------------------------------------------
    // Sinh phiếu
    // ---------------------------------------------------------------------------------------------

    /**
     * Một transaction, tất cả hoặc không (contract v1.4 §13.3):
     * <ul>
     *     <li>Kiểm toàn bộ {@code students} trước khi ghi; có lỗi thì 400 với {@code data.details} đủ mọi học sinh
     *     lỗi ({@code VALIDATION_ERROR} nếu có lỗi dữ liệu request, ngược lại {@code STUDENT_NOT_ELIGIBLE}).</li>
     *     <li>Mỗi học sinh một phiếu: 10 buổi chưa thu cũ nhất; chưa đủ 10 chỉ khi {@code allowPartial} + lý do
     *     (lấy hết, {@code IS_PARTIAL = 1}). Đủ 10 thì {@code allowPartial} bị bỏ qua (§13.5).</li>
     *     <li>Hai người tạo cùng lúc: ORA-00001 trên {@code UQ_FEE_SESSIONS_ATTENDANCE} -> 409, rollback (§13.7).</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenerateInvoicesResponseDto generateRange(GenerateRangeInvoicesRequestDto request) {
        GenerateRangeInvoicesRequestDto payload = request == null ? new GenerateRangeInvoicesRequestDto() : request;
        validateRange(payload.getFromDate(), payload.getToDate());
        BigDecimal price = validatePrice(payload.getPricePerSession());
        List<GenerateRangeInvoicesRequestDto.StudentSelection> selections = payload.getStudents();
        if (selections == null || selections.isEmpty()) {
            String message = "Chọn ít nhất một học sinh để tạo phiếu.";
            throw new BusinessDataException(VALIDATION_ERROR, message,
                    new FeeStudentErrorDto.Payload(List.of(FeeStudentErrorDto.builder()
                            .field(FIELD_STUDENTS).code(CODE_STUDENTS_REQUIRED).message(message).build())));
        }
        ClassEntity clazz = requireClass(payload.getClassId());
        // Thiếu STK thụ hưởng thì không ghi gì (phiếu cần QR).
        bankAccountService.requireActive();

        LocalDate from = payload.getFromDate();
        LocalDate to = payload.getToDate();
        BillingPlan plan = plan(clazz, from, to);
        List<SelectedStudent> selected = validateSelections(selections, plan);

        String comment = blankToNull(payload.getTeacherComment());
        String wish = firstNonBlank(payload.getFooterWish(), DomainConstants.TUITION_SLIP_DEFAULT_WISH);
        LocalDate today = LocalDate.now();
        String actor = SecurityUtils.currentUsername();

        GenerateInvoicesResponseDto result = GenerateInvoicesResponseDto.builder()
                .skippedNoAttendance((int) plan.students().stream()
                        .filter(s -> !s.inactive() && s.sessions().isEmpty()).count())
                .build();
        try {
            for (SelectedStudent s : selected) {
                List<BillableAttendanceRow> sessions = s.sessions();
                int count = sessions.size();
                LocalDate periodFrom = sessions.get(0).attendanceDate();
                LocalDate periodTo = sessions.get(count - 1).attendanceDate();
                LocalDate dueDate = resolveDueDate(periodTo, today);
                BigDecimal totalAmount = amountOf(price, count);
                BigDecimal discount = BigDecimal.ZERO.setScale(2);
                TuitionFeeEntity saved = tuitionFeeRepository.saveAndFlush(TuitionFeeEntity.builder()
                        .feeCode(tuitionFeeRepository.nextTuitionFeeCode())
                        .studentId(s.student().getId())
                        .classId(clazz.getId())
                        .totalAmount(totalAmount)
                        .discountAmount(discount)
                        .paidAmount(BigDecimal.ZERO)
                        .dueDate(dueDate)
                        .status(FeeStatusCalculator.resolveFeeStatus(totalAmount, discount, BigDecimal.ZERO, dueDate))
                        .note("HP " + periodFrom.format(NOTE_DATE) + "-" + periodTo.format(NOTE_DATE))
                        .feeMonth(null)
                        .feeYear(null)
                        .periodFrom(periodFrom)
                        .periodTo(periodTo)
                        .isRange(FeePeriods.RANGE_FLAG)
                        .isPartial(s.partialReason() == null ? 0 : FeePeriods.RANGE_FLAG)
                        .partialReason(s.partialReason())
                        .pricePerSession(price)
                        .totalSessions(count)
                        .teacherComment(comment)
                        .footerWish(wish)
                        .slipLabel(DomainConstants.TUITION_SLIP_LABEL_DEFAULT)
                        .isDeleted(PersistenceFlags.NOT_DELETED)
                        .createdBy(actor)
                        .build());
                List<TuitionFeeSessionEntity> rows = new ArrayList<>(count);
                for (BillableAttendanceRow row : sessions) {
                    rows.add(TuitionFeeSessionEntity.builder()
                            .feeId(saved.getId())
                            .attendanceId(row.attendanceId())
                            .sessionDate(row.attendanceDate())
                            .createdBy(actor)
                            .build());
                }
                tuitionFeeSessionRepository.saveAllAndFlush(rows);
                result.getFeeIds().add(saved.getId());
                result.getSlips().add(GenerateInvoicesResponseDto.CreatedSlip.builder()
                        .feeId(saved.getId())
                        .feeCode(saved.getFeeCode())
                        .studentId(s.student().getId())
                        .studentCode(s.student().getStudentCode())
                        .sessionCount(count)
                        .periodFrom(periodFrom)
                        .periodTo(periodTo)
                        .totalAmount(totalAmount)
                        .partial(s.partialReason() != null)
                        .partialReason(s.partialReason())
                        .build());
                result.setCreatedCount(result.getCreatedCount() + 1);
            }
        } catch (DataIntegrityViolationException e) {
            if (isFeeSessionUniqueViolation(e)) {
                log.warn("Sinh phiếu lớp {} {}..{}: trùng buổi khi ghi (ORA-00001 {}), rollback toàn bộ",
                        clazz.getClassCode(), from, to, DomainConstants.FEE_SESSIONS_UNIQUE_CONSTRAINT);
                throw conflict(List.of());
            }
            throw e;
        }

        log.info("Sinh phiếu học phí lớp {} từ {} đến {}: created={}, partial={}, actor={}",
                clazz.getClassCode(), from, to, result.getCreatedCount(),
                result.getSlips().stream().filter(GenerateInvoicesResponseDto.CreatedSlip::isPartial).count(), actor);
        return result;
    }

    /**
     * Kiểm mọi phần tử {@code students}, gom đủ lỗi rồi mới ném (contract v1.4 §13.3, §13.6). Trả về danh sách
     * học sinh hợp lệ (thứ tự request) kèm các buổi sẽ lên phiếu.
     */
    private static List<SelectedStudent> validateSelections(
            List<GenerateRangeInvoicesRequestDto.StudentSelection> selections, BillingPlan plan) {
        Map<Long, PlannedStudent> byId = new HashMap<>();
        for (PlannedStudent s : plan.students()) {
            byId.put(s.student().getId(), s);
        }
        List<FeeStudentErrorDto> errors = new ArrayList<>();
        boolean requestError = false;
        Set<Long> seen = new HashSet<>();
        List<SelectedStudent> selected = new ArrayList<>();
        for (GenerateRangeInvoicesRequestDto.StudentSelection sel : selections) {
            Long studentId = sel == null ? null : sel.getStudentId();
            if (studentId == null) {
                errors.add(error(null, null, null, FIELD_STUDENT_ID, CODE_STUDENT_ID_REQUIRED, "Thiếu studentId."));
                requestError = true;
                continue;
            }
            PlannedStudent planned = byId.get(studentId);
            Integer unbilled = planned == null ? null : planned.sessions().size();
            if (!seen.add(studentId)) {
                errors.add(error(studentId, planned, unbilled, FIELD_STUDENT_ID, CODE_DUPLICATE_STUDENT,
                        "Học sinh bị chọn trùng trong danh sách."));
                requestError = true;
                continue;
            }
            if (planned == null) {
                errors.add(error(studentId, null, 0, FIELD_STUDENT_ID, CODE_NOT_IN_CLASS,
                        "Học sinh không thuộc lớp."));
                continue;
            }
            int count = planned.sessions().size();
            if (count == 0) {
                errors.add(error(studentId, planned, 0, null, CODE_NO_UNBILLED_SESSION,
                        "Không có buổi chưa thu trong khoảng ngày."));
                continue;
            }
            if (count >= DomainConstants.FEE_PACKAGE_SIZE) {
                // §13.5: đủ gói -> bỏ qua allowPartial / partialReason, IS_PARTIAL = 0.
                selected.add(new SelectedStudent(planned.student(), packageOf(planned.sessions()), null));
                continue;
            }
            if (!Boolean.TRUE.equals(sel.getAllowPartial())) {
                errors.add(error(studentId, planned, count, null, CODE_STUDENT_NOT_ELIGIBLE,
                        "Chưa đủ " + DomainConstants.FEE_PACKAGE_SIZE + " buổi (hiện " + count + " buổi)."));
                continue;
            }
            String reason = blankToNull(sel.getPartialReason());
            if (reason == null) {
                errors.add(error(studentId, planned, count, FIELD_PARTIAL_REASON, CODE_PARTIAL_REASON_REQUIRED,
                        "Thu chưa đủ gói (" + count + " buổi) phải nhập lý do."));
                requestError = true;
                continue;
            }
            if (reason.codePointCount(0, reason.length()) > DomainConstants.FEE_PARTIAL_REASON_MAX) {
                errors.add(error(studentId, planned, count, FIELD_PARTIAL_REASON, CODE_PARTIAL_REASON_TOO_LONG,
                        "Lý do không được vượt quá " + DomainConstants.FEE_PARTIAL_REASON_MAX + " ký tự."));
                requestError = true;
                continue;
            }
            selected.add(new SelectedStudent(planned.student(), planned.sessions(), reason));
        }
        if (!errors.isEmpty()) {
            throw new BusinessDataException(
                    requestError ? VALIDATION_ERROR : DomainConstants.STUDENT_NOT_ELIGIBLE,
                    requestError ? "Danh sách học sinh không hợp lệ."
                            : "Có học sinh không đủ điều kiện tạo phiếu.",
                    new FeeStudentErrorDto.Payload(errors));
        }
        return selected;
    }

    private static FeeStudentErrorDto error(Long studentId, PlannedStudent planned, Integer unbilled, String field,
                                            String code, String message) {
        return FeeStudentErrorDto.builder()
                .studentId(studentId)
                .studentName(planned == null ? null : planned.student().getFullName())
                .field(field)
                .code(code)
                .message(message)
                .unbilledCount(unbilled)
                .build();
    }

    /** Tối đa 10 buổi cũ nhất (danh sách đã sắp theo ngày, attendanceId). */
    private static List<BillableAttendanceRow> packageOf(List<BillableAttendanceRow> sessions) {
        return sessions.size() <= DomainConstants.FEE_PACKAGE_SIZE ? sessions
                : sessions.subList(0, DomainConstants.FEE_PACKAGE_SIZE);
    }

    /** ORA-00001 trên {@code UQ_FEE_SESSIONS_ATTENDANCE} (tìm trong cả chuỗi nguyên nhân). */
    public static boolean isFeeSessionUniqueViolation(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.toUpperCase(java.util.Locale.ROOT)
                    .contains(DomainConstants.FEE_SESSIONS_UNIQUE_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }

    static BusinessDataException conflict(List<FeeStudentErrorDto> details) {
        return new BusinessDataException(DomainConstants.FEE_SESSION_CONFLICT,
                DomainConstants.FEE_SESSION_CONFLICT_MESSAGE, new FeeStudentErrorDto.Payload(details));
    }

    /**
     * Danh sách học sinh + buổi <b>chưa thu</b> của lớp trong khoảng (contract v1.4 §13, §13.4).
     * <ul>
     *     <li>Buổi đã nằm trên phiếu hiệu lực (chưa hủy, chưa xóa) bị loại; khoảng ngày chồng nhau không bị chặn
     *     (§13.1). Hủy/xóa phiếu giải phóng {@code TUITION_FEE_SESSIONS} nên buổi được tính lại.</li>
     *     <li>Học sinh đang học (ENROLLED, còn được liệt kê): luôn có mặt, kể cả 0 buổi.</li>
     *     <li>Học sinh đã nghỉ lớp: chỉ có mặt khi còn ít nhất 1 buổi chưa thu ({@code inactive = true}).</li>
     * </ul>
     * Buổi đã thu tra bằng JOIN theo lớp + khoảng ngày (không IN-list - tránh ORA-01795).
     */
    private BillingPlan plan(ClassEntity clazz, LocalDate from, LocalDate to) {
        Set<Long> billed = new HashSet<>();
        for (FeeSessionConflictRow row : tuitionFeeSessionRepository.findActiveConflictsInClassRange(
                clazz.getId(), from, to, DomainConstants.BILLABLE_ATTENDANCE_STATUSES)) {
            if (row.attendanceId() != null) {
                billed.add(row.attendanceId());
            }
        }
        Map<Long, List<BillableAttendanceRow>> byStudent = new LinkedHashMap<>();
        for (BillableAttendanceRow row : attendanceRepository.findBillableInClassRange(
                clazz.getId(), from, to, DomainConstants.BILLABLE_ATTENDANCE_STATUSES)) {
            if (row.attendanceDate() == null || row.studentId() == null || billed.contains(row.attendanceId())) {
                continue;
            }
            byStudent.computeIfAbsent(row.studentId(), k -> new ArrayList<>()).add(row);
        }
        for (List<BillableAttendanceRow> rows : byStudent.values()) {
            rows.sort(Comparator.comparing(BillableAttendanceRow::attendanceDate)
                    .thenComparing(BillableAttendanceRow::attendanceId, Comparator.nullsLast(Comparator.naturalOrder())));
        }

        Map<Long, StudentEntity> students = new LinkedHashMap<>();
        Set<Long> active = new HashSet<>();
        for (ClassStudentEntity enrollment : classStudentRepository.findByClassIdAndIsDeleted(
                clazz.getId(), PersistenceFlags.NOT_DELETED)) {
            if (!"ENROLLED".equals(enrollment.getStatus()) || students.containsKey(enrollment.getStudentId())) {
                continue;
            }
            StudentEntity student = listedStudent(enrollment.getStudentId());
            if (student != null) {
                students.put(student.getId(), student);
                active.add(student.getId());
            }
        }
        for (Long studentId : byStudent.keySet()) {
            if (!students.containsKey(studentId)) {
                studentRepository.findById(studentId).ifPresent(s -> students.put(s.getId(), s));
            }
        }

        List<PlannedStudent> planned = new ArrayList<>();
        for (StudentEntity student : students.values()) {
            List<BillableAttendanceRow> rows = byStudent.getOrDefault(student.getId(), List.of());
            boolean inactive = !active.contains(student.getId());
            if (inactive && rows.isEmpty()) {
                continue;
            }
            planned.add(new PlannedStudent(student, rows, inactive));
        }
        planned.sort(Comparator.comparing((PlannedStudent p) -> p.student().getStudentCode(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(p -> p.student().getId()));

        return new BillingPlan(planned);
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new OracleBusinessException(VALIDATION_ERROR, "Từ ngày và đến ngày không được để trống.");
        }
        if (from.isAfter(to)) {
            throw new OracleBusinessException(VALIDATION_ERROR, "Từ ngày phải nhỏ hơn hoặc bằng đến ngày.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > DomainConstants.FEE_RANGE_MAX_DAYS) {
            throw new OracleBusinessException(VALIDATION_ERROR,
                    "Khoảng ngày tối đa " + DomainConstants.FEE_RANGE_MAX_DAYS + " ngày.");
        }
    }

    /** Đơn giá bắt buộc, {@code > 0}, tối đa 2 chữ số thập phân (không làm tròn - contract v1.3 §11). */
    static BigDecimal validatePrice(BigDecimal price) {
        if (price == null) {
            throw new OracleBusinessException(VALIDATION_ERROR, "Đơn giá mỗi buổi không được để trống.");
        }
        if (price.signum() <= 0) {
            throw new OracleBusinessException(VALIDATION_ERROR, "Đơn giá mỗi buổi phải lớn hơn 0.");
        }
        if (price.stripTrailingZeros().scale() > 2 || price.precision() - price.scale() > 13) {
            throw new OracleBusinessException(VALIDATION_ERROR,
                    "Đơn giá tối đa 13 chữ số phần nguyên và 2 chữ số thập phân.");
        }
        return price.setScale(2, RoundingMode.UNNECESSARY);
    }

    static BigDecimal amountOf(BigDecimal price, int sessions) {
        return price.multiply(BigDecimal.valueOf(sessions)).setScale(2, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------------------------------------
    // Phiếu
    // ---------------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public TuitionSlipResponseDto getSlip(Long invoiceId) {
        TuitionFeeEntity fee = requireFee(invoiceId);
        return enrichWithQr(loadSlip(fee), fee, bankAccountService.requireActive());
    }

    @Override
    @Transactional(readOnly = true)
    public String generateSlipHtml(Long invoiceId) {
        return generateSlipHtml(getSlip(invoiceId));
    }

    @Override
    public String generateSlipHtml(TuitionSlipResponseDto data) {
        if (data == null) {
            throw new OracleBusinessException("FEE_NOT_FOUND", "Không có dữ liệu phiếu học phí.");
        }
        return TuitionSlipHtmlRenderer.render(data);
    }

    /**
     * B5: hạn thu mặc định là ngày cuối kỳ; nếu ngày đó đã trước {@code today} (sinh bù kỳ cũ) thì
     * hạn thu = {@code today + FEE_BACKBILL_GRACE_DAYS} để phiếu không bị {@code OVERDUE} ngay.
     */
    static LocalDate resolveDueDate(LocalDate endOfPeriod, LocalDate today) {
        return endOfPeriod.isBefore(today) ? today.plusDays(DomainConstants.FEE_BACKBILL_GRACE_DAYS) : endOfPeriod;
    }

    private TuitionFeeEntity requireFee(Long invoiceId) {
        if (invoiceId == null) {
            throw new OracleBusinessException("FEE_ID_REQUIRED", "ID khoản học phí không được để trống.");
        }
        return tuitionFeeRepository.findByIdAndIsDeleted(invoiceId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "FEE_NOT_FOUND", "Không tìm thấy khoản học phí với ID: " + invoiceId));
    }

    /**
     * Thông tin phiếu từ {@code PRC_GET_TUITION_SLIP_DATA} (V14_1, không đổi); kỳ thu và ngày trên phiếu:
     * <ul>
     *     <li>Phiếu khoảng ngày: ngày đọc từ {@code FIN_TUITION_FEE_SESSIONS}; phiếu đã hủy không có ngày
     *     và KHÔNG đọc lại điểm danh.</li>
     *     <li>Phiếu tháng cũ: dùng {@code FIN_TUITION_FEE_SESSIONS} nếu đã backfill (V23_7), không thì dùng
     *     danh sách điểm danh của tháng từ procedure như trước.</li>
     * </ul>
     */
    private TuitionSlipResponseDto loadSlip(TuitionFeeEntity fee) {
        TuitionSlipResponseDto slip = tuitionFeeRepository.getTuitionSlipData(fee.getId());
        boolean range = FeePeriods.isRangeFee(fee.getIsRange());
        slip.setPeriodFrom(fee.getPeriodFrom());
        slip.setPeriodTo(fee.getPeriodTo());
        slip.setRangeFee(range);
        slip.setPartial(FeePeriods.isPartialFee(fee.getIsPartial()));
        slip.setPartialReason(fee.getPartialReason());
        if (range) {
            slip.setMonth(FeePeriods.displayMonth(null, fee.getPeriodFrom()));
            slip.setYear(FeePeriods.displayYear(null, null, fee.getPeriodFrom()));
            slip.setPeriodText(FeePeriods.periodText(null, null, fee.getPeriodFrom(), fee.getPeriodTo()));
            slip.setMonthYearText(slip.getPeriodText());
            slip.setAttendedDates(DomainConstants.FEE_STATUS_CANCELLED.equals(fee.getStatus())
                    ? new ArrayList<>()
                    : sessionBadges(fee.getId()));
        } else {
            slip.setMonthYearText(FeePeriods.formatMonthYear(slip.getMonth(), slip.getYear()));
            slip.setPeriodText(FeePeriods.periodText(fee.getFeeMonth(), fee.getFeeYear(), fee.getPeriodFrom(),
                    fee.getPeriodTo()));
            if (fee.getFeeMonth() != null) {
                List<String> snapshot = sessionBadges(fee.getId());
                if (!snapshot.isEmpty()) {
                    slip.setAttendedDates(snapshot);
                }
            }
        }
        if (slip.getTotalSessions() == null) {
            slip.setTotalSessions(slip.getAttendedDates() == null ? 0 : slip.getAttendedDates().size());
        }
        if (slip.getPricePerSession() == null && slip.getTotalSessions() != null && slip.getTotalSessions() > 0
                && slip.getTotalAmount() != null) {
            slip.setPricePerSession(slip.getTotalAmount()
                    .divide(BigDecimal.valueOf(slip.getTotalSessions()), 2, RoundingMode.HALF_UP));
        }
        if (slip.getFooterWish() == null || slip.getFooterWish().isBlank()) {
            slip.setFooterWish(DomainConstants.TUITION_SLIP_DEFAULT_WISH);
        }
        if (slip.getSlipLabel() == null || slip.getSlipLabel().isBlank()) {
            slip.setSlipLabel(DomainConstants.TUITION_SLIP_LABEL_DEFAULT);
        }
        return slip;
    }

    private List<String> sessionBadges(Long feeId) {
        List<String> badges = new ArrayList<>();
        tuitionFeeSessionRepository.findByFeeIdOrderBySessionDateAscIdAsc(feeId).stream()
                .map(TuitionFeeSessionEntity::getSessionDate)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .forEach(d -> badges.add(d.format(DAY_MONTH)));
        return badges;
    }

    /**
     * Gắn số tiền và VietQR vào phiếu theo khoản phí {@code fee} (nguồn sự thật, không dùng số của procedure).
     * B6: số tiền QR = {@link FeeStatusCalculator#remainingOf}; không sinh QR khi còn phải thu {@code <= 0}
     * hoặc khoản phí {@code PAID} / {@code CANCELLED}.
     */
    private TuitionSlipResponseDto enrichWithQr(TuitionSlipResponseDto slip, TuitionFeeEntity fee,
                                                BankAccountResponseDto account) {
        BigDecimal remaining = FeeStatusCalculator.remainingOf(fee);
        slip.setTotalAmount(fee.getTotalAmount());
        slip.setDiscountAmount(nvl(fee.getDiscountAmount()));
        slip.setPaidAmount(nvl(fee.getPaidAmount()));
        slip.setRemainingAmount(remaining);
        slip.setStatus(fee.getStatus());
        slip.setDueDate(fee.getDueDate());

        String bankBin = account.getBankBin();
        String accountNo = account.getAccountNo();
        String accountName = account.getAccountName();
        slip.setBankBin(bankBin);
        slip.setBankName(firstNonBlank(account.getBankName(), "Ngân hàng"));
        slip.setAccountNo(accountNo);
        slip.setAccountName(accountName);

        long amountVnd = remaining.setScale(0, RoundingMode.HALF_UP).longValue();
        if (!isQrPayable(fee.getStatus(), amountVnd)) {
            slip.setQrPayload(null);
            slip.setQrBase64(null);
            slip.setQuickPayUrl(null);
            slip.setTransferContent(null);
            return slip;
        }
        String description = transferContentOf(fee, slip.getStudentName(), slip.getStudentCode(), slip.getMonth());
        slip.setTransferContent(description);
        String payload = VietQrHelper.buildVietQrPayload(bankBin, accountNo, amountVnd, description);
        slip.setQrPayload(payload);
        slip.setQrBase64(VietQrHelper.generateQrBase64(payload, QR_SIZE, QR_SIZE));
        slip.setQuickPayUrl(VietQrHelper.buildQuickUrl(bankBin, accountNo, amountVnd, description, accountName));
        return slip;
    }

    /**
     * Nội dung chuyển khoản (= addInfo của QR = {@code transferContent} trả FE): {@code "<TEN> <code> HP ddMM-ddMM"}
     * cho phiếu khoảng ngày, {@code "<TEN> <code> HP T<m>"} cho phiếu cũ; tên chỉ dùng chỗ còn lại trong 25 ký tự.
     */
    static String transferContentOf(TuitionFeeEntity fee, String studentName, String studentCode, Integer legacyMonth) {
        if (FeePeriods.isRangeFee(fee.getIsRange())) {
            LocalDate to = fee.getPeriodTo() == null ? fee.getPeriodFrom() : fee.getPeriodTo();
            return FeePeriods.rangeTransferContent(studentName, studentCode, fee.getPeriodFrom(), to);
        }
        return FeePeriods.legacyTransferContent(studentName, studentCode, legacyMonth);
    }

    /** Có được sinh VietQR cho khoản phí: còn phải thu {@code > 0} và không {@code PAID} / {@code CANCELLED}. */
    static boolean isQrPayable(String status, long remainingVnd) {
        return remainingVnd > 0
                && !DomainConstants.FEE_STATUS_PAID.equals(status)
                && !DomainConstants.FEE_STATUS_CANCELLED.equals(status);
    }

    static String transferContent(String studentCode, Integer month) {
        return FeePeriods.legacyTransferContent(studentCode, month);
    }

    static String formatMonthYear(Integer month, Integer year) {
        return FeePeriods.formatMonthYear(month, year);
    }

    static String asciiFold(String input) {
        return FeePeriods.asciiFold(input);
    }

    private ClassEntity requireClass(Long classId) {
        if (classId == null) {
            throw new OracleBusinessException(VALIDATION_ERROR, "ID lớp học không được để trống.");
        }
        return classRepository.findByIdAndIsDeleted(classId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "CLASS_NOT_FOUND", "Không tìm thấy lớp học với ID: " + classId));
    }

    private StudentEntity listedStudent(Long studentId) {
        return studentRepository.findByIdAndIsDeleted(studentId, PersistenceFlags.NOT_DELETED)
                .filter(s -> DomainConstants.isListedStudent(s.getStatus(), s.getIsDeleted()))
                .orElse(null);
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback;
    }

    private record PlannedStudent(StudentEntity student, List<BillableAttendanceRow> sessions, boolean inactive) {
    }

    private record SelectedStudent(StudentEntity student, List<BillableAttendanceRow> sessions,
                                   String partialReason) {
    }

    private record BillingPlan(List<PlannedStudent> students) {
    }
}
