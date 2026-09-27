package com.education.base.common;

import com.education.base.exception.OracleBusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSlotsTest {

    @Test
    void overlapsWhenWindowsCross() {
        assertThat(TimeSlots.overlaps("08:00", "09:30", "09:00", "10:00")).isTrue();
        assertThat(TimeSlots.overlaps("08:00", "09:00", "09:00", "10:00")).isFalse();
        assertThat(TimeSlots.overlaps("10:00", "11:00", "08:00", "09:00")).isFalse();
    }

    @Test
    void requireStartBeforeEnd_rejectsEqualOrInverted() {
        assertThatThrownBy(() -> TimeSlots.requireStartBeforeEnd("09:00", "09:00"))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("INVALID_TIME_RANGE");
    }
}
