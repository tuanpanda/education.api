package com.education.base.common;

import com.education.base.entity.TuitionFeeEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Khóa hành vi đã tách nguyên văn từ {@code TuitionFeeServiceImpl} (Stream 0): mọi thay đổi quy tắc trạng thái
 * phải sửa test này một cách có chủ đích.
 */
class FeeStatusCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    private static BigDecimal vnd(long value) {
        return BigDecimal.valueOf(value);
    }

    @Test
    void remaining_subtractsDiscountAndPaid_neverNegative() {
        assertThat(FeeStatusCalculator.remaining(vnd(1_000_000), vnd(100_000), vnd(300_000)))
                .isEqualByComparingTo("600000");
        assertThat(FeeStatusCalculator.remaining(vnd(500_000), vnd(0), vnd(700_000)))
                .isEqualByComparingTo("0");
        assertThat(FeeStatusCalculator.remaining(null, null, null)).isEqualByComparingTo("0");
    }

    @Test
    void remainingOf_readsEntityAmounts() {
        TuitionFeeEntity fee = TuitionFeeEntity.builder()
                .totalAmount(vnd(800_000))
                .discountAmount(vnd(50_000))
                .paidAmount(null)
                .build();
        assertThat(FeeStatusCalculator.remainingOf(fee)).isEqualByComparingTo("750000");
    }

    @Test
    void resolveFeeStatus_fullyPaidOrDiscounted_isPaid() {
        assertThat(FeeStatusCalculator.resolveFeeStatus(vnd(500_000), vnd(0), vnd(500_000), TODAY.minusDays(30), TODAY))
                .isEqualTo("PAID");
        assertThat(FeeStatusCalculator.resolveFeeStatus(vnd(500_000), vnd(500_000), vnd(0), null, TODAY))
                .isEqualTo("PAID");
    }

    @Test
    void resolveFeeStatus_notDueYet_unpaidOrPartial() {
        assertThat(FeeStatusCalculator.resolveFeeStatus(vnd(500_000), vnd(0), vnd(0), TODAY, TODAY))
                .isEqualTo("UNPAID");
        assertThat(FeeStatusCalculator.resolveFeeStatus(vnd(500_000), vnd(0), vnd(100_000), TODAY.plusDays(5), TODAY))
                .isEqualTo("PARTIAL");
        assertThat(FeeStatusCalculator.resolveFeeStatus(vnd(500_000), null, null, null, TODAY))
                .isEqualTo("UNPAID");
    }

    @Test
    void resolveFeeStatus_pastDue_isOverdueEvenWhenPartiallyPaid() {
        assertThat(FeeStatusCalculator.resolveFeeStatus(vnd(500_000), vnd(0), vnd(0), TODAY.minusDays(1), TODAY))
                .isEqualTo("OVERDUE");
        assertThat(FeeStatusCalculator.resolveFeeStatus(vnd(500_000), vnd(0), vnd(100_000), TODAY.minusDays(1), TODAY))
                .isEqualTo("OVERDUE");
    }

    @Test
    void resolveFeeStatus_withoutToday_usesCurrentDate() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        assertThat(FeeStatusCalculator.resolveFeeStatus(vnd(500_000), vnd(0), vnd(0), yesterday))
                .isEqualTo("OVERDUE");
    }

    @Test
    void paidEffect_followsNetPaidRule() {
        assertThat(FeeStatusCalculator.paidEffect("PAYMENT", "SUCCESS", vnd(100))).isEqualByComparingTo("100");
        assertThat(FeeStatusCalculator.paidEffect(null, "SUCCESS", vnd(100))).isEqualByComparingTo("100");
        assertThat(FeeStatusCalculator.paidEffect("PAYMENT", "REFUNDED", vnd(100))).isEqualByComparingTo("100");
        assertThat(FeeStatusCalculator.paidEffect("PAYMENT", "VOIDED", vnd(100))).isEqualByComparingTo("0");
        assertThat(FeeStatusCalculator.paidEffect("PAYMENT", "PENDING", vnd(100))).isEqualByComparingTo("0");
        assertThat(FeeStatusCalculator.paidEffect("REFUND", "SUCCESS", vnd(40))).isEqualByComparingTo("-40");
        assertThat(FeeStatusCalculator.paidEffect("REFUND", "VOIDED", vnd(40))).isEqualByComparingTo("0");
        assertThat(FeeStatusCalculator.paidEffect("OTHER", "SUCCESS", vnd(40))).isEqualByComparingTo("0");
    }

    @Test
    void netPaid_sumsEffectsSkipsDeletedAndNeverNegative() {
        com.education.base.entity.PaymentTransactionEntity pay = com.education.base.entity.PaymentTransactionEntity
                .builder().transactionType("PAYMENT").status("REFUNDED").amount(vnd(300)).isDeleted(0).build();
        com.education.base.entity.PaymentTransactionEntity refund = com.education.base.entity.PaymentTransactionEntity
                .builder().transactionType("REFUND").status("SUCCESS").amount(vnd(300)).isDeleted(0).build();
        com.education.base.entity.PaymentTransactionEntity deleted = com.education.base.entity.PaymentTransactionEntity
                .builder().transactionType("PAYMENT").status("SUCCESS").amount(vnd(999)).isDeleted(1).build();
        com.education.base.entity.PaymentTransactionEntity extraRefund = com.education.base.entity.PaymentTransactionEntity
                .builder().transactionType("REFUND").status("SUCCESS").amount(vnd(50)).isDeleted(0).build();

        assertThat(FeeStatusCalculator.netPaid(java.util.List.of(pay, refund, deleted))).isEqualByComparingTo("0");
        assertThat(FeeStatusCalculator.netPaid(java.util.List.of(pay, refund, extraRefund))).isEqualByComparingTo("0");
        assertThat(FeeStatusCalculator.netPaid(java.util.List.of(pay))).isEqualByComparingTo("300");
    }
}
