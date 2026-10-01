package com.education.base.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptHtmlRendererTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "0|không",
            "5|năm",
            "10|mười",
            "11|mười một",
            "15|mười lăm",
            "21|hai mươi mốt",
            "24|hai mươi tư",
            "105|một trăm linh năm",
            "1000|một nghìn",
            "1050|một nghìn không trăm năm mươi",
            "1500000|một triệu năm trăm nghìn",
            "1250000|một triệu hai trăm năm mươi nghìn",
            "1005000|một triệu không trăm linh năm nghìn",
            "2000000000|hai tỷ",
            "3000500000|ba tỷ năm trăm nghìn"
    })
    void toVietnameseWords_readsVnd(String amount, String expected) {
        assertThat(ReceiptHtmlRenderer.toVietnameseWords(new BigDecimal(amount))).isEqualTo(expected);
    }

    @Test
    void formatVnd_usesDotGrouping() {
        assertThat(ReceiptHtmlRenderer.formatVnd(new BigDecimal("1250000.40"))).isEqualTo("1.250.000đ");
        assertThat(ReceiptHtmlRenderer.formatVnd(null)).isEqualTo("0đ");
    }

    @Test
    void render_payment_showsReceiptFieldsAndEscapesHtml() {
        String html = ReceiptHtmlRenderer.render(base()
                .payerName("<script>alert(1)</script>")
                .build());

        assertThat(html).contains("PHIẾU THU HỌC PHÍ", "PT20261000001", "PAY4", "Trung tâm ABC", "12 Lê Lợi",
                "ĐT: 0909000000", "Nguyen Van A", "SV01", "Lop 1", "FEE01 tháng 10/2026", "1.500.000đ",
                "Một triệu năm trăm nghìn đồng", "Chuyển khoản", "FT123", "Le Thu Ngan", "01/10/2026 09:30",
                "window.print()");
        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).contains("&lt;script&gt;");
        assertThat(html).doesNotContain("ĐÃ HỦY");
    }

    @Test
    void render_refund_usesPaymentVoucherTitleAndOriginalReceipt() {
        String html = ReceiptHtmlRenderer.render(base()
                .transactionType("REFUND")
                .receiptNo("PT20261000002")
                .refReceiptNo("PT20261000001")
                .build());

        assertThat(html).contains("PHIẾU CHI HOÀN HỌC PHÍ", "Người nhận tiền", "Hoàn học phí khoản FEE01",
                "(phiếu thu PT20261000001)");
    }

    @Test
    void render_voided_showsStampAndReason() {
        String html = ReceiptHtmlRenderer.render(base()
                .status("VOIDED")
                .voidReason("Nhập nhầm số tiền")
                .voidedAt(LocalDateTime.of(2026, 10, 2, 8, 0))
                .build());

        assertThat(html).contains("ĐÃ HỦY", "Nhập nhầm số tiền", "02/10/2026 08:00");
    }

    @Test
    void render_legacyRowWithoutReceiptNo_showsPlaceholder() {
        String html = ReceiptHtmlRenderer.render(base().receiptNo(null).centerName(null).build());

        assertThat(html).contains("(chưa cấp số)", "Trung tâm");
    }

    private static ReceiptHtmlRenderer.ReceiptData.ReceiptDataBuilder base() {
        return ReceiptHtmlRenderer.ReceiptData.builder()
                .centerName("Trung tâm ABC")
                .centerAddress("12 Lê Lợi")
                .centerPhone("0909000000")
                .receiptNo("PT20261000001")
                .transactionCode("PAY4")
                .transactionType("PAYMENT")
                .status("SUCCESS")
                .paymentDate(LocalDateTime.of(2026, 10, 1, 9, 30))
                .payerName("Nguyen Van Bo")
                .studentCode("SV01")
                .studentName("Nguyen Van A")
                .className("Lop 1")
                .feeCode("FEE01")
                .feeMonth(10)
                .feeYear(2026)
                .amount(new BigDecimal("1500000"))
                .paymentMethod("BANK_TRANSFER")
                .bankReferenceNo("FT123")
                .cashierName("Le Thu Ngan");
    }
}
