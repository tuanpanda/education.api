package com.education.base.common;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WeekdayCodesTest {

    @Test
    void mapsIsoMondayToStoredTuesdayConvention() {
        assertThat(WeekdayCodes.from(DayOfWeek.MONDAY)).isEqualTo(2);
        assertThat(WeekdayCodes.from(DayOfWeek.SATURDAY)).isEqualTo(7);
        assertThat(WeekdayCodes.from(DayOfWeek.SUNDAY)).isEqualTo(8);
    }

    @Test
    void roundTripsAllStoredValues() {
        for (int stored = 2; stored <= 8; stored++) {
            assertThat(WeekdayCodes.from(WeekdayCodes.toDayOfWeek(stored))).isEqualTo(stored);
        }
    }

    @Test
    void fromDateUsesJavaDayOfWeek() {
        assertThat(WeekdayCodes.from(LocalDate.of(2026, 9, 21))).isEqualTo(2);
        assertThat(WeekdayCodes.from(LocalDate.of(2026, 9, 27))).isEqualTo(8);
    }
}
