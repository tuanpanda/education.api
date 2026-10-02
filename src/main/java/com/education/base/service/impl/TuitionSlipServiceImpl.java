package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.FeeStatusCalculator;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.VietQrHelper;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.dto.request.CreateMonthlyInvoiceRequestDto;
import com.education.base.dto.response.GenerateMonthlyInvoicesResponseDto;
import com.education.base.dto.response.GenerateMonthlyInvoicesResponseDto.FeeConflict;
import com.education.base.dto.response.GenerateMonthlyInvoicesResponseDto.StudentError;
import com.education.base.dto.response.TuitionSlipResponseDto;
import com.education.base.entity.AttendanceEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.StudentDiscountEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.security.SecurityUtils;
import com.education.base.service.BankAccountService;
import com.education.base.service.StudentDiscountService;
import com.education.base.service.TuitionSlipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class TuitionSlipServiceImpl implements TuitionSlipService {

    private static final int QR_SIZE = 360;
    private static final int TRANSFER_CONTENT_MAX = 25;
    private static final DateTimeFormatter MONTH_YEAR_TEXT = DateTimeFormatter.ofPattern("'Tháng' MM/yyyy", Locale.US);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private final TuitionFeeRepository tuitionFeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final ClassStudentRepository classStudentRepository;
    private final ClassRepository classRepository;
    private final StudentRepository studentRepository;
    private final BankAccountService bankAccountService;
    private final StudentDiscountService studentDiscountService;

    /** Mỗi học sinh một transaction riêng ({@code REQUIRES_NEW}): lỗi một khoản phí không rollback cả lớp. */
    private final TransactionTemplate perStudentTransaction;

    public TuitionSlipServiceImpl(TuitionFeeRepository tuitionFeeRepository,
                                  AttendanceRepository attendanceRepository,
                                  ClassStudentRepository classStudentRepository,
                                  ClassRepository classRepository,
                                  StudentRepository studentRepository,
                                  BankAccountService bankAccountService,
                                  StudentDiscountService studentDiscountService,
                                  PlatformTransactionManager transactionManager) {
        this.tuitionFeeRepository = tuitionFeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.classStudentRepository = classStudentRepository;
        this.classRepository = classRepository;
        this.studentRepository = studentRepository;
        this.bankAccountService = bankAccountService;
        this.studentDiscountService = studentDiscountService;
        this.perStudentTransaction = new TransactionTemplate(transactionManager);
        this.perStudentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Sinh / cập nhật phiếu học phí tháng cho cả lớp theo điểm danh tính phí ({@code PRESENT} + {@code LATE}).
     * <p>
     * Không bọc cả lớp trong một transaction: mỗi học sinh được ghi trong transaction riêng
     * ({@link #perStudentTransaction}); khoản phí xung đột giữ nguyên và nằm trong {@code conflicts},
     * lỗi của từng học sinh nằm trong {@code errors}.
     */
    @Override
    public GenerateMonthlyInvoicesResponseDto generateMonthly(CreateMonthlyInvoiceRequestDto request) {
        CreateMonthlyInvoiceRequestDto payload = request == null ? new CreateMonthlyInvoiceRequestDto() : request;
        ClassEntity clazz = requireClass(payload.getClassId());
        int month = payload.getMonth();
        int year = payload.getYear();
        // Kiểm tra STK thụ hưởng TRƯỚC khi ghi: thiếu STK thì không ghi gì (trước đây rollback cả lô).
        BankAccountResponseDto account = bankAccountService.requireActive();

        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        BillingContext ctx = new BillingContext(
                clazz,
                month,
                year,
                payload.getPricePerSession().setScale(2, RoundingMode.HALF_UP),
                blankToNull(payload.getTeacherComment()),
                firstNonBlank(payload.getFooterWish(), DomainConstants.TUITION_SLIP_DEFAULT_WISH),
                resolveDueDate(to, LocalDate.now()),
                SecurityUtils.currentUsername());

        Map<Long, List<LocalDate>> billableByStudent = billableDatesByStudent(clazz.getId(), from, to);

        List<StudentEntity> students = new ArrayList<>();
        for (ClassStudentEntity enrollment : classStudentRepository.findByClassIdAndIsDeleted(
                clazz.getId(), PersistenceFlags.NOT_DELETED)) {
            if (!"ENROLLED".equals(enrollment.getStatus())) {
                continue;
            }
            StudentEntity student = listedStudent(enrollment.getStudentId());
            if (student != null) {
                students.add(student);
            }
        }
        Map<Long, List<StudentDiscountEntity>> discountsByStudent = students.isEmpty()
                ? Map.of()
                : studentDiscountService.findApplicable(
                        students.stream().map(StudentEntity::getId).toList(), clazz.getId(), from, to);

        GenerateMonthlyInvoicesResponseDto result = GenerateMonthlyInvoicesResponseDto.builder().build();
        for (StudentEntity student : students) {
            List<LocalDate> dates = billableByStudent.getOrDefault(student.getId(), List.of());
            List<StudentDiscountEntity> discounts = discountsByStudent.getOrDefault(student.getId(), List.of());
            StudentOutcome outcome;
            try {
                outcome = perStudentTransaction.execute(status -> billStudent(ctx, student, dates, discounts));
            } catch (RuntimeException e) {
                result.getErrors().add(toStudentError(student, e));
                log.warn("Sinh phiếu học phí lớp {} kỳ {}/{} lỗi với học sinh id={}: {}",
                        clazz.getClassCode(), month, year, student.getId(), e.getMessage());
                continue;
            }
            if (outcome == null) {
                continue;
            }
            switch (outcome.kind()) {
                case CREATED -> result.setCreatedCount(result.getCreatedCount() + 1);
                case UPDATED -> result.setUpdatedCount(result.getUpdatedCount() + 1);
                case CANCELLED -> result.setCancelledCount(result.getCancelledCount() + 1);
                case SKIPPED -> result.setSkippedNoAttendance(result.getSkippedNoAttendance() + 1);
                case CONFLICT -> result.getConflicts().add(outcome.conflict());
                case UNCHANGED -> {
                    // khoản phí bị hủy thủ công: giữ nguyên, vẫn trả phiếu (không có QR).
                }
            }
            if (outcome.fee() != null && outcome.kind() != OutcomeKind.CANCELLED
                    && outcome.kind() != OutcomeKind.CONFLICT) {
                result.getSlips().add(enrichWithQr(
                        toSlipDto(outcome.fee(), clazz, student, dates), outcome.fee(), account));
            }
        }

        log.info("Sinh phiếu học phí lớp {} kỳ {}/{}: created={}, updated={}, cancelled={}, skippedNoAttendance={},"
                        + " conflicts={}, errors={}",
                clazz.getClassCode(), month, year, result.getCreatedCount(), result.getUpdatedCount(),
                result.getCancelledCount(), result.getSkippedNoAttendance(), result.getConflicts().size(),
                result.getErrors().size());
        return result;
    }

    /** Ghi khoản phí tháng của MỘT học sinh; chạy trong transaction riêng. */
    private StudentOutcome billStudent(BillingContext ctx, StudentEntity student, List<LocalDate> dates,
                                       List<StudentDiscountEntity> discounts) {
        Long existingId = tuitionFeeRepository.findMonthlyFeeId(
                student.getId(), ctx.clazz().getId(), ctx.year(), ctx.month()).orElse(null);

        if (dates.isEmpty()) {
            return existingId == null ? StudentOutcome.of(OutcomeKind.SKIPPED, null)
                    : handleNoBillableSessions(ctx, student, lockFee(existingId));
        }

        int sessions = dates.size();
        BigDecimal totalAmount = ctx.price().multiply(BigDecimal.valueOf(sessions)).setScale(2, RoundingMode.HALF_UP);

        if (existingId == null) {
            BigDecimal discount = studentDiscountService.computeDiscount(totalAmount, discounts);
            TuitionFeeEntity saved = tuitionFeeRepository.saveAndFlush(TuitionFeeEntity.builder()
                    .feeCode(tuitionFeeRepository.nextTuitionFeeCode())
                    .studentId(student.getId())
                    .classId(ctx.clazz().getId())
                    .totalAmount(totalAmount)
                    .discountAmount(discount)
                    .paidAmount(BigDecimal.ZERO)
                    .dueDate(ctx.dueDate())
                    .status(FeeStatusCalculator.resolveFeeStatus(totalAmount, discount, BigDecimal.ZERO, ctx.dueDate()))
                    .note(defaultNote(ctx))
                    .feeMonth(ctx.month())
                    .feeYear(ctx.year())
                    .pricePerSession(ctx.price())
                    .totalSessions(sessions)
                    .teacherComment(ctx.comment())
                    .footerWish(ctx.wish())
                    .slipLabel(DomainConstants.TUITION_SLIP_LABEL_DEFAULT)
                    .isDeleted(PersistenceFlags.NOT_DELETED)
                    .createdBy(ctx.actor())
                    .build());
            return StudentOutcome.of(OutcomeKind.CREATED, saved);
        }

        // B2: nạp lại dưới khóa dòng; PAID_AMOUNT chỉ được đọc, không bao giờ bị ghi đè ở đây.
        TuitionFeeEntity fee = lockFee(existingId);
        boolean revive = false;
        if (DomainConstants.FEE_STATUS_CANCELLED.equals(fee.getStatus())) {
            if (!isAutoCancelled(fee) || nvl(fee.getPaidAmount()).signum() > 0) {
                return StudentOutcome.of(OutcomeKind.UNCHANGED, fee);
            }
            // Khoản phí do hệ thống tự hủy (B4) nay lại có buổi tính phí: mở lại.
            revive = true;
        }

        BigDecimal paid = nvl(fee.getPaidAmount());
        BigDecimal discount = discounts.isEmpty()
                ? nvl(fee.getDiscountAmount())
                : studentDiscountService.computeDiscount(totalAmount, discounts);
        // B3: tiền giảm không bao giờ vượt tổng tiền (CK_FEES_DISCOUNT).
        discount = discount.min(totalAmount).max(BigDecimal.ZERO);
        if (totalAmount.subtract(discount).compareTo(paid) < 0) {
            // B3: tổng mới (sau giảm) nhỏ hơn số đã thu -> không đánh dấu PAID kèm tiền thừa; giữ nguyên khoản phí.
            return StudentOutcome.conflict(FeeConflict.builder()
                    .feeId(fee.getId())
                    .feeCode(fee.getFeeCode())
                    .studentId(student.getId())
                    .studentCode(student.getStudentCode())
                    .studentName(student.getFullName())
                    .reason(GenerateMonthlyInvoicesResponseDto.CONFLICT_NET_BELOW_PAID)
                    .message("Tổng học phí mới sau giảm (" + totalAmount.subtract(discount).toPlainString()
                            + ") nhỏ hơn số đã thu (" + paid.toPlainString()
                            + "). Khoản phí được giữ nguyên; cần hoàn tiền hoặc điều chỉnh thủ công.")
                    .billableSessions(sessions)
                    .newTotalAmount(totalAmount)
                    .newDiscountAmount(discount)
                    .paidAmount(paid)
                    .build());
        }

        fee.setPricePerSession(ctx.price());
        fee.setTotalSessions(sessions);
        fee.setTotalAmount(totalAmount);
        fee.setDiscountAmount(discount);
        fee.setTeacherComment(ctx.comment());
        fee.setFooterWish(ctx.wish());
        fee.setSlipLabel(DomainConstants.TUITION_SLIP_LABEL_DEFAULT);
        if (revive) {
            fee.setCancelReason(null);
            fee.setNote(defaultNote(ctx));
            fee.setDueDate(ctx.dueDate());
        } else if (fee.getDueDate() == null) {
            fee.setDueDate(ctx.dueDate());
        }
        fee.setStatus(FeeStatusCalculator.resolveFeeStatus(totalAmount, discount, paid, fee.getDueDate()));
        fee.setUpdatedBy(ctx.actor());
        return StudentOutcome.of(OutcomeKind.UPDATED, tuitionFeeRepository.saveAndFlush(fee));
    }

    /** B4: học sinh không còn buổi tính phí nhưng đã có khoản phí của tháng. */
    private StudentOutcome handleNoBillableSessions(BillingContext ctx, StudentEntity student, TuitionFeeEntity fee) {
        if (DomainConstants.FEE_STATUS_CANCELLED.equals(fee.getStatus())) {
            return StudentOutcome.of(OutcomeKind.SKIPPED, null);
        }
        BigDecimal paid = nvl(fee.getPaidAmount());
        if (paid.signum() > 0) {
            return StudentOutcome.conflict(FeeConflict.builder()
                    .feeId(fee.getId())
                    .feeCode(fee.getFeeCode())
                    .studentId(student.getId())
                    .studentCode(student.getStudentCode())
                    .studentName(student.getFullName())
                    .reason(GenerateMonthlyInvoicesResponseDto.CONFLICT_NO_SESSIONS_HAS_PAYMENTS)
                    .message("Không còn buổi tính phí trong tháng nhưng khoản phí đã thu " + paid.toPlainString()
                            + ". Khoản phí được giữ nguyên; cần hoàn tiền trước khi hủy.")
                    .billableSessions(0)
                    .newTotalAmount(BigDecimal.ZERO.setScale(2))
                    .newDiscountAmount(BigDecimal.ZERO.setScale(2))
                    .paidAmount(paid)
                    .build());
        }
        fee.setStatus(DomainConstants.FEE_STATUS_CANCELLED);
        fee.setCancelReason(DomainConstants.FEE_AUTO_CANCEL_NO_SESSION_NOTE);
        fee.setNote(DomainConstants.FEE_AUTO_CANCEL_NO_SESSION_NOTE);
        fee.setUpdatedBy(ctx.actor());
        return StudentOutcome.of(OutcomeKind.CANCELLED, tuitionFeeRepository.saveAndFlush(fee));
    }

    @Override
    @Transactional(readOnly = true)
    public TuitionSlipResponseDto getSlip(Long invoiceId) {
        TuitionFeeEntity fee = requireFee(invoiceId);
        return enrichWithQr(loadSlip(invoiceId), fee, bankAccountService.requireActive());
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
     * B5: hạn thu mặc định là ngày cuối tháng; nếu ngày đó đã trước {@code today} (sinh bù tháng cũ) thì
     * hạn thu = {@code today + FEE_BACKBILL_GRACE_DAYS} để phiếu không bị {@code OVERDUE} ngay.
     */
    static LocalDate resolveDueDate(LocalDate endOfMonth, LocalDate today) {
        return endOfMonth.isBefore(today) ? today.plusDays(DomainConstants.FEE_BACKBILL_GRACE_DAYS) : endOfMonth;
    }

    private TuitionFeeEntity requireFee(Long invoiceId) {
        if (invoiceId == null) {
            throw new OracleBusinessException("FEE_ID_REQUIRED", "ID khoản học phí không được để trống.");
        }
        return tuitionFeeRepository.findByIdAndIsDeleted(invoiceId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "FEE_NOT_FOUND", "Không tìm thấy khoản học phí với ID: " + invoiceId));
    }

    private TuitionFeeEntity lockFee(Long id) {
        try {
            return tuitionFeeRepository.findByIdAndIsDeletedForUpdate(id, PersistenceFlags.NOT_DELETED)
                    .orElseThrow(() -> new OracleBusinessException(
                            "FEE_NOT_FOUND", "Không tìm thấy khoản học phí với ID: " + id));
        } catch (PessimisticLockingFailureException e) {
            throw new OracleBusinessException("FEE_LOCKED",
                    "Khoản học phí đang được xử lý bởi một giao dịch khác. Vui lòng thử lại sau ít phút.", e);
        }
    }

    private TuitionSlipResponseDto loadSlip(Long invoiceId) {
        TuitionSlipResponseDto slip = tuitionFeeRepository.getTuitionSlipData(invoiceId);
        slip.setMonthYearText(formatMonthYear(slip.getMonth(), slip.getYear()));
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
        String description = transferContent(slip.getStudentCode(), slip.getMonth());
        String payload = VietQrHelper.buildVietQrPayload(bankBin, accountNo, amountVnd, description);
        slip.setQrPayload(payload);
        slip.setQrBase64(VietQrHelper.generateQrBase64(payload, QR_SIZE, QR_SIZE));
        slip.setQuickPayUrl(VietQrHelper.buildQuickUrl(bankBin, accountNo, amountVnd, description, accountName));
        return slip;
    }

    /** Có được sinh VietQR cho khoản phí: còn phải thu {@code > 0} và không {@code PAID} / {@code CANCELLED}. */
    static boolean isQrPayable(String status, long remainingVnd) {
        return remainingVnd > 0
                && !DomainConstants.FEE_STATUS_PAID.equals(status)
                && !DomainConstants.FEE_STATUS_CANCELLED.equals(status);
    }

    /** B1: ngày đi học tính phí ({@link DomainConstants#BILLABLE_ATTENDANCE_STATUSES}) theo học sinh. */
    private Map<Long, List<LocalDate>> billableDatesByStudent(Long classId, LocalDate from, LocalDate to) {
        Map<Long, List<LocalDate>> result = new LinkedHashMap<>();
        List<AttendanceEntity> rows = attendanceRepository
                .findByClassIdAndAttendanceDateBetweenAndIsDeleted(classId, from, to, PersistenceFlags.NOT_DELETED);
        for (AttendanceEntity row : rows) {
            if (!DomainConstants.BILLABLE_ATTENDANCE_STATUSES.contains(row.getStatus())
                    || row.getAttendanceDate() == null) {
                continue;
            }
            result.computeIfAbsent(row.getStudentId(), key -> new ArrayList<>()).add(row.getAttendanceDate());
        }
        for (List<LocalDate> dates : result.values()) {
            dates.sort(Comparator.naturalOrder());
        }
        return result;
    }

    private TuitionSlipResponseDto toSlipDto(TuitionFeeEntity fee, ClassEntity clazz, StudentEntity student,
                                             List<LocalDate> billableDates) {
        List<String> badges = new ArrayList<>();
        for (LocalDate date : billableDates) {
            badges.add(date.format(DAY_MONTH));
        }
        return TuitionSlipResponseDto.builder()
                .invoiceId(fee.getId())
                .invoiceCode(fee.getFeeCode())
                .month(fee.getFeeMonth())
                .year(fee.getFeeYear())
                .monthYearText(formatMonthYear(fee.getFeeMonth(), fee.getFeeYear()))
                .className(clazz.getClassName())
                .classCode(clazz.getClassCode())
                .studentCode(student.getStudentCode())
                .studentName(student.getFullName())
                .slipLabel(firstNonBlank(fee.getSlipLabel(), DomainConstants.TUITION_SLIP_LABEL_DEFAULT))
                .pricePerSession(fee.getPricePerSession())
                .totalSessions(fee.getTotalSessions())
                .totalAmount(fee.getTotalAmount())
                .attendedDates(badges)
                .teacherComment(fee.getTeacherComment())
                .footerWish(firstNonBlank(fee.getFooterWish(), DomainConstants.TUITION_SLIP_DEFAULT_WISH))
                .build();
    }

    static String transferContent(String studentCode, Integer month) {
        String code = asciiFold(studentCode == null ? "" : studentCode)
                .replaceAll("[^A-Za-z0-9]", "");
        int safeMonth = month == null ? 0 : month;
        String text = (code + " HP T" + safeMonth).trim();
        return text.length() > TRANSFER_CONTENT_MAX ? text.substring(0, TRANSFER_CONTENT_MAX) : text;
    }

    static String formatMonthYear(Integer month, Integer year) {
        if (month == null || year == null) {
            return "";
        }
        return LocalDate.of(year, month, 1).format(MONTH_YEAR_TEXT);
    }

    static String asciiFold(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    private static boolean isAutoCancelled(TuitionFeeEntity fee) {
        String reason = fee.getCancelReason();
        return reason != null && reason.startsWith(DomainConstants.FEE_AUTO_CANCEL_PREFIX);
    }

    private static String defaultNote(BillingContext ctx) {
        return "HP T" + ctx.month() + "/" + ctx.year();
    }

    private static StudentError toStudentError(StudentEntity student, RuntimeException e) {
        String code = e instanceof OracleBusinessException business ? business.getErrorCode() : "FEE_GENERATE_FAILED";
        String message = e instanceof OracleBusinessException
                ? e.getMessage()
                : "Không ghi được phiếu học phí cho học sinh này; các học sinh khác vẫn được xử lý.";
        return StudentError.builder()
                .studentId(student.getId())
                .studentCode(student.getStudentCode())
                .studentName(student.getFullName())
                .code(code)
                .message(message)
                .build();
    }

    private ClassEntity requireClass(Long classId) {
        if (classId == null) {
            throw new OracleBusinessException("CLASS_ID_REQUIRED", "ID lớp học không được để trống.");
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

    private enum OutcomeKind { CREATED, UPDATED, CANCELLED, SKIPPED, CONFLICT, UNCHANGED }

    private record StudentOutcome(OutcomeKind kind, TuitionFeeEntity fee, FeeConflict conflict) {
        static StudentOutcome of(OutcomeKind kind, TuitionFeeEntity fee) {
            return new StudentOutcome(kind, fee, null);
        }

        static StudentOutcome conflict(FeeConflict conflict) {
            return new StudentOutcome(OutcomeKind.CONFLICT, null, conflict);
        }
    }

    private record BillingContext(ClassEntity clazz, int month, int year, BigDecimal price, String comment,
                                  String wish, LocalDate dueDate, String actor) {
    }
}
