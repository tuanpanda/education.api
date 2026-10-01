package com.education.base.common;

import com.education.base.entity.TuitionFeeEntity;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.LocalDate;

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
