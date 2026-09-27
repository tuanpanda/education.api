package com.education.base.common;

import com.education.base.exception.OracleBusinessException;
import lombok.experimental.UtilityClass;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Quy ước thứ trong tuần lưu Oracle: {@code 2}=Thứ Hai … {@code 7}=Thứ Bảy, {@code 8}=Chủ Nhật.
 * <p>
 * Java {@link DayOfWeek} dùng ISO-8601 ({@code MONDAY=1} … {@code SUNDAY=7}).
 */
@UtilityClass
public class WeekdayCodes {

    public static final int MIN = 2;

    public static final int MAX = 8;

    public static int from(DayOfWeek day) {
        if (day == null) {
            throw new OracleBusinessException("INVALID_DAY_OF_WEEK", "Thiếu thứ trong tuần.");
        }
        return day == DayOfWeek.SUNDAY ? MAX : day.getValue() + 1;
    }

    public static int from(LocalDate date) {
        if (date == null) {
            throw new OracleBusinessException("INVALID_DATE_RANGE", "Ngày học không được để trống.");
        }
        return from(date.getDayOfWeek());
    }

    public static DayOfWeek toDayOfWeek(int stored) {
        if (stored < MIN || stored > MAX) {
            throw new OracleBusinessException("INVALID_DAY_OF_WEEK",
                    "Thứ trong tuần chỉ nhận 2–8 (Thứ Hai–Chủ Nhật): " + stored);
        }
        return stored == MAX ? DayOfWeek.SUNDAY : DayOfWeek.of(stored - 1);
    }
}
