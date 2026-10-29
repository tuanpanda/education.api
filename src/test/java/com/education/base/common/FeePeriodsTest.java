package com.education.base.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Kỳ thu / nội dung chuyển khoản (contract fee-range-api v1.3 mục 4, 6, 9). */
class FeePeriodsTest {

    static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    static final LocalDate NOV_15 = LocalDate.of(2026, 11, 15);

    @Test
    void isRangeFee_onlyWhenIsRangeFlagIsOne() {
        assertThat(FeePeriods.isRangeFee(1)).isTrue();
        assertThat(FeePeriods.isRangeFee(0)).as("phiếu tháng cũ / khoản tạo tay").isFalse();
        assertThat(FeePeriods.isRangeFee(null)).isFalse();
        assertThat(FeePeriods.isRangeFee(2)).isFalse();
    }

    @Test
    void periodText_legacyRangeAndUnknown() {
        assertThat(FeePeriods.periodText(4, 2026, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                .isEqualTo("Tháng 04/2026");
        assertThat(FeePeriods.periodText(null, null, OCT_1, NOV_15)).isEqualTo("Từ 01/10/2026 đến 15/11/2026");
        assertThat(FeePeriods.periodText(null, null, null, null)).isNull();
    }

    @Test
    void displayMonthYear_rangeFeeDerivedFromPeriodFrom() {
        assertThat(FeePeriods.displayMonth(null, OCT_1)).isEqualTo(10);
        assertThat(FeePeriods.displayYear(null, null, OCT_1)).isEqualTo(2026);
        assertThat(FeePeriods.displayMonth(4, OCT_1)).isEqualTo(4);
        assertThat(FeePeriods.displayYear(2025, 4, OCT_1)).isEqualTo(2025);
        assertThat(FeePeriods.displayMonth(null, null)).isNull();
    }

    @Test
    void rangeTransferContent_spanningMonths() {
        assertThat(FeePeriods.rangeTransferContent("HS000123", OCT_1, NOV_15)).isEqualTo("HS000123 HP 0110-1511");
        assertThat(FeePeriods.rangeTransferContent("Đỗ-01", OCT_1, NOV_15)).isEqualTo("Do01 HP 0110-1511");
        assertThat(FeePeriods.rangeTransferContent("ABCDEFGHIJKLMNOP", OCT_1, NOV_15))
                .isEqualTo("ABCDEFGHIJKL HP 0110-1511").hasSize(25);
    }

    @Test
    void legacyTransferContent_unchanged() {
        assertThat(FeePeriods.legacyTransferContent("SV01", 4)).isEqualTo("SV01 HP T4");
        assertThat(FeePeriods.legacyTransferContent("SV01", null)).isEqualTo("SV01 HP T0");
    }
}
