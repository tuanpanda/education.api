package com.education.base.service.impl;

import com.education.base.common.DomainConstants;
import com.education.base.common.FeeStatusCalculator;
import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.ConfirmPaymentRequest;
import com.education.base.dto.request.PaymentTransactionFilterRequest;
import com.education.base.dto.request.RefundTransactionRequest;
import com.education.base.dto.request.VoidTransactionRequest;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.PaymentTransactionDetailResponse;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.entity.ClassEntity;
import com.education.base.entity.PaymentTransactionEntity;
import com.education.base.entity.StudentEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.PaymentTransactionMapper;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.PaymentTransactionRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.repository.UserRepository;
import com.education.base.repository.spec.PaymentTransactionSpecifications;
import com.education.base.security.SecurityUtils;
import com.education.base.service.BankAccountService;
import com.education.base.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    /** Unique index trên {@code FIN_PAYMENT_TRANSACTIONS(BANK_REFERENCE_NO)} (migration V13_3). */
    static final String UQ_BANK_REFERENCE_INDEX = "UQ_FIN_TRANS_BANK_REF";

    /** Unique constraint trên {@code FIN_PAYMENT_TRANSACTIONS(TRANSACTION_CODE)} (V1). */
    static final String UQ_TRANSACTION_CODE = "UQ_FIN_TRANS_CODE";

    /** Unique index trên {@code FIN_PAYMENT_TRANSACTIONS(RECEIPT_NO)} (V14_2). */
    static final String UQ_RECEIPT_NO_INDEX = "UQ_FIN_TRANS_RECEIPT_NO";

    /** Cấp số phiếu kế tiếp: gọi trong ngữ cảnh PL/SQL vì {@code FN_NEXT_BIZ_CODE} có UPDATE (không gọi được từ SELECT). */
    static final String NEXT_RECEIPT_NO_CALL = "{? = call FN_NEXT_BIZ_CODE(?)}";

    private final TuitionFeeRepository tuitionFeeRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BankAccountService bankAccountService;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    /** Tên trung tâm in trên phiếu thu; bỏ trống thì lấy tên chủ tài khoản thụ hưởng đang dùng. */
    @Value("${app.receipt.center-name:}")
    private String centerName;

    @Value("${app.receipt.center-address:}")
    private String centerAddress;

    @Value("${app.receipt.center-phone:}")
    private String centerPhone;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentTransactionDto confirmPayment(Long id, ConfirmPaymentRequest request) {
        ConfirmPaymentRequest payload = request == null ? new ConfirmPaymentRequest() : request;
        TuitionFeeEntity fee = requirePayable(lockActiveFee(id));
        BigDecimal remaining = FeeStatusCalculator.remainingOf(fee);
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
        String payerName = firstNonBlank(payload.getPayerName(), defaultPayerName(fee));
        // Cấp số phiếu sát lúc ghi: FN_NEXT_BIZ_CODE khóa dòng SYS_CODE_RULES tới khi transaction kết thúc.
        String receiptNo = nextReceiptNo();
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
                    .transactionType(DomainConstants.TRANSACTION_TYPE_PAYMENT)
                    .receiptNo(receiptNo)
                    .payerName(truncate(payerName, 150))
                    .createdBy(SecurityUtils.currentUsername())
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw translatePaymentConstraint(e, bankRef, transactionCode);
        }

        BigDecimal newPaid = nvl(fee.getPaidAmount()).add(amount);
        fee.setPaidAmount(newPaid);
        fee.setStatus(FeeStatusCalculator.resolveFeeStatus(fee.getTotalAmount(), fee.getDiscountAmount(), newPaid, fee.getDueDate()));
        tuitionFeeRepository.save(fee);

        log.info("Đã xác nhận thanh toán {} cho khoản học phí id={}, status={}, receiptNo={}",
                amount, fee.getId(), fee.getStatus(), receiptNo);
        return paymentTransactionMapper.toDto(transaction);
    }

    // ---- Tra cứu ----------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentTransactionDto> search(PaymentTransactionFilterRequest filter) {
        PaymentTransactionFilterRequest criteria = filter == null ? new PaymentTransactionFilterRequest() : filter;
        Page<PaymentTransactionEntity> page = paymentTransactionRepository.findAll(
                PaymentTransactionSpecifications.fromFilter(criteria),
                PageRequest.of(criteria.resolvePageNo() - 1, criteria.resolvePageSize(),
                        Sort.by(Sort.Order.desc("paymentDate"), Sort.Order.desc("id"))));
        List<PaymentTransactionDto> content = enrich(page.getContent());
        return PageResponse.of(content, criteria.resolvePageNo(), criteria.resolvePageSize(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentTransactionDetailResponse getDetail(Long id) {
        PaymentTransactionEntity transaction = requireTransaction(id);
        PaymentTransactionDto dto = enrich(List.of(transaction)).getFirst();

        List<PaymentTransactionEntity> refunds = List.of();
        PaymentTransactionDto original = null;
        if (isPayment(transaction)) {
            refunds = paymentTransactionRepository.findByRefTransactionIdAndIsDeletedOrderByPaymentDateDescIdDesc(
                    transaction.getId(), PersistenceFlags.NOT_DELETED);
        } else if (transaction.getRefTransactionId() != null) {
            original = paymentTransactionRepository
                    .findByIdAndIsDeleted(transaction.getRefTransactionId(), PersistenceFlags.NOT_DELETED)
                    .map(paymentTransactionMapper::toDto)
                    .orElse(null);
        }

        TuitionFeeEntity fee = tuitionFeeRepository.findById(transaction.getTuitionFeeId()).orElse(null);
        BigDecimal refundable = refundableOf(transaction, refunds);
        return PaymentTransactionDetailResponse.builder()
                .transaction(dto)
                .fee(fee == null ? null : toFeeSummary(fee, dto))
                .refunds(paymentTransactionMapper.toDtoList(refunds))
                .originalTransaction(original)
                .voidable(isPayment(transaction)
                        && DomainConstants.TRANSACTION_STATUS_SUCCESS.equals(transaction.getStatus())
                        && transaction.getVoidedAt() == null
                        && refunds.stream().noneMatch(PaymentServiceImpl::isActiveRefund))
                .refundable(isPayment(transaction)
                        && DomainConstants.TRANSACTION_STATUS_SUCCESS.equals(transaction.getStatus())
                        && refundable.compareTo(BigDecimal.ZERO) > 0)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public String renderReceiptHtml(Long id) {
        PaymentTransactionEntity transaction = requireTransaction(id);
        String status = transaction.getStatus();
        if (!DomainConstants.TRANSACTION_STATUS_SUCCESS.equals(status)
                && !DomainConstants.TRANSACTION_STATUS_REFUNDED.equals(status)
                && !DomainConstants.TRANSACTION_STATUS_VOIDED.equals(status)) {
            throw new OracleBusinessException("RECEIPT_NOT_AVAILABLE",
                    "Giao dịch " + transaction.getTransactionCode() + " chưa thành công nên không có phiếu thu.");
        }
        TuitionFeeEntity fee = tuitionFeeRepository.findById(transaction.getTuitionFeeId()).orElse(null);
        StudentEntity student = fee == null || fee.getStudentId() == null ? null
                : studentRepository.findById(fee.getStudentId()).orElse(null);
        ClassEntity clazz = fee == null || fee.getClassId() == null ? null
                : classRepository.findById(fee.getClassId()).orElse(null);
        String refReceiptNo = transaction.getRefTransactionId() == null ? null
                : paymentTransactionRepository.findByIdAndIsDeleted(transaction.getRefTransactionId(),
                                PersistenceFlags.NOT_DELETED)
                        .map(ref -> firstNonBlank(ref.getReceiptNo(), ref.getTransactionCode()))
                        .orElse(null);

        String center = blankToNull(centerName);
        if (center == null) {
            center = activeAccountName();
        }
        ReceiptHtmlRenderer.ReceiptData data = ReceiptHtmlRenderer.ReceiptData.builder()
                .centerName(center)
                .centerAddress(blankToNull(centerAddress))
                .centerPhone(blankToNull(centerPhone))
                .receiptNo(transaction.getReceiptNo())
                .transactionCode(transaction.getTransactionCode())
                .transactionType(firstNonBlank(transaction.getTransactionType(), DomainConstants.TRANSACTION_TYPE_PAYMENT))
                .status(status)
                .paymentDate(transaction.getPaymentDate())
                .payerName(transaction.getPayerName())
                .studentCode(student == null ? null : student.getStudentCode())
                .studentName(student == null ? null : student.getFullName())
                .className(clazz == null ? null : clazz.getClassName())
                .feeCode(fee == null ? null : fee.getFeeCode())
                .feeMonth(fee == null ? null : fee.getFeeMonth())
                .feeYear(fee == null ? null : fee.getFeeYear())
                .amount(transaction.getAmount())
                .paymentMethod(transaction.getPaymentMethod())
                .bankReferenceNo(transaction.getBankReferenceNo())
                .note(transaction.getNote())
                .cashierName(cashierName(transaction.getCreatedBy()))
                .voidReason(transaction.getVoidReason())
                .voidedAt(transaction.getVoidedAt())
                .refReceiptNo(refReceiptNo)
                .build();
        return ReceiptHtmlRenderer.render(data);
    }

    // ---- Hủy / hoàn tiền ----------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentTransactionDto voidTransaction(Long id, VoidTransactionRequest request) {
        String reason = request == null ? null : blankToNull(request.getReason());
        if (reason == null) {
            throw new OracleBusinessException("VOID_REASON_REQUIRED", "Vui lòng nhập lý do hủy giao dịch.");
        }
        // Khóa khoản phí TRƯỚC rồi mới đọc giao dịch: mọi thao tác ghi trên giao dịch của khoản phí này đều
        // tuần tự hóa qua khóa dòng đó (cùng khóa với confirmPayment).
        TuitionFeeEntity fee = lockActiveFee(requireFeeIdOf(id));
        PaymentTransactionEntity transaction = requireTransaction(id);
        if (!isPayment(transaction)) {
            throw new OracleBusinessException("TRANSACTION_NOT_VOIDABLE",
                    "Chỉ hủy được giao dịch thu tiền, không hủy dòng hoàn tiền.");
        }
        if (DomainConstants.TRANSACTION_STATUS_VOIDED.equals(transaction.getStatus()) || transaction.getVoidedAt() != null) {
            throw new OracleBusinessException("TRANSACTION_ALREADY_VOIDED",
                    "Giao dịch " + transaction.getTransactionCode() + " đã bị hủy trước đó.");
        }
        List<PaymentTransactionEntity> refunds = paymentTransactionRepository
                .findByRefTransactionIdAndIsDeletedOrderByPaymentDateDescIdDesc(transaction.getId(),
                        PersistenceFlags.NOT_DELETED);
        if (refunds.stream().anyMatch(PaymentServiceImpl::isActiveRefund)) {
            throw new OracleBusinessException("TRANSACTION_HAS_REFUNDS",
                    "Giao dịch " + transaction.getTransactionCode() + " đã có hoàn tiền, không thể hủy.");
        }
        if (!DomainConstants.TRANSACTION_STATUS_SUCCESS.equals(transaction.getStatus())) {
            throw new OracleBusinessException("TRANSACTION_NOT_VOIDABLE",
                    "Chỉ hủy được giao dịch đang ở trạng thái SUCCESS (hiện tại: " + transaction.getStatus() + ").");
        }

        String actor = SecurityUtils.currentUsername();
        transaction.setStatus(DomainConstants.TRANSACTION_STATUS_VOIDED);
        transaction.setVoidedAt(LocalDateTime.now());
        transaction.setVoidedBy(actor);
        transaction.setVoidReason(reason);
        transaction.setUpdatedBy(actor);
        PaymentTransactionEntity saved = paymentTransactionRepository.save(transaction);

        recomputeFee(fee, saved);
        log.info("Đã hủy giao dịch id={} ({}), khoản học phí id={} paid={} status={}",
                saved.getId(), saved.getTransactionCode(), fee.getId(), fee.getPaidAmount(), fee.getStatus());
        return paymentTransactionMapper.toDto(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentTransactionDto refundTransaction(Long id, RefundTransactionRequest request) {
        RefundTransactionRequest payload = request == null ? new RefundTransactionRequest() : request;
        BigDecimal amount = payload.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new OracleBusinessException("INVALID_REFUND_AMOUNT", "Số tiền hoàn phải lớn hơn 0.");
        }
        String method = blankToNull(payload.getMethod());
        if (method == null) {
            throw new OracleBusinessException("REFUND_METHOD_REQUIRED", "Vui lòng chọn hình thức hoàn tiền.");
        }
        String reason = blankToNull(payload.getReason());
        if (reason == null) {
            throw new OracleBusinessException("REFUND_REASON_REQUIRED", "Vui lòng nhập lý do hoàn tiền.");
        }

        TuitionFeeEntity fee = lockActiveFee(requireFeeIdOf(id));
        PaymentTransactionEntity original = requireTransaction(id);
        if (!isPayment(original)) {
            throw new OracleBusinessException("TRANSACTION_NOT_REFUNDABLE",
                    "Chỉ hoàn tiền cho giao dịch thu tiền.");
        }
        if (DomainConstants.TRANSACTION_STATUS_REFUNDED.equals(original.getStatus())) {
            throw new OracleBusinessException("TRANSACTION_NOT_REFUNDABLE",
                    "Giao dịch " + original.getTransactionCode() + " đã được hoàn tiền toàn bộ.");
        }
        if (!DomainConstants.TRANSACTION_STATUS_SUCCESS.equals(original.getStatus())) {
            throw new OracleBusinessException("TRANSACTION_NOT_REFUNDABLE",
                    "Chỉ hoàn tiền cho giao dịch đang ở trạng thái SUCCESS (hiện tại: " + original.getStatus() + ").");
        }
        List<PaymentTransactionEntity> refunds = paymentTransactionRepository
                .findByRefTransactionIdAndIsDeletedOrderByPaymentDateDescIdDesc(original.getId(),
                        PersistenceFlags.NOT_DELETED);
        BigDecimal refundable = refundableOf(original, refunds);
        if (amount.compareTo(refundable) > 0) {
            throw new OracleBusinessException("REFUND_EXCEEDS_REFUNDABLE",
                    "Số tiền hoàn vượt quá số tiền còn có thể hoàn (" + refundable + ").");
        }

        String actor = SecurityUtils.currentUsername();
        String transactionCode = "RFD" + original.getId() + System.currentTimeMillis();
        String receiptNo = nextReceiptNo();
        PaymentTransactionEntity refund;
        try {
            refund = paymentTransactionRepository.saveAndFlush(PaymentTransactionEntity.builder()
                    .transactionCode(transactionCode)
                    .tuitionFeeId(fee.getId())
                    .amount(amount)
                    .paymentMethod(method.toUpperCase(Locale.ROOT))
                    .status(DomainConstants.TRANSACTION_STATUS_SUCCESS)
                    .transactionType(DomainConstants.TRANSACTION_TYPE_REFUND)
                    .refTransactionId(original.getId())
                    .receiptNo(receiptNo)
                    .payerName(truncate(firstNonBlank(payload.getPayerName(), original.getPayerName()), 150))
                    .note(reason)
                    .isDeleted(PersistenceFlags.NOT_DELETED)
                    .createdBy(actor)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw translatePaymentConstraint(e, null, transactionCode);
        }

        if (amount.compareTo(refundable) == 0) {
            original.setStatus(DomainConstants.TRANSACTION_STATUS_REFUNDED);
            original.setUpdatedBy(actor);
            original = paymentTransactionRepository.save(original);
        }

        recomputeFee(fee, original, refund);
        log.info("Đã hoàn {} cho giao dịch id={} (dòng hoàn id={}, receiptNo={}), khoản học phí id={} paid={} status={}",
                amount, original.getId(), refund.getId(), receiptNo, fee.getId(), fee.getPaidAmount(), fee.getStatus());
        return paymentTransactionMapper.toDto(refund);
    }

    // ---- Tính lại khoản phí ---------------------------------------------------------------------

    /**
     * Tính lại {@code PAID_AMOUNT} từ toàn bộ giao dịch của khoản phí và suy ra {@code STATUS}
     * qua {@link FeeStatusCalculator}; khoản phí {@code CANCELLED} giữ nguyên trạng thái.
     *
     * @param touched các giao dịch vừa ghi trong transaction này (ưu tiên trạng thái trong bộ nhớ)
     */
    private void recomputeFee(TuitionFeeEntity fee, PaymentTransactionEntity... touched) {
        Map<Object, PaymentTransactionEntity> rows = new LinkedHashMap<>();
        for (PaymentTransactionEntity row : paymentTransactionRepository
                .findByTuitionFeeIdAndIsDeleted(fee.getId(), PersistenceFlags.NOT_DELETED)) {
            rows.put(row.getId() == null ? new Object() : row.getId(), row);
        }
        for (PaymentTransactionEntity row : touched) {
            if (row != null) {
                rows.put(row.getId() == null ? new Object() : row.getId(), row);
            }
        }
        BigDecimal paid = netPaid(rows.values());
        fee.setPaidAmount(paid);
        if (!DomainConstants.FEE_STATUS_CANCELLED.equals(fee.getStatus())) {
            fee.setStatus(FeeStatusCalculator.resolveFeeStatus(fee.getTotalAmount(), fee.getDiscountAmount(), paid,
                    fee.getDueDate()));
        }
        tuitionFeeRepository.save(fee);
    }

    /**
     * Số tiền thực thu của khoản phí = Σ dòng {@code PAYMENT} có trạng thái {@code SUCCESS} hoặc {@code REFUNDED}
     * (đã hoàn toàn bộ vẫn là tiền đã thu, được bù trừ bởi các dòng hoàn) − Σ dòng {@code REFUND} {@code SUCCESS};
     * không âm. Dòng {@code VOIDED} / {@code PENDING} / {@code FAILED} không được tính.
     */
    static BigDecimal netPaid(Collection<PaymentTransactionEntity> transactions) {
        return FeeStatusCalculator.netPaid(transactions);
    }

    private static boolean isPayment(PaymentTransactionEntity row) {
        return row.getTransactionType() == null
                || DomainConstants.TRANSACTION_TYPE_PAYMENT.equals(row.getTransactionType());
    }

    /** Dòng hoàn tiền còn hiệu lực (chưa hủy). */
    private static boolean isActiveRefund(PaymentTransactionEntity row) {
        return DomainConstants.TRANSACTION_TYPE_REFUND.equals(row.getTransactionType())
                && DomainConstants.TRANSACTION_STATUS_SUCCESS.equals(row.getStatus());
    }

    private static BigDecimal refundedOf(Collection<PaymentTransactionEntity> refunds) {
        return refunds.stream()
                .filter(PaymentServiceImpl::isActiveRefund)
                .map(r -> nvl(r.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal refundableOf(PaymentTransactionEntity payment, Collection<PaymentTransactionEntity> refunds) {
        if (!isPayment(payment)) {
            return BigDecimal.ZERO;
        }
        BigDecimal refundable = nvl(payment.getAmount()).subtract(refundedOf(refunds));
        return refundable.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : refundable;
    }

    // ---- Nạp dữ liệu ---------------------------------------------------------------------------

    private PaymentTransactionEntity requireTransaction(Long id) {
        if (id == null) {
            throw new OracleBusinessException("TRANSACTION_ID_REQUIRED", "ID giao dịch không được để trống.");
        }
        return paymentTransactionRepository.findByIdAndIsDeleted(id, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "TRANSACTION_NOT_FOUND", "Không tìm thấy giao dịch với ID: " + id));
    }

    private Long requireFeeIdOf(Long transactionId) {
        if (transactionId == null) {
            throw new OracleBusinessException("TRANSACTION_ID_REQUIRED", "ID giao dịch không được để trống.");
        }
        return paymentTransactionRepository.findTuitionFeeIdByIdAndIsDeleted(transactionId, PersistenceFlags.NOT_DELETED)
                .orElseThrow(() -> new OracleBusinessException(
                        "TRANSACTION_NOT_FOUND", "Không tìm thấy giao dịch với ID: " + transactionId));
    }

    /** Điền thông tin khoản phí / học sinh / lớp / số tiền đã hoàn cho một trang giao dịch (nạp theo lô, tránh N+1). */
    private List<PaymentTransactionDto> enrich(List<PaymentTransactionEntity> transactions) {
        if (transactions.isEmpty()) {
            return List.of();
        }
        Map<Long, TuitionFeeEntity> fees = byId(tuitionFeeRepository.findAllById(distinct(transactions.stream()
                .map(PaymentTransactionEntity::getTuitionFeeId).toList())), TuitionFeeEntity::getId);
        Map<Long, StudentEntity> students = byId(studentRepository.findAllById(distinct(fees.values().stream()
                .map(TuitionFeeEntity::getStudentId).toList())), StudentEntity::getId);
        Map<Long, ClassEntity> classes = byId(classRepository.findAllById(distinct(fees.values().stream()
                .map(TuitionFeeEntity::getClassId).toList())), ClassEntity::getId);

        List<Long> paymentIds = transactions.stream().filter(PaymentServiceImpl::isPayment)
                .map(PaymentTransactionEntity::getId).filter(Objects::nonNull).toList();
        Map<Long, List<PaymentTransactionEntity>> refundsByOriginal = paymentIds.isEmpty() ? Map.of()
                : paymentTransactionRepository.findByRefTransactionIdInAndIsDeleted(paymentIds, PersistenceFlags.NOT_DELETED)
                .stream()
                .filter(r -> r.getRefTransactionId() != null)
                .collect(Collectors.groupingBy(PaymentTransactionEntity::getRefTransactionId));

        List<PaymentTransactionDto> result = new ArrayList<>(transactions.size());
        for (PaymentTransactionEntity entity : transactions) {
            PaymentTransactionDto dto = paymentTransactionMapper.toDto(entity);
            if (dto.getTransactionType() == null) {
                dto.setTransactionType(DomainConstants.TRANSACTION_TYPE_PAYMENT);
            }
            TuitionFeeEntity fee = fees.get(entity.getTuitionFeeId());
            if (fee != null) {
                dto.setFeeCode(fee.getFeeCode());
                dto.setStudentId(fee.getStudentId());
                dto.setClassId(fee.getClassId());
                StudentEntity student = fee.getStudentId() == null ? null : students.get(fee.getStudentId());
                if (student != null) {
                    dto.setStudentCode(student.getStudentCode());
                    dto.setStudentName(student.getFullName());
                }
                ClassEntity clazz = fee.getClassId() == null ? null : classes.get(fee.getClassId());
                if (clazz != null) {
                    dto.setClassCode(clazz.getClassCode());
                    dto.setClassName(clazz.getClassName());
                }
            }
            if (isPayment(entity)) {
                List<PaymentTransactionEntity> refunds = refundsByOriginal.getOrDefault(entity.getId(), List.of());
                dto.setRefundedAmount(refundedOf(refunds));
                dto.setRefundableAmount(DomainConstants.TRANSACTION_STATUS_SUCCESS.equals(entity.getStatus())
                        ? refundableOf(entity, refunds) : BigDecimal.ZERO);
            }
            result.add(dto);
        }
        return result;
    }

    private static PaymentTransactionDetailResponse.FeeSummary toFeeSummary(TuitionFeeEntity fee,
                                                                            PaymentTransactionDto enriched) {
        return PaymentTransactionDetailResponse.FeeSummary.builder()
                .id(fee.getId())
                .feeCode(fee.getFeeCode())
                .studentId(fee.getStudentId())
                .studentCode(enriched.getStudentCode())
                .studentName(enriched.getStudentName())
                .classId(fee.getClassId())
                .classCode(enriched.getClassCode())
                .className(enriched.getClassName())
                .feeMonth(fee.getFeeMonth())
                .feeYear(fee.getFeeYear())
                .totalAmount(fee.getTotalAmount())
                .discountAmount(fee.getDiscountAmount())
                .paidAmount(fee.getPaidAmount())
                .remainingAmount(FeeStatusCalculator.remainingOf(fee))
                .dueDate(fee.getDueDate())
                .status(fee.getStatus())
                .build();
    }

    private static List<Long> distinct(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static <T> Map<Long, T> byId(List<T> rows, Function<T, Long> id) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }

    /** Người nộp mặc định: phụ huynh của học sinh, nếu không có thì chính học sinh. */
    private String defaultPayerName(TuitionFeeEntity fee) {
        if (fee.getStudentId() == null) {
            return null;
        }
        return studentRepository.findById(fee.getStudentId())
                .map(student -> firstNonBlank(student.getParentName(), student.getFullName()))
                .orElse(null);
    }

    /** Cấp số phiếu thu / phiếu chi kế tiếp từ {@code SYS_CODE_RULES 'RECEIPT'} (V14_2). */
    private String nextReceiptNo() {
        String code = jdbcTemplate.execute(NEXT_RECEIPT_NO_CALL, (CallableStatementCallback<String>) cs -> {
            cs.registerOutParameter(1, Types.VARCHAR);
            cs.setString(2, DomainConstants.RECEIPT_RULE_CODE);
            cs.execute();
            return cs.getString(1);
        });
        if (code == null || code.isBlank()) {
            throw new OracleBusinessException("RECEIPT_NO_GENERATE_FAILED",
                    "Không sinh được số phiếu thu từ SYS_CODE_RULES (RULE_CODE = RECEIPT). Kiểm tra migration V14_2.");
        }
        return code.trim();
    }

    private String activeAccountName() {
        try {
            BankAccountResponseDto account = bankAccountService.requireActive();
            return account == null ? null : account.getAccountName();
        } catch (OracleBusinessException e) {
            log.debug("Không có STK đang dùng để lấy tên trung tâm cho phiếu thu: {}", e.getMessage());
            return null;
        }
    }

    private String cashierName(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userRepository.findByUsernameAndIsDeleted(username, PersistenceFlags.NOT_DELETED)
                .map(user -> firstNonBlank(user.getFullName(), username))
                .orElse(username);
    }

    // ---- Dùng chung với confirmPayment (chuyển nguyên trạng ở B-1) ---------------------------------

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
        if (detail.contains(UQ_RECEIPT_NO_INDEX)) {
            return new OracleBusinessException("RECEIPT_NO_DUPLICATED",
                    "Số phiếu thu bị trùng (quy luật RECEIPT trong SYS_CODE_RULES lệch với dữ liệu). Vui lòng thử lại.", e);
        }
        return e;
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

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
