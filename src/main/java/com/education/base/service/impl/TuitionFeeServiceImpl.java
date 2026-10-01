package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.FeeStatusCalculator;
import com.education.base.common.PersistenceFlags;
import com.education.base.common.VietQrHelper;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.dto.request.ConfirmPaymentRequest;
import com.education.base.dto.request.TuitionFeeCancelRequest;
import com.education.base.dto.request.TuitionFeeCreateRequest;
import com.education.base.dto.request.TuitionFeeFilterRequest;
import com.education.base.dto.request.TuitionFeeUpdateRequest;
import com.education.base.dto.request.TuitionQrRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.dto.response.TuitionFeeDetailResponse;
import com.education.base.dto.response.TuitionFeeListItemDto;
import com.education.base.dto.response.TuitionQrResponseDto;
import com.education.base.entity.ClassEntity;
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
import com.education.base.security.SecurityUtils;
import com.education.base.service.BankAccountService;
import com.education.base.service.FileStorageService;
import com.education.base.service.PaymentService;
import com.education.base.service.TuitionFeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TuitionFeeServiceImpl implements TuitionFeeService {

    private static final int QR_SIZE = 512;

    private final TuitionFeeRepository tuitionFeeRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final FileStorageService fileStorageService;
    private final FileMapper fileMapper;
    private final FinanceAcademicMapper financeAcademicMapper;
    private final BankAccountService bankAccountService;
    private final PaymentService paymentService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TuitionFeeListItemDto> search(TuitionFeeFilterRequest filter) {
        TuitionFeeFilterRequest criteria = filter == null ? new TuitionFeeFilterRequest() : filter;
        Page<TuitionFeeEntity> page = tuitionFeeRepository.findAll(
                TuitionFeeSpecifications.fromFilter(criteria),
                PageRequest.of(criteria.resolvePageNo() - 1, criteria.resolvePageSize(),
                        Sort.by(Sort.Direction.DESC, "id")));
        List<TuitionFeeEntity> fees = page.getContent();
        Map<Long, StudentEntity> students = loadStudents(fees);
        Map<Long, ClassEntity> classes = loadClasses(fees);
        List<TuitionFeeListItemDto> content = new ArrayList<>(fees.size());
        for (TuitionFeeEntity entity : fees) {
            content.add(toReport(entity, students, classes));
        }
        return PageResponse.of(content, criteria.resolvePageNo(), criteria.resolvePageSize(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public TuitionFeeDetailResponse getDetail(Long id) {
        TuitionFeeEntity fee = requireActiveFee(id);
        TuitionFeeDetailResponse detail = tuitionFeeRepository.getFeeDetail(id);
        detail.setCancelReason(fee.getCancelReason());
        if (fee.getStudentId() != null) {
            studentRepository.findById(fee.getStudentId())
                    .ifPresent(student -> detail.setStudentStatus(student.getStatus()));
        }
        detail.setAttachments(fileMapper.toDtoList(
                fileStorageService.getFilesByRef(DomainConstants.Module.TUITION, id)));
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TuitionFeeDetailResponse create(TuitionFeeCreateRequest request) {
        String feeCode = blankToNull(request.getFeeCode());
        if (feeCode != null && tuitionFeeRepository.existsByFeeCode(feeCode)) {
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
        // Mã bỏ trống: sinh theo quy luật TUITION (cùng hàm FN_NEXT_BIZ_CODE mà trigger V10 dùng).
        entity.setFeeCode(feeCode != null ? feeCode : tuitionFeeRepository.nextTuitionFeeCode());
        entity.setNote(blankToNull(request.getNote()));
        entity.setCreatedBy(SecurityUtils.currentUsername());
        entity.setDiscountAmount(discount);
        entity.setPaidAmount(BigDecimal.ZERO);
        entity.setIsDeleted(PersistenceFlags.NOT_DELETED);
        entity.setStatus(FeeStatusCalculator.resolveFeeStatus(entity.getTotalAmount(), discount, BigDecimal.ZERO, entity.getDueDate()));
        TuitionFeeEntity saved = tuitionFeeRepository.saveAndFlush(entity);
        log.info("Đã tạo khoản học phí id={}, feeCode={}", saved.getId(), saved.getFeeCode());
        return getDetail(saved.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TuitionFeeDetailResponse update(Long id, TuitionFeeUpdateRequest request) {
        if (request == null || request.getTotalAmount() == null) {
            throw new OracleBusinessException("FEE_TOTAL_REQUIRED", "Tổng số tiền không được để trống.");
        }
        TuitionFeeEntity fee = lockActiveFee(id);
        if (DomainConstants.FEE_STATUS_CANCELLED.equals(fee.getStatus())) {
            throw new OracleBusinessException("FEE_CANCELLED",
                    "Khoản học phí " + fee.getFeeCode() + " đã bị hủy, không sửa được.");
        }
        BigDecimal total = request.getTotalAmount();
        BigDecimal discount = nvl(request.getDiscountAmount());
        if (discount.compareTo(total) > 0) {
            throw new OracleBusinessException("INVALID_DISCOUNT",
                    "Số tiền giảm không được lớn hơn tổng số tiền.");
        }
        BigDecimal paid = nvl(fee.getPaidAmount());
        if (total.subtract(discount).compareTo(paid) < 0) {
            throw new OracleBusinessException("FEE_TOTAL_BELOW_PAID",
                    "Tổng tiền sau giảm (" + total.subtract(discount).toPlainString()
                            + ") không được nhỏ hơn số đã thu (" + paid.toPlainString()
                            + "). Hãy hoàn tiền trước khi giảm khoản phí.");
        }
        fee.setTotalAmount(total);
        fee.setDiscountAmount(discount);
        fee.setDueDate(request.getDueDate());
        fee.setNote(blankToNull(request.getNote()));
        fee.setStatus(FeeStatusCalculator.resolveFeeStatus(total, discount, paid, fee.getDueDate()));
        fee.setUpdatedBy(SecurityUtils.currentUsername());
        tuitionFeeRepository.saveAndFlush(fee);
        log.info("Đã cập nhật khoản học phí id={}, status={}", fee.getId(), fee.getStatus());
        return getDetail(fee.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TuitionFeeDetailResponse cancel(Long id, TuitionFeeCancelRequest request) {
        String reason = request == null ? null : blankToNull(request.getReason());
        if (reason == null) {
            throw new OracleBusinessException("CANCEL_REASON_REQUIRED", "Lý do hủy không được để trống.");
        }
        TuitionFeeEntity fee = lockActiveFee(id);
        if (DomainConstants.FEE_STATUS_CANCELLED.equals(fee.getStatus())) {
            throw new OracleBusinessException("FEE_CANCELLED",
                    "Khoản học phí " + fee.getFeeCode() + " đã bị hủy.");
        }
        if (nvl(fee.getPaidAmount()).signum() > 0) {
            throw new OracleBusinessException("FEE_HAS_PAYMENTS",
                    "Khoản học phí " + fee.getFeeCode() + " đã thu " + nvl(fee.getPaidAmount()).toPlainString()
                            + ". Hãy hoàn tiền các giao dịch trước khi hủy.");
        }
        fee.setStatus(DomainConstants.FEE_STATUS_CANCELLED);
        fee.setCancelReason(reason);
        fee.setUpdatedBy(SecurityUtils.currentUsername());
        tuitionFeeRepository.saveAndFlush(fee);
        log.info("Đã hủy khoản học phí id={}, lý do={}", fee.getId(), reason);
        return getDetail(fee.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TuitionFeeEntity fee = lockActiveFee(id);
        boolean unpaid = DomainConstants.FEE_STATUS_UNPAID.equals(fee.getStatus())
                && nvl(fee.getPaidAmount()).signum() == 0;
        if (!unpaid || !paymentTransactionRepository
                .findByTuitionFeeIdAndIsDeleted(fee.getId(), PersistenceFlags.NOT_DELETED).isEmpty()) {
            throw new OracleBusinessException("FEE_NOT_DELETABLE",
                    "Chỉ xóa được khoản học phí chưa thu (UNPAID) và chưa có giao dịch. "
                            + "Khoản đã quá hạn / đã thu hãy dùng chức năng Hủy hoặc hoàn tiền.");
        }
        fee.setIsDeleted(PersistenceFlags.DELETED);
        fee.setUpdatedBy(SecurityUtils.currentUsername());
        tuitionFeeRepository.saveAndFlush(fee);
        log.info("Đã xóa mềm khoản học phí id={}, feeCode={}", fee.getId(), fee.getFeeCode());
    }

    @Override
    @Transactional(readOnly = true)
    public TuitionQrResponseDto createQr(Long id, TuitionQrRequest request) {
        TuitionFeeEntity fee = requirePayableFee(id);
        BigDecimal remaining = FeeStatusCalculator.remainingOf(fee);
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
        return paymentService.confirmPayment(id, request);
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
            log.warn("Không khóa được khoản học phí id={}: {}", id, e.getMessage());
            throw new OracleBusinessException("FEE_LOCKED",
                    "Khoản học phí đang được xử lý bởi một giao dịch khác. Vui lòng thử lại sau ít phút.", e);
        }
    }

    private TuitionFeeEntity requirePayableFee(Long id) {
        return requirePayable(requireActiveFee(id));
    }

    private TuitionFeeEntity requirePayable(TuitionFeeEntity fee) {
        if ("CANCELLED".equals(fee.getStatus())) {
            throw new OracleBusinessException("FEE_CANCELLED",
                    "Khoản học phí " + fee.getFeeCode() + " đã bị hủy.");
        }
        if ("PAID".equals(fee.getStatus()) || FeeStatusCalculator.remainingOf(fee).compareTo(BigDecimal.ZERO) <= 0) {
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

    private TuitionFeeListItemDto toReport(TuitionFeeEntity entity, Map<Long, StudentEntity> students,
                                           Map<Long, ClassEntity> classes) {
        TuitionFeeListItemDto dto = financeAcademicMapper.toFeeListItem(entity);
        dto.setRemainingAmount(FeeStatusCalculator.remainingOf(entity));
        StudentEntity student = entity.getStudentId() == null ? null : students.get(entity.getStudentId());
        if (student != null) {
            dto.setStudentCode(student.getStudentCode());
            dto.setStudentName(student.getFullName());
            dto.setStudentStatus(student.getStatus());
        }
        ClassEntity clazz = entity.getClassId() == null ? null : classes.get(entity.getClassId());
        if (clazz != null) {
            dto.setClassCode(clazz.getClassCode());
            dto.setClassName(clazz.getClassName());
        }
        return dto;
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
