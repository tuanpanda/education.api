package com.education.base.common;

import com.education.base.exception.OracleBusinessException;
import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

/**
 * So sánh và chuẩn hóa khung giờ {@code HH:mm} (zero-padded, 24h).
 * So sánh từ điển hợp lệ vì luôn đủ 5 ký tự {@code HH:mm}.
 */
@UtilityClass
public class TimeSlots {

    private static final Pattern HHMM = Pattern.compile(DomainConstants.TIME_HHMM_PATTERN);

    public static String normalize(String raw, String fieldLabel) {
        if (raw == null || raw.isBlank()) {
            throw new OracleBusinessException("INVALID_TIME", fieldLabel + " không được để trống.");
        }
        String value = raw.trim();
        if (!HHMM.matcher(value).matches()) {
            throw new OracleBusinessException("INVALID_TIME",
                    fieldLabel + " phải có dạng HH:mm (00:00–23:59): " + raw);
        }
        return value;
    }

    public static void requireStartBeforeEnd(String startTime, String endTime) {
        if (startTime.compareTo(endTime) >= 0) {
            throw new OracleBusinessException("INVALID_TIME_RANGE",
                    "Giờ bắt đầu phải nhỏ hơn giờ kết thúc (" + startTime + " – " + endTime + ").");
        }
    }

    /**
     * Hai khoảng [start, end) giao nhau khi {@code start1 < end2 && start2 < end1}.
     */
    public static boolean overlaps(String start1, String end1, String start2, String end2) {
        if (start1 == null || end1 == null || start2 == null || end2 == null) {
            return false;
        }
        return start1.compareTo(end2) < 0 && start2.compareTo(end1) < 0;
    }
}
