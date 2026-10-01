package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.FeeStatusCalculator;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.VietQrHelper;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.dto.request.CreateMonthlyInvoiceRequestDto;
import com.education.base.dto.response.GenerateMonthlyInvoicesResponseDto;
import com.education.base.dto.response.TuitionSlipResponseDto;
import com.education.base.entity.AttendanceEntity;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.ClassStudentEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.service.BankAccountService;
import com.education.base.service.TuitionSlipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
@RequiredArgsConstructor
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenerateMonthlyInvoicesResponseDto generateMonthly(CreateMonthlyInvoiceRequestDto request) {
        CreateMonthlyInvoiceRequestDto payload = request == null ? new CreateMonthlyInvoiceRequestDto() : request;
        ClassEntity clazz = requireClass(payload.getClassId());
        int month = payload.getMonth();
        int year = payload.getYear();
        BigDecimal price = payload.getPricePerSession().setScale(2, RoundingMode.HALF_UP);
        String comment = blankToNull(payload.getTeacherComment());
        String wish = firstNonBlank(payload.getFooterWish(), DomainConstants.TUITION_SLIP_DEFAULT_WISH);

        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());

        Map<Long, List<LocalDate>> presentByStudent = presentDatesByStudent(clazz.getId(), from, to);

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<TuitionSlipResponseDto> slips = new ArrayList<>();

        List<ClassStudentEntity> enrollments = classStudentRepository.findByClassIdAndIsDeleted(
                clazz.getId(), PersistenceFlags.NOT_DELETED);
        for (ClassStudentEntity enrollment : enrollments) {
            if (!"ENROLLED".equals(enrollment.getStatus())) {
                continue;
            }
            StudentEntity student = listedStudent(enrollment.getStudentId());
            if (student == null) {
                continue;
            }
            List<LocalDate> presentDates = presentByStudent.getOrDefault(student.getId(), List.of());
            if (presentDates.isEmpty()) {
                skipped++;
                continue;
            }

            int sessions = presentDates.size();
            BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(sessions)).setScale(2, RoundingMode.HALF_UP);

            TuitionFeeEntity existing = tuitionFeeRepository
                    .findByStudentIdAndClassIdAndFeeYearAndFeeMonthAndIsDeleted(
                            student.getId(), clazz.getId(), year, month, PersistenceFlags.NOT_DELETED)
                    .orElse(null);

            TuitionFeeEntity saved;
            if (existing == null) {
                saved = tuitionFeeRepository.saveAndFlush(TuitionFeeEntity.builder()
                        .feeCode(tuitionFeeRepository.nextTuitionFeeCode())
                        .studentId(student.getId())
                        .classId(clazz.getId())
                        .totalAmount(totalAmount)
                        .discountAmount(BigDecimal.ZERO)
                        .paidAmount(BigDecimal.ZERO)
                        .dueDate(to)
                        .status(FeeStatusCalculator.resolveFeeStatus(totalAmount, BigDecimal.ZERO, BigDecimal.ZERO, to))
                        .note("HP T" + month + "/" + year)
                        .feeMonth(month)
                        .feeYear(year)
                        .pricePerSession(price)
                        .totalSessions(sessions)
                        .teacherComment(comment)
                        .footerWish(wish)
                        .slipLabel(DomainConstants.TUITION_SLIP_LABEL_DEFAULT)
                        .isDeleted(PersistenceFlags.NOT_DELETED)
                        .build());
                created++;
            } else if ("PAID".equals(existing.getStatus()) || "CANCELLED".equals(existing.getStatus())) {
                slips.add(enrichWithQr(toSlipDto(existing, clazz, student, presentDates)));
                continue;
            } else {
                existing.setPricePerSession(price);
                existing.setTotalSessions(sessions);
                existing.setTotalAmount(totalAmount);
                existing.setDueDate(to);
                existing.setTeacherComment(comment);
                existing.setFooterWish(wish);
                existing.setSlipLabel(DomainConstants.TUITION_SLIP_LABEL_DEFAULT);
                existing.setStatus(FeeStatusCalculator.resolveFeeStatus(
                        totalAmount, existing.getDiscountAmount(), existing.getPaidAmount(), to));
                saved = tuitionFeeRepository.saveAndFlush(existing);
                updated++;
                slips.add(enrichWithQr(toSlipDto(saved, clazz, student, presentDates)));
                continue;
            }
            slips.add(enrichWithQr(toSlipDto(saved, clazz, student, presentDates)));
        }

        log.info("Sinh phiếu học phí lớp {} kỳ {}/{}: created={}, updated={}, skippedNoAttendance={}",
                clazz.getClassCode(), month, year, created, updated, skipped);
        return GenerateMonthlyInvoicesResponseDto.builder()
                .createdCount(created)
                .updatedCount(updated)
                .skippedNoAttendance(skipped)
                .slips(slips)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TuitionSlipResponseDto getSlip(Long invoiceId) {
        return enrichWithQr(loadSlip(invoiceId));
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

    private TuitionSlipResponseDto loadSlip(Long invoiceId) {
        if (invoiceId == null) {
            throw new OracleBusinessException("FEE_ID_REQUIRED", "ID khoản học phí không được để trống.");
        }
        tuitionFeeRepository.findByIdAndIsDeleted(invoiceId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "FEE_NOT_FOUND", "Không tìm thấy khoản học phí với ID: " + invoiceId));
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

    private TuitionSlipResponseDto enrichWithQr(TuitionSlipResponseDto slip) {
        long amountVnd = nvl(slip.getTotalAmount()).setScale(0, RoundingMode.HALF_UP).longValue();
        String description = transferContent(slip.getStudentCode(), slip.getMonth());
        BankAccountResponseDto account = bankAccountService.requireActive();
        String bankBin = account.getBankBin();
        String accountNo = account.getAccountNo();
        String accountName = account.getAccountName();
        String payload = VietQrHelper.buildVietQrPayload(bankBin, accountNo, amountVnd, description);
        slip.setBankBin(bankBin);
        slip.setBankName(firstNonBlank(account.getBankName(), "Ngân hàng"));
        slip.setAccountNo(accountNo);
        slip.setAccountName(accountName);
        slip.setQrPayload(payload);
        slip.setQrBase64(VietQrHelper.generateQrBase64(payload, QR_SIZE, QR_SIZE));
        slip.setQuickPayUrl(VietQrHelper.buildQuickUrl(bankBin, accountNo, amountVnd, description, accountName));
        return slip;
    }

    private Map<Long, List<LocalDate>> presentDatesByStudent(Long classId, LocalDate from, LocalDate to) {
        Map<Long, List<LocalDate>> result = new LinkedHashMap<>();
        List<AttendanceEntity> rows = attendanceRepository
                .findByClassIdAndAttendanceDateBetweenAndIsDeleted(classId, from, to, PersistenceFlags.NOT_DELETED);
        for (AttendanceEntity row : rows) {
            if (!DomainConstants.ATTENDANCE_PRESENT.equals(row.getStatus()) || row.getAttendanceDate() == null) {
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
                                             List<LocalDate> presentDates) {
        List<String> badges = new ArrayList<>();
        for (LocalDate date : presentDates) {
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
}
