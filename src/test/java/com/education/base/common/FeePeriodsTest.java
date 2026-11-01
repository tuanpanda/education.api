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

    // ---- Tên học sinh trước nội dung chuyển khoản (Tuấn 29/10/2026) ---------------------------------------

    @Test
    void transferName_stripsDiacritics_includingD_uppercase_onlyAZ09Space_collapsed() {
        assertThat(FeePeriods.transferName("Nguyễn Thị Ánh Tuyết")).isEqualTo("NGUYEN THI ANH TUYET");
        assertThat(FeePeriods.transferName("Đinh Xuân Đức")).isEqualTo("DINH XUAN DUC");
        assertThat(FeePeriods.transferName("đặng  văn-đạt (bé)")).isEqualTo("DANG VAN DAT BE");
        assertThat(FeePeriods.transferName("  Lê   Ưng  ")).isEqualTo("LE UNG");
        assertThat(FeePeriods.transferName("Trần Ngọc Ẩn 2")).isEqualTo("TRAN NGOC AN 2");
        assertThat(FeePeriods.transferName(null)).isEmpty();
        assertThat(FeePeriods.transferName("   ")).isEmpty();
        assertThat(FeePeriods.transferName("@@@")).isEmpty();
    }

    @Test
    void legacyTransferContent_withName_prefixesNameWithinLimit() {
        assertThat(FeePeriods.legacyTransferContent("Lê An", "SV01", 4)).isEqualTo("LE AN SV01 HP T4");
        assertThat(FeePeriods.legacyTransferContent("Đỗ Đức", "SV01", 4)).isEqualTo("DO DUC SV01 HP T4");
        // "HS000123 HP T4" = 14 -> 10 chỗ cho tên: cắt ở ranh giới từ.
        assertThat(FeePeriods.legacyTransferContent("Nguyễn Văn Anh", "HS000123", 4))
                .isEqualTo("NGUYEN VAN HS000123 HP T4").hasSize(25);
        assertThat(FeePeriods.legacyTransferContent("Nguyễn Thị Minh Khai", "HS000123", 4))
                .isEqualTo("NGUYEN THI HS000123 HP T4");
        // Từ đầu dài hơn chỗ trống -> bỏ hẳn tên (không cắt giữa từ).
        assertThat(FeePeriods.legacyTransferContent("Christopherson Lê", "HS000123", 4))
                .isEqualTo("HS000123 HP T4");
        // 10 chỗ: "LE VAN" (6) vừa, thêm "BACHKHOA" thì vượt -> chỉ giữ "LE VAN".
        assertThat(FeePeriods.legacyTransferContent("Lê Văn Báchkhoa", "HS000123", 4))
                .isEqualTo("LE VAN HS000123 HP T4");
        assertThat(FeePeriods.legacyTransferContent(null, "SV01", 4)).isEqualTo("SV01 HP T4");
        assertThat(FeePeriods.legacyTransferContent("  ", "SV01", 4)).isEqualTo("SV01 HP T4");
    }

    @Test
    void rangeTransferContent_withName_neverTruncatesCodeOrDates_dropsNameWhenNoRoom() {
        // "SV01 HP 0110-1511" = 17 -> 7 chỗ cho tên.
        assertThat(FeePeriods.rangeTransferContent("Lê Văn An", "SV01", OCT_1, NOV_15))
                .isEqualTo("LE VAN SV01 HP 0110-1511");
        assertThat(FeePeriods.rangeTransferContent("Đào Ý", "SV01", OCT_1, NOV_15))
                .isEqualTo("DAO Y SV01 HP 0110-1511");
        // "HS000123 HP 0110-1511" = 21 -> 3 chỗ: "NGUYEN" không vừa -> bỏ tên, không cắt giữa từ.
        assertThat(FeePeriods.rangeTransferContent("Nguyễn Văn A", "HS000123", OCT_1, NOV_15))
                .isEqualTo("HS000123 HP 0110-1511");
        // 3 chỗ vừa đủ một từ ngắn.
        assertThat(FeePeriods.rangeTransferContent("Lý An", "HS000123", OCT_1, NOV_15))
                .isEqualTo("LY HS000123 HP 0110-1511");
        // Mã 12 ký tự lấp đầy 25 -> bỏ hẳn tên, phần mã/kỳ y như cũ.
        assertThat(FeePeriods.rangeTransferContent("Nguyễn Văn A", "ABCDEFGHIJKL", OCT_1, NOV_15))
                .isEqualTo(FeePeriods.rangeTransferContent("ABCDEFGHIJKL", OCT_1, NOV_15))
                .isEqualTo("ABCDEFGHIJKL HP 0110-1511");
        assertThat(FeePeriods.rangeTransferContent("Nguyễn Văn A", "ABCDEFGHIJKLMNOP", OCT_1, NOV_15))
                .isEqualTo("ABCDEFGHIJKL HP 0110-1511");
        // Còn đúng 1 chỗ (chỉ đủ dấu cách) -> bỏ tên.
        assertThat(FeePeriods.rangeTransferContent("An", "ABCDEFGHIJK", OCT_1, NOV_15))
                .isEqualTo("ABCDEFGHIJK HP 0110-1511");
        assertThat(FeePeriods.rangeTransferContent(null, "HS000123", OCT_1, NOV_15)).isEqualTo("HS000123 HP 0110-1511");
    }

    @Test
    void withStudentName_alwaysWithinLimit_andEndsWithOriginalContent() {
        String[] names = {"Nguyễn Văn A", "Đặng Thị Bích Ngọc", "X", "Christopherson", "Lê  Ư"};
        String[] contents = {"SV01 HP T4", "HS000123 HP 0110-3110", "ABCDEFGHIJKL HP 0110-3110", ""};
        for (String n : names) {
            for (String c : contents) {
                String out = FeePeriods.withStudentName(n, c, FeePeriods.TRANSFER_CONTENT_MAX);
                assertThat(out.length()).as(n + "|" + c).isLessThanOrEqualTo(FeePeriods.TRANSFER_CONTENT_MAX);
                assertThat(out).endsWith(c).matches("[A-Z0-9 ]*[A-Za-z0-9 \\-]*").doesNotContain("  ");
                assertThat(out).doesNotStartWith(" ").doesNotEndWith(" ");
                String prefix = out.substring(0, out.length() - c.length()).trim();
                String fullName = FeePeriods.transferName(n);
                assertThat(prefix.isEmpty() || (fullName + " ").startsWith(prefix + " "))
                        .as("chỉ giữ nguyên từ: " + out).isTrue();
            }
        }
    }
}
