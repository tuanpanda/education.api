package com.education.base.common;

import com.education.base.entity.PaymentTransactionEntity;
import com.education.base.entity.TuitionFeeEntity;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;

/**
 * Tính số tiền còn phải thu và trạng thái của một khoản học phí ({@code FIN_TUITION_FEES}).
 * <p>
 * Dùng chung cho mọi luồng ghi khoản học phí (tạo / sinh phiếu tháng / xác nhận thanh toán / hủy giao dịch)
 * để quy tắc trạng thái chỉ nằm ở MỘT nơi. Tách nguyên văn từ {@code TuitionFeeServiceImpl} (Stream 0),
 * không đổi hành vi:
 * <ul>
 *     <li>{@code remaining = total - discount - paid}, không âm (giá trị null coi là 0).</li>
 *     <li>{@code remaining <= 0} → {@code PAID}.</li>
 *     <li>Quá hạn ({@code dueDate < today}) → {@code OVERDUE}, kể cả khi đã trả một phần.</li>
 *     <li>Đã trả một phần, chưa quá hạn → {@code PARTIAL}; chưa trả → {@code UNPAID}.</li>
 * </ul>
 * {@code CANCELLED} không bao giờ được suy ra ở đây: hủy khoản phí là thao tác nghiệp vụ riêng.
 * <p>
 * Số đã thu ({@code PAID_AMOUNT}) = {@link #netPaid}: Σ {@code PAYMENT} {@code SUCCESS}/{@code REFUNDED} −
 * Σ {@code REFUND} {@code SUCCESS}; {@code VOIDED}/{@code PENDING}/{@code FAILED} không tính. Báo cáo V14_3, sổ
 * công nợ, file Excel giao dịch và màn hình chi tiết khoản phí dùng đúng quy tắc này ({@link #paidEffect}).
 */
@UtilityClass
public class FeeStatusCalculator {

    /** Số tiền còn phải thu của khoản học phí, không âm. */
    public BigDecimal remainingOf(TuitionFeeEntity fee) {
        return remaining(fee.getTotalAmount(), fee.getDiscountAmount(), fee.getPaidAmount());
    }

    /** {@code total - discount - paid}, không âm; giá trị null coi là 0. */
    public BigDecimal remaining(BigDecimal total, BigDecimal discount, BigDecimal paid) {
        BigDecimal remaining = nvl(total).subtract(nvl(discount)).subtract(nvl(paid));
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    /**
     * Tác động của một giao dịch lên số đã thu: {@code +amount} với {@code PAYMENT} (hoặc loại null - dữ liệu cũ)
     * {@code SUCCESS}/{@code REFUNDED}; {@code -amount} với {@code REFUND} {@code SUCCESS}; {@code 0} còn lại.
     */
    public BigDecimal paidEffect(String transactionType, String status, BigDecimal amount) {
        if (DomainConstants.TRANSACTION_TYPE_REFUND.equals(transactionType)) {
            return DomainConstants.TRANSACTION_STATUS_SUCCESS.equals(status) ? nvl(amount).negate() : BigDecimal.ZERO;
        }
        if (transactionType == null || DomainConstants.TRANSACTION_TYPE_PAYMENT.equals(transactionType)) {
            return DomainConstants.TRANSACTION_STATUS_SUCCESS.equals(status)
                    || DomainConstants.TRANSACTION_STATUS_REFUNDED.equals(status) ? nvl(amount) : BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }

    /** Số thực thu của khoản phí từ các giao dịch (bỏ dòng đã xóa mềm), không âm. */
    public BigDecimal netPaid(Collection<PaymentTransactionEntity> transactions) {
        BigDecimal paid = BigDecimal.ZERO;
        for (PaymentTransactionEntity row : transactions) {
            if (row == null || Objects.equals(row.getIsDeleted(), PersistenceFlags.DELETED)) {
                continue;
            }
            paid = paid.add(paidEffect(row.getTransactionType(), row.getStatus(), row.getAmount()));
        }
        return paid.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : paid;
    }

    /** Trạng thái khoản học phí tính theo ngày hiện tại ({@link LocalDate#now()}). */
    public String resolveFeeStatus(BigDecimal total, BigDecimal discount, BigDecimal paid, LocalDate dueDate) {
        return resolveFeeStatus(total, discount, paid, dueDate, LocalDate.now());
    }

    /** Trạng thái khoản học phí tính theo ngày {@code today} (dễ kiểm thử). */
    public String resolveFeeStatus(BigDecimal total, BigDecimal discount, BigDecimal paid, LocalDate dueDate,
                                   LocalDate today) {
        BigDecimal remaining = nvl(total).subtract(nvl(discount)).subtract(nvl(paid));
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return DomainConstants.FEE_STATUS_PAID;
        }
        boolean overdue = dueDate != null && dueDate.isBefore(today);
        if (nvl(paid).compareTo(BigDecimal.ZERO) > 0) {
            return overdue ? DomainConstants.FEE_STATUS_OVERDUE : DomainConstants.FEE_STATUS_PARTIAL;
        }
        return overdue ? DomainConstants.FEE_STATUS_OVERDUE : DomainConstants.FEE_STATUS_UNPAID;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
