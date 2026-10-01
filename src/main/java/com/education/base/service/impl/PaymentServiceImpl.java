package com.education.base.service.impl;

import com.education.base.common.FeeStatusCalculator;
import com.education.base.common.PersistenceFlags;
import com.education.base.dto.request.ConfirmPaymentRequest;
import com.education.base.dto.response.BankAccountResponseDto;
import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.entity.PaymentTransactionEntity;
import com.education.base.entity.TuitionFeeEntity;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.PaymentTransactionMapper;
import com.education.base.repository.PaymentTransactionRepository;
import com.education.base.repository.TuitionFeeRepository;
import com.education.base.service.BankAccountService;
import com.education.base.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    /** Unique index trên {@code FIN_PAYMENT_TRANSACTIONS(BANK_REFERENCE_NO)} (migration V13_3). */
    static final String UQ_BANK_REFERENCE_INDEX = "UQ_FIN_TRANS_BANK_REF";

    /** Unique constraint trên {@code FIN_PAYMENT_TRANSACTIONS(TRANSACTION_CODE)} (V1). */
    static final String UQ_TRANSACTION_CODE = "UQ_FIN_TRANS_CODE";

    private final TuitionFeeRepository tuitionFeeRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BankAccountService bankAccountService;
    private final PaymentTransactionMapper paymentTransactionMapper;

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
        fee.setStatus(FeeStatusCalculator.resolveFeeStatus(fee.getTotalAmount(), fee.getDiscountAmount(), newPaid, fee.getDueDate()));
        tuitionFeeRepository.save(fee);

        log.info("Đã xác nhận thanh toán {} cho khoản học phí id={}, status={}",
                amount, fee.getId(), fee.getStatus());
        return paymentTransactionMapper.toDto(transaction);
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
}
