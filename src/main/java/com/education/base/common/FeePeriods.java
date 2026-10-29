package com.education.base.common;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Kỳ thu của khoản học phí (thu theo khoảng ngày - SPEC-fee-date-range v1.1, contract fee-range-api v1.3).
 * <ul>
 *     <li>Phiếu theo khoảng ngày: {@code IS_RANGE = 1}, {@code FEE_MONTH}/{@code FEE_YEAR} NULL, {@code PERIOD_FROM}/{@code PERIOD_TO} có giá trị.</li>
 *     <li>Phiếu tháng cũ: {@code FEE_MONTH}/{@code FEE_YEAR} có giá trị (có thể đã backfill {@code PERIOD_*} - V23_8).</li>
 *     <li>Khoản tạo tay: không có cả hai.</li>
 * </ul>
 * Mọi nơi hiển thị kỳ thu / nội dung chuyển khoản dùng lớp này để thống nhất.
 */
public final class FeePeriods {

    /** Giới hạn nội dung chuyển khoản VietQR / NAPAS. */
    public static final int TRANSFER_CONTENT_MAX = 25;

    private static final DateTimeFormatter MONTH_YEAR_TEXT = DateTimeFormatter.ofPattern("'Tháng' MM/yyyy", Locale.US);
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US);
    private static final DateTimeFormatter DDMM = DateTimeFormatter.ofPattern("ddMM", Locale.US);

    private FeePeriods() {
    }

    /** Giá trị {@code FIN_TUITION_FEES.IS_RANGE} của phiếu sinh theo khoảng ngày (V23_1). */
    public static final int RANGE_FLAG = 1;

    /** Giá trị mặc định: phiếu tháng cũ, khoản tạo tay. */
    public static final int NOT_RANGE_FLAG = 0;

    /**
     * Phiếu theo khoảng ngày khi và chỉ khi {@code IS_RANGE = 1} (chỉ {@code generate-range} ghi 1).
     * Không suy từ {@code FEE_MONTH IS NULL}: khoản tạo tay cũng có thể không có tháng.
     */
    public static boolean isRangeFee(Integer isRange) {
        return isRange != null && isRange == RANGE_FLAG;
    }

    /** Phiếu gói chưa đủ 10 buổi ({@code IS_PARTIAL = 1}, V24). */
    public static boolean isPartialFee(Integer isPartial) {
        return isPartial != null && isPartial == RANGE_FLAG;
    }

    /**
     * {@code "Tháng MM/yyyy"} cho phiếu tháng cũ; {@code "Từ dd/MM/yyyy đến dd/MM/yyyy"} cho phiếu khoảng ngày;
     * {@code null} khi không xác định được kỳ.
     */
    public static String periodText(Integer feeMonth, Integer feeYear, LocalDate periodFrom, LocalDate periodTo) {
        if (feeMonth != null && feeYear != null) {
            return formatMonthYear(feeMonth, feeYear);
        }
        if (periodFrom != null && periodTo != null) {
            return "Từ " + periodFrom.format(VN_DATE) + " đến " + periodTo.format(VN_DATE);
        }
        return null;
    }

    public static String formatMonthYear(Integer month, Integer year) {
        if (month == null || year == null) {
            return "";
        }
        return LocalDate.of(year, month, 1).format(MONTH_YEAR_TEXT);
    }

    /** Tháng hiển thị cho màn cũ (contract mục 9): phiếu khoảng ngày suy ra từ {@code PERIOD_FROM}, không ghi DB. */
    public static Integer displayMonth(Integer feeMonth, LocalDate periodFrom) {
        return feeMonth != null ? feeMonth : periodFrom == null ? null : periodFrom.getMonthValue();
    }

    public static Integer displayYear(Integer feeYear, Integer feeMonth, LocalDate periodFrom) {
        if (feeMonth != null) {
            return feeYear;
        }
        return periodFrom == null ? feeYear : Integer.valueOf(periodFrom.getYear());
    }

    /** Phiếu tháng cũ / khoản tạo tay: {@code "<code> HP T<m>"} (giữ nguyên như trước, không có tên). */
    public static String legacyTransferContent(String studentCode, Integer month) {
        return legacyTransferContent(null, studentCode, month);
    }

    /**
     * {@code "<TEN KHONG DAU> <code> HP T<m>"}, tối đa {@link #TRANSFER_CONTENT_MAX}. Phần {@code "<code> HP T<m>"}
     * giữ như trước; tên chỉ dùng chỗ còn lại (xem {@link #withStudentName}).
     */
    public static String legacyTransferContent(String studentName, String studentCode, Integer month) {
        String code = asciiCode(studentCode);
        int safeMonth = month == null ? 0 : month;
        String text = (code + " HP T" + safeMonth).trim();
        text = text.length() > TRANSFER_CONTENT_MAX ? text.substring(0, TRANSFER_CONTENT_MAX) : text;
        return withStudentName(studentName, text, TRANSFER_CONTENT_MAX);
    }

    /**
     * Phiếu khoảng ngày: {@code "<code> HP <ddMM>-<ddMM>"}, tối đa 25 ký tự, chỉ ASCII, không có {@code /}.
     * Quá dài thì cắt mã học sinh (chỉ xảy ra khi mã &gt; 12 ký tự), không bao giờ cắt ngày.
     */
    public static String rangeTransferContent(String studentCode, LocalDate from, LocalDate to) {
        return rangeTransferContent(null, studentCode, from, to);
    }

    /** {@code "<TEN KHONG DAU> <code> HP <ddMM>-<ddMM>"}: tên chỉ dùng chỗ còn lại trong 25 ký tự. */
    public static String rangeTransferContent(String studentName, String studentCode, LocalDate from, LocalDate to) {
        String suffix = " HP " + from.format(DDMM) + "-" + to.format(DDMM);
        String code = asciiCode(studentCode);
        int room = TRANSFER_CONTENT_MAX - suffix.length();
        if (code.length() > room) {
            code = code.substring(0, Math.max(room, 0));
        }
        return withStudentName(studentName, (code + suffix).trim(), TRANSFER_CONTENT_MAX);
    }

    /**
     * Ghép tên học sinh (không dấu, IN HOA, chỉ {@code A-Z0-9} và dấu cách) trước {@code content}, tổng
     * {@code <= max}. Không bao giờ cắt {@code content}: tên được cắt ở ranh giới từ nếu được, không thì cắt cứng;
     * hết chỗ thì bỏ hẳn tên. Tên rỗng / {@code null} -> {@code content} như cũ.
     */
    public static String withStudentName(String studentName, String content, int max) {
        String base = content == null ? "" : content.trim();
        String name = transferName(studentName);
        if (name.isEmpty()) {
            return base;
        }
        int room = max - base.length() - (base.isEmpty() ? 0 : 1);
        if (room <= 0) {
            return base;
        }
        if (name.length() > room) {
            boolean wordEnds = name.charAt(room) == ' ';
            String cut = name.substring(0, room);
            int lastSpace = cut.lastIndexOf(' ');
            if (!wordEnds && lastSpace > 0) {
                cut = cut.substring(0, lastSpace);
            }
            name = cut.trim();
        }
        return base.isEmpty() ? name : name + " " + base;
    }

    /** Tên cho nội dung chuyển khoản: bỏ dấu (Đ/đ -> D), IN HOA, chỉ {@code A-Z0-9} + một dấu cách giữa các từ. */
    public static String transferName(String studentName) {
        if (studentName == null || studentName.isBlank()) {
            return "";
        }
        return asciiFold(studentName).toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll(" +", " ")
                .trim();
    }

    /** Mã học sinh đã bị cắt trong nội dung chuyển khoản phiếu khoảng ngày (cần đối soát tay). */
    public static boolean isRangeTransferCodeTruncated(String studentCode) {
        return asciiCode(studentCode).length() > TRANSFER_CONTENT_MAX - " HP 0000-0000".length();
    }

    public static String asciiFold(String input) {
        String normalized = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D');
    }

    private static String asciiCode(String studentCode) {
        return asciiFold(studentCode).replaceAll("[^A-Za-z0-9]", "");
    }
}
