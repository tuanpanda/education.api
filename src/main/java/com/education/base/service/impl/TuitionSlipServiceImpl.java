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
import com.education.base.dto.response.FeeSessionConflictDto;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
        int conflictStudents = 0;
        BigDecimal total = price == null ? null : BigDecimal.ZERO.setScale(2);
        for (PlannedStudent s : plan.students()) {
            BigDecimal amount = price == null ? null : amountOf(price, s.sessions().size());
            StudentConflict conflict = plan.conflicts().get(s.student().getId());
            rows.add(StudentRow.builder()
                    .studentId(s.student().getId())
                    .studentCode(s.student().getStudentCode())
                    .studentName(s.student().getFullName())
                    .sessionDates(s.sessions().stream().map(BillableAttendanceRow::attendanceDate).toList())
                    .sessionCount(s.sessions().size())
                    .totalAmount(amount)
                    .discountAmount(price == null ? null : BigDecimal.ZERO.setScale(2))
                    .netAmount(amount)
                    .conflictDates(conflict == null ? new ArrayList<>() : new ArrayList<>(conflict.dates()))
                    .conflictFeeCodes(conflict == null ? new ArrayList<>() : new ArrayList<>(conflict.feeCodes()))
                    .inactive(s.inactive())
                    .build());
            if (s.sessions().isEmpty()) {
                skipped++;
            } else {
                billable++;
                sessions += s.sessions().size();
                if (total != null) {
                    total = total.add(amount);
                }
            }
            if (conflict != null) {
                conflictStudents++;
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
                        .conflictStudentCount(conflictStudents)
                        .build())
                .build();
    }

    // ---------------------------------------------------------------------------------------------
    // Sinh phiếu
    // ---------------------------------------------------------------------------------------------

    /**
     * Một transaction cho cả lớp: xung đột (kiểm trước khi ghi, hoặc ORA-00001 trên
     * {@code UQ_FEE_SESSIONS_ATTENDANCE} khi hai người tạo cùng lúc) -> 409 và rollback toàn bộ.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenerateInvoicesResponseDto generateRange(GenerateRangeInvoicesRequestDto request) {
        GenerateRangeInvoicesRequestDto payload = request == null ? new GenerateRangeInvoicesRequestDto() : request;
        validateRange(payload.getFromDate(), payload.getToDate());
        BigDecimal price = validatePrice(payload.getPricePerSession());
        ClassEntity clazz = requireClass(payload.getClassId());
        // Thiếu STK thụ hưởng thì không ghi gì (phiếu cần QR).
        bankAccountService.requireActive();

        LocalDate from = payload.getFromDate();
        LocalDate to = payload.getToDate();
        BillingPlan plan = plan(clazz, from, to);
        if (!plan.conflicts().isEmpty()) {
            throw conflict(toConflictDetails(plan));
        }

        String comment = blankToNull(payload.getTeacherComment());
        String wish = firstNonBlank(payload.getFooterWish(), DomainConstants.TUITION_SLIP_DEFAULT_WISH);
        LocalDate dueDate = resolveDueDate(to, LocalDate.now());
        String actor = SecurityUtils.currentUsername();
        String note = "HP " + from.format(NOTE_DATE) + "-" + to.format(NOTE_DATE);

        GenerateInvoicesResponseDto result = GenerateInvoicesResponseDto.builder().build();
        try {
            for (PlannedStudent s : plan.students()) {
                if (s.sessions().isEmpty()) {
                    if (!s.inactive()) {
                        result.setSkippedNoAttendance(result.getSkippedNoAttendance() + 1);
                    }
                    continue;
                }
                int count = s.sessions().size();
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
                        .note(note)
                        .feeMonth(null)
                        .feeYear(null)
                        .periodFrom(from)
                        .periodTo(to)
                        .isRange(FeePeriods.RANGE_FLAG)
                        .pricePerSession(price)
                        .totalSessions(count)
                        .teacherComment(comment)
                        .footerWish(wish)
                        .slipLabel(DomainConstants.TUITION_SLIP_LABEL_DEFAULT)
                        .isDeleted(PersistenceFlags.NOT_DELETED)
                        .createdBy(actor)
                        .build());
                List<TuitionFeeSessionEntity> rows = new ArrayList<>(count);
                for (BillableAttendanceRow row : s.sessions()) {
                    rows.add(TuitionFeeSessionEntity.builder()
                            .feeId(saved.getId())
                            .attendanceId(row.attendanceId())
                            .sessionDate(row.attendanceDate())
                            .createdBy(actor)
                            .build());
                }
                tuitionFeeSessionRepository.saveAllAndFlush(rows);
                result.getFeeIds().add(saved.getId());
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

        log.info("Sinh phiếu học phí lớp {} từ {} đến {}: created={}, skippedNoAttendance={}, actor={}",
                clazz.getClassCode(), from, to, result.getCreatedCount(), result.getSkippedNoAttendance(), actor);
        return result;
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

    static BusinessDataException conflict(List<FeeSessionConflictDto> details) {
        return new BusinessDataException(DomainConstants.FEE_SESSION_CONFLICT,
                DomainConstants.FEE_SESSION_CONFLICT_MESSAGE, new FeeSessionConflictDto.Payload(details));
    }

    private static List<FeeSessionConflictDto> toConflictDetails(BillingPlan plan) {
        List<FeeSessionConflictDto> details = new ArrayList<>();
        for (PlannedStudent s : plan.students()) {
            StudentConflict c = plan.conflicts().get(s.student().getId());
            if (c == null) {
                continue;
            }
            details.add(FeeSessionConflictDto.builder()
                    .studentId(s.student().getId())
                    .studentCode(s.student().getStudentCode())
                    .studentName(s.student().getFullName())
                    .dates(new ArrayList<>(c.dates()))
                    .feeCodes(new ArrayList<>(c.feeCodes()))
                    .build());
        }
        return details;
    }

    /**
     * Danh sách học sinh + buổi tính phí + xung đột của lớp trong khoảng.
     * <ul>
     *     <li>Học sinh đang học (ENROLLED, còn được liệt kê): luôn có mặt, kể cả 0 buổi.</li>
     *     <li>Học sinh đã nghỉ lớp: chỉ có mặt khi có ít nhất 1 buổi ({@code inactive = true}).</li>
     * </ul>
     * Xung đột tra bằng JOIN theo lớp + khoảng ngày (không IN-list - tránh ORA-01795).
     */
    private BillingPlan plan(ClassEntity clazz, LocalDate from, LocalDate to) {
        Map<Long, List<BillableAttendanceRow>> byStudent = new LinkedHashMap<>();
        for (BillableAttendanceRow row : attendanceRepository.findBillableInClassRange(
                clazz.getId(), from, to, DomainConstants.BILLABLE_ATTENDANCE_STATUSES)) {
            if (row.attendanceDate() == null || row.studentId() == null) {
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

        Map<Long, StudentConflict> conflicts = new HashMap<>();
        for (FeeSessionConflictRow row : tuitionFeeSessionRepository.findActiveConflictsInClassRange(
                clazz.getId(), from, to, DomainConstants.BILLABLE_ATTENDANCE_STATUSES)) {
            StudentConflict c = conflicts.computeIfAbsent(row.studentId(),
                    k -> new StudentConflict(new TreeSet<>(), new LinkedHashSet<>()));
            if (row.sessionDate() != null) {
                c.dates().add(row.sessionDate());
            }
            if (row.feeCode() != null) {
                c.feeCodes().add(row.feeCode());
            }
        }
        return new BillingPlan(planned, conflicts);
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
            return slip;
        }
        String description = transferContentOf(fee, slip.getStudentCode(), slip.getMonth());
        String payload = VietQrHelper.buildVietQrPayload(bankBin, accountNo, amountVnd, description);
        slip.setQrPayload(payload);
        slip.setQrBase64(VietQrHelper.generateQrBase64(payload, QR_SIZE, QR_SIZE));
        slip.setQuickPayUrl(VietQrHelper.buildQuickUrl(bankBin, accountNo, amountVnd, description, accountName));
        return slip;
    }

    /** Nội dung chuyển khoản: phiếu khoảng ngày {@code "<code> HP ddMM-ddMM"}, phiếu cũ {@code "<code> HP T<m>"}. */
    static String transferContentOf(TuitionFeeEntity fee, String studentCode, Integer legacyMonth) {
        if (FeePeriods.isRangeFee(fee.getIsRange())) {
            LocalDate to = fee.getPeriodTo() == null ? fee.getPeriodFrom() : fee.getPeriodTo();
            return FeePeriods.rangeTransferContent(studentCode, fee.getPeriodFrom(), to);
        }
        return FeePeriods.legacyTransferContent(studentCode, legacyMonth);
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

    private record StudentConflict(TreeSet<LocalDate> dates, LinkedHashSet<String> feeCodes) {
    }

    private record BillingPlan(List<PlannedStudent> students, Map<Long, StudentConflict> conflicts) {
    }
}
