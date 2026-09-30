package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.VietQrHelper;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.dto.request.ConfirmPaymentRequest;
import com.education.base.dto.request.TuitionFeeCreateRequest;
import com.education.base.dto.request.TuitionFeeFilterRequest;
import com.education.base.dto.request.TuitionQrRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.dto.response.TuitionFeeDetailResponse;
import com.education.base.dto.response.TuitionFeeReportDto;
import com.education.base.dto.response.TuitionQrResponseDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.PaymentTransactionEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapper;
import com.education.base.mapper.FinanceAcademicMapper;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.PaymentTransactionRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.repository.spec.TuitionFeeSpecifications;
import com.education.base.service.BankAccountService;
import com.education.base.service.FileStorageService;
import com.education.base.service.TuitionFeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TuitionFeeServiceImpl implements TuitionFeeService {

    private static final int QR_SIZE = 512;

    /** Unique index trên {@code FIN_PAYMENT_TRANSACTIONS(BANK_REFERENCE_NO)} (migration V13_3). */
    static final String UQ_BANK_REFERENCE_INDEX = "UQ_FIN_TRANS_BANK_REF";

    /** Unique constraint trên {@code FIN_PAYMENT_TRANSACTIONS(TRANSACTION_CODE)} (V1). */
    static final String UQ_TRANSACTION_CODE = "UQ_FIN_TRANS_CODE";

    private final TuitionFeeRepository tuitionFeeRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final FileStorageService fileStorageService;
    private final FileMapper fileMapper;
    private final FinanceAcademicMapper financeAcademicMapper;
    private final BankAccountService bankAccountService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TuitionFeeReportDto> search(TuitionFeeFilterRequest filter) {
        TuitionFeeFilterRequest criteria = filter == null ? new TuitionFeeFilterRequest() : filter;
        Page<TuitionFeeEntity> page = tuitionFeeRepository.findAll(
                TuitionFeeSpecifications.fromFilter(criteria),
                PageRequest.of(criteria.resolvePageNo() - 1, criteria.resolvePageSize(),
                        Sort.by(Sort.Direction.DESC, "id")));
        List<TuitionFeeEntity> fees = page.getContent();
        Map<Long, StudentEntity> students = loadStudents(fees);
        Map<Long, ClassEntity> classes = loadClasses(fees);
        List<TuitionFeeReportDto> content = new ArrayList<>(fees.size());
        for (TuitionFeeEntity entity : fees) {
            content.add(toReport(entity, students, classes));
        }
        return PageResponse.of(content, criteria.resolvePageNo(), criteria.resolvePageSize(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public TuitionFeeDetailResponse getDetail(Long id) {
        requireActiveFee(id);
        TuitionFeeDetailResponse detail = tuitionFeeRepository.getFeeDetail(id);
        detail.setAttachments(fileMapper.toDtoList(
                fileStorageService.getFilesByRef(DomainConstants.Module.TUITION, id)));
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TuitionFeeDetailResponse create(TuitionFeeCreateRequest request) {
        String feeCode = request.getFeeCode().trim();
        if (tuitionFeeRepository.existsByFeeCode(feeCode)) {
            throw new OracleBusinessException("FEE_CODE_DUPLICATED",
                    "Mã khoản học phí '" + feeCode + "' đã tồn tại.");
        }
        requireStudent(request.getStudentId());
        if (request.getClassId() != null) {
            requireClass(request.getClassId());
        }
        BigDecimal discount = nvl(request.getDiscountAmount());
        if (discount.compareTo(request.getTotalAmount()) > 0) {
            throw new OracleBusinessException("INVALID_DISCOUNT",
                    "Số tiền giảm không được lớn hơn tổng số tiền.");
        }

        TuitionFeeEntity entity = financeAcademicMapper.toFeeEntity(request);
        entity.setFeeCode(feeCode);
        entity.setDiscountAmount(discount);
        entity.setPaidAmount(BigDecimal.ZERO);
        entity.setIsDeleted(PersistenceFlags.NOT_DELETED);
        entity.setStatus(resolveFeeStatus(entity.getTotalAmount(), discount, BigDecimal.ZERO, entity.getDueDate()));
        TuitionFeeEntity saved = tuitionFeeRepository.saveAndFlush(entity);
        log.info("Đã tạo khoản học phí id={}, feeCode={}", saved.getId(), saved.getFeeCode());
        return getDetail(saved.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public TuitionQrResponseDto createQr(Long id, TuitionQrRequest request) {
        TuitionFeeEntity fee = requirePayableFee(id);
        BigDecimal remaining = remainingOf(fee);
        long amountVnd = remaining.setScale(0, RoundingMode.HALF_UP).longValue();
        if (amountVnd <= 0) {
            throw new OracleBusinessException("FEE_ALREADY_PAID",
                    "Khoản học phí " + fee.getFeeCode() + " không còn số tiền phải thu.");
        }

        TuitionQrRequest params = request == null ? new TuitionQrRequest() : request;
        BankAccountResponseDto account = bankAccountService.requireActive();
        String bankBin = account.getBankBin();
        String accountNo = account.getAccountNo();
        String accountName = account.getAccountName();
        StudentEntity student = studentRepository.findById(fee.getStudentId()).orElse(null);
        String description = firstNonBlank(params.getDescription(), defaultQrDescription(fee, student));

        String payload = VietQrHelper.buildVietQrPayload(bankBin, accountNo, amountVnd, description);
        String quickUrl = VietQrHelper.buildQuickUrl(bankBin, accountNo, amountVnd, description, accountName);
        String image = VietQrHelper.generateQrBase64(payload, QR_SIZE, QR_SIZE);

        return TuitionQrResponseDto.builder()
                .tuitionFeeId(fee.getId())
                .feeCode(fee.getFeeCode())
                .studentCode(student == null ? null : student.getStudentCode())
                .studentName(student == null ? null : student.getFullName())
                .remainingAmount(remaining)
                .quickUrl(quickUrl)
                .qrPayload(payload)
                .base64Image(image)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentTransactionDto confirmPayment(Long id, ConfirmPaymentRequest request) {
        ConfirmPaymentRequest payload = request == null ? new ConfirmPaymentRequest() : request;
        TuitionFeeEntity fee = requirePayable(lockActiveFee(id));
        BigDecimal remaining = remainingOf(fee);
        BigDecimal amount = payload.getAmount() == null ? remaining : payload.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new OracleBusinessException("INVALID_PAYMENT_AMOUNT", "Số tiền thanh toán phải lớn hơn 0.");
        }
        if (amount.compareTo(remaining) > 0) {
            throw new OracleBusinessException("PAYMENT_EXCEEDS_REMAINING",
                    "Số tiền thanh toán vượt quá số còn phải thu (" + remaining + ").");
        }

        String transactionCode = payload.getTransactionCode();
        if (transactionCode == null || transactionCode.isBlank()) {
            transactionCode = "PAY" + fee.getId() + System.currentTimeMillis();
        } else {
            transactionCode = transactionCode.trim();
            if (paymentTransactionRepository.existsByTransactionCode(transactionCode)) {
                throw new OracleBusinessException("TRANSACTION_CODE_DUPLICATED",
                        "Mã giao dịch '" + transactionCode + "' đã tồn tại.");
            }
        }

        String bankRef = blankToNull(payload.getBankReferenceNo());
        if (bankRef != null && paymentTransactionRepository.existsByBankReferenceNo(bankRef)) {
            throw bankReferenceDuplicated(bankRef);
        }

        String method = firstNonBlank(payload.getPaymentMethod(), "VIETQR");
        BankAccountResponseDto account = bankAccountService.requireActive();
        PaymentTransactionEntity transaction;
        try {
            // saveAndFlush: vi phạm unique index (request đồng thời trên khoản phí khác) lộ ra ngay tại đây.
            transaction = paymentTransactionRepository.saveAndFlush(PaymentTransactionEntity.builder()
                    .transactionCode(transactionCode)
                    .tuitionFeeId(fee.getId())
                    .amount(amount)
                    .paymentMethod(method.toUpperCase(Locale.ROOT))
                    .bankBin(account.getBankBin())
                    .accountNo(account.getAccountNo())
                    .bankReferenceNo(bankRef)
                    .status("SUCCESS")
                    .note(payload.getNote())
                    .isDeleted(PersistenceFlags.NOT_DELETED)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw translatePaymentConstraint(e, bankRef, transactionCode);
        }

        BigDecimal newPaid = nvl(fee.getPaidAmount()).add(amount);
        fee.setPaidAmount(newPaid);
        fee.setStatus(resolveFeeStatus(fee.getTotalAmount(), fee.getDiscountAmount(), newPaid, fee.getDueDate()));
        tuitionFeeRepository.save(fee);

        log.info("Đã xác nhận thanh toán {} cho khoản học phí id={}, status={}",
                amount, fee.getId(), fee.getStatus());
        return financeAcademicMapper.toPaymentDto(transaction);
    }

    private TuitionFeeEntity requireActiveFee(Long id) {
        if (id == null) {
            throw new OracleBusinessException("FEE_ID_REQUIRED", "ID khoản học phí không được để trống.");
        }
        return tuitionFeeRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "FEE_NOT_FOUND", "Không tìm thấy khoản học phí với ID: " + id));
    }

    /** Nạp và khóa dòng khoản học phí ({@code PESSIMISTIC_WRITE}) trong transaction hiện tại. */
    private TuitionFeeEntity lockActiveFee(Long id) {
        if (id == null) {
            throw new OracleBusinessException("FEE_ID_REQUIRED", "ID khoản học phí không được để trống.");
        }
        try {
            return tuitionFeeRepository.findByIdAndIsDeletedForUpdate(id, PersistenceFlags.NOT_DELETED)
                    .orElseThrow(() -> new OracleBusinessException(
                            "FEE_NOT_FOUND", "Không tìm thấy khoản học phí với ID: " + id));
        } catch (PessimisticLockingFailureException e) {
            log.warn("Không khóa được khoản học phí id={} để ghi nhận thanh toán: {}", id, e.getMessage());
            throw new OracleBusinessException("FEE_LOCKED",
                    "Khoản học phí đang được xử lý bởi một giao dịch khác. Vui lòng thử lại sau ít phút.", e);
        }
    }

    private static OracleBusinessException bankReferenceDuplicated(String bankRef) {
        return new OracleBusinessException("BANK_REFERENCE_DUPLICATED",
                "Mã tham chiếu ngân hàng '" + bankRef + "' đã được ghi nhận cho một giao dịch khác. "
                        + "Vui lòng kiểm tra lại, mỗi giao dịch ngân hàng chỉ được xác nhận một lần.");
    }

    private static RuntimeException translatePaymentConstraint(DataIntegrityViolationException e,
                                                               String bankRef, String transactionCode) {
        String detail = String.valueOf(e.getMostSpecificCause().getMessage()).toUpperCase(Locale.ROOT);
        if (bankRef != null && detail.contains(UQ_BANK_REFERENCE_INDEX)) {
            return bankReferenceDuplicated(bankRef);
        }
        if (detail.contains(UQ_TRANSACTION_CODE)) {
            return new OracleBusinessException("TRANSACTION_CODE_DUPLICATED",
                    "Mã giao dịch '" + transactionCode + "' đã tồn tại.", e);
        }
        return e;
    }

    private TuitionFeeEntity requirePayableFee(Long id) {
        return requirePayable(requireActiveFee(id));
    }

    private TuitionFeeEntity requirePayable(TuitionFeeEntity fee) {
        if ("CANCELLED".equals(fee.getStatus())) {
            throw new OracleBusinessException("FEE_CANCELLED",
                    "Khoản học phí " + fee.getFeeCode() + " đã bị hủy.");
        }
        if ("PAID".equals(fee.getStatus()) || remainingOf(fee).compareTo(BigDecimal.ZERO) <= 0) {
            throw new OracleBusinessException("FEE_ALREADY_PAID",
                    "Khoản học phí " + fee.getFeeCode() + " đã thu đủ.");
        }
        return fee;
    }

    private StudentEntity requireStudent(Long studentId) {
        return studentRepository.findByIdAndIsDeleted(studentId, PersistenceFlags.NOT_DELETED)
                .filter(s -> DomainConstants.STUDENT_STATUS_ACTIVE.equals(s.getStatus()))
                .orElseThrow(() -> new OracleBusinessException(
                        "STUDENT_NOT_FOUND", "Không tìm thấy học sinh với ID: " + studentId));
    }

    private ClassEntity requireClass(Long classId) {
        return classRepository.findByIdAndIsDeleted(classId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "CLASS_NOT_FOUND", "Không tìm thấy lớp học với ID: " + classId));
    }

    /** Nạp một lần mọi học sinh của trang kết quả (tránh N+1). */
    private Map<Long, StudentEntity> loadStudents(Collection<TuitionFeeEntity> fees) {
        List<Long> ids = fees.stream().map(TuitionFeeEntity::getStudentId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return studentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(StudentEntity::getId, Function.identity(), (a, b) -> a));
    }

    /** Nạp một lần mọi lớp học của trang kết quả (tránh N+1). */
    private Map<Long, ClassEntity> loadClasses(Collection<TuitionFeeEntity> fees) {
        List<Long> ids = fees.stream().map(TuitionFeeEntity::getClassId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return classRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ClassEntity::getId, Function.identity(), (a, b) -> a));
    }

    private TuitionFeeReportDto toReport(TuitionFeeEntity entity, Map<Long, StudentEntity> students,
                                         Map<Long, ClassEntity> classes) {
        TuitionFeeReportDto dto = financeAcademicMapper.toFeeReport(entity);
        dto.setRemainingAmount(remainingOf(entity));
        StudentEntity student = entity.getStudentId() == null ? null : students.get(entity.getStudentId());
        if (student != null) {
            dto.setStudentCode(student.getStudentCode());
            dto.setStudentName(student.getFullName());
        }
        ClassEntity clazz = entity.getClassId() == null ? null : classes.get(entity.getClassId());
        if (clazz != null) {
            dto.setClassCode(clazz.getClassCode());
            dto.setClassName(clazz.getClassName());
        }
        return dto;
    }

    static BigDecimal remainingOf(TuitionFeeEntity fee) {
        BigDecimal remaining = nvl(fee.getTotalAmount())
                .subtract(nvl(fee.getDiscountAmount()))
                .subtract(nvl(fee.getPaidAmount()));
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    static String resolveFeeStatus(BigDecimal total, BigDecimal discount, BigDecimal paid, LocalDate dueDate) {
        BigDecimal remaining = nvl(total).subtract(nvl(discount)).subtract(nvl(paid));
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return "PAID";
        }
        boolean overdue = dueDate != null && dueDate.isBefore(LocalDate.now());
        if (nvl(paid).compareTo(BigDecimal.ZERO) > 0) {
            return overdue ? "OVERDUE" : "PARTIAL";
        }
        return overdue ? "OVERDUE" : "UNPAID";
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback;
    }

    private static String defaultQrDescription(TuitionFeeEntity fee, StudentEntity student) {
        String studentCode = student == null ? "" : student.getStudentCode();
        String description = ("HP " + fee.getFeeCode() + " " + studentCode).trim();
        return description.length() > 99 ? description.substring(0, 99) : description;
    }
}
