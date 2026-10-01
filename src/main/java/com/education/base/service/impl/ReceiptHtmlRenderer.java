package com.education.base.service.impl;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Dựng HTML/CSS inline phiếu thu (giao dịch {@code PAYMENT}) / phiếu chi hoàn tiền ({@code REFUND}) khổ A5
 * để in từ trình duyệt. Mô phỏng {@code TuitionSlipHtmlRenderer} (không dùng chung code để hai phân hệ
 * độc lập).
 */
final class ReceiptHtmlRenderer {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String[] DIGITS = {
            "không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"};

    private static final String[] GROUP_UNITS = {"", " nghìn", " triệu", " tỷ"};

    private ReceiptHtmlRenderer() {
    }

    /** Dữ liệu in phiếu (đã được Service tổng hợp từ giao dịch, khoản phí, học sinh, lớp, STK). */
    @Value
    @Builder
    static class ReceiptData {
        String centerName;
        String centerAddress;
        String centerPhone;
        String receiptNo;
        String transactionCode;
        String transactionType;
        String status;
        LocalDateTime paymentDate;
        String payerName;
        String studentCode;
        String studentName;
        String className;
        String feeCode;
        Integer feeMonth;
        Integer feeYear;
        BigDecimal amount;
        String paymentMethod;
        String bankReferenceNo;
        String note;
        String cashierName;
        String voidReason;
        LocalDateTime voidedAt;
        String refReceiptNo;
    }

    static String render(ReceiptData data) {
        boolean refund = "REFUND".equals(data.getTransactionType());
        boolean voided = "VOIDED".equals(data.getStatus());
        String title = refund ? "PHIẾU CHI HOÀN HỌC PHÍ" : "PHIẾU THU HỌC PHÍ";
        String payerLabel = refund ? "Người nhận tiền" : "Người nộp tiền";
        String reasonLabel = refund ? "Lý do chi" : "Nội dung thu";
        String receiptNo = emptyTo(data.getReceiptNo(), "(chưa cấp số)");
        String period = data.getFeeMonth() != null && data.getFeeYear() != null
                ? " tháng " + data.getFeeMonth() + "/" + data.getFeeYear() : "";
        String content = refund
                ? "Hoàn học phí khoản " + emptyTo(data.getFeeCode(), "") + period
                + (blank(data.getRefReceiptNo()) ? "" : " (phiếu thu " + data.getRefReceiptNo().trim() + ")")
                : "Thu học phí khoản " + emptyTo(data.getFeeCode(), "") + period;
        String stamp = voided
                ? "<div class=\"stamp\">ĐÃ HỦY</div>"
                + "<div class=\"void\">Đã hủy lúc " + esc(formatDateTime(data.getVoidedAt())) + ". Lý do: "
                + esc(emptyTo(data.getVoidReason(), "")) + "</div>"
                : "";
        String noteRow = blank(data.getNote()) ? ""
                : "<tr><th>Ghi chú</th><td>" + esc(data.getNote().trim()) + "</td></tr>";
        String bankRefRow = blank(data.getBankReferenceNo()) ? ""
                : "<tr><th>Mã tham chiếu NH</th><td>" + esc(data.getBankReferenceNo().trim()) + "</td></tr>";
        String centerLines = line(data.getCenterAddress()) + line(blank(data.getCenterPhone()) ? null
                : "ĐT: " + data.getCenterPhone().trim());

        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>%s %s</title>
                  <style>
                    * { box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
                    body { margin: 0; background: #f3f4f6; font-family: "Times New Roman", "Segoe UI", serif; color: #111827; }
                    .page { display: flex; justify-content: center; padding: 24px 12px; }
                    .sheet { position: relative; width: 100%%; max-width: 760px; background: #fff; padding: 28px 36px 36px;
                             border: 1px solid #d1d5db; box-shadow: 0 10px 30px rgba(0,0,0,.08); }
                    .top { display: flex; justify-content: space-between; gap: 16px; }
                    .center .name { font-weight: 700; font-size: 16px; text-transform: uppercase; }
                    .center div { font-size: 13px; line-height: 1.5; }
                    .no { text-align: right; font-size: 13px; line-height: 1.6; }
                    .no strong { font-size: 15px; }
                    h1 { text-align: center; margin: 22px 0 4px; font-size: 24px; letter-spacing: .04em; }
                    .date { text-align: center; font-style: italic; font-size: 14px; margin-bottom: 18px; }
                    table { width: 100%%; border-collapse: collapse; font-size: 15px; }
                    th { text-align: left; width: 34%%; font-weight: 400; color: #374151; padding: 6px 8px 6px 0; vertical-align: top; }
                    td { padding: 6px 0; font-weight: 600; }
                    .amount { font-size: 20px; font-weight: 800; }
                    .words { font-style: italic; font-weight: 600; }
                    .sign { display: grid; grid-template-columns: 1fr 1fr; text-align: center; margin-top: 32px; font-size: 14px; }
                    .sign .role { font-weight: 700; }
                    .sign .hint { font-style: italic; font-size: 12px; color: #6b7280; }
                    .sign .who { margin-top: 64px; font-weight: 700; }
                    .stamp { position: absolute; top: 120px; right: 40px; transform: rotate(-14deg); border: 4px solid #dc2626;
                             color: #dc2626; font-size: 34px; font-weight: 900; padding: 6px 18px; border-radius: 8px; opacity: .85; }
                    .void { margin-top: 14px; color: #b91c1c; font-size: 13px; font-style: italic; }
                    .toolbar { text-align: center; margin: 0 0 12px; }
                    .toolbar span { display: inline-block; padding: 6px 14px; font-size: 13px; color: #555;
                                    border: 1px dashed #bbb; border-radius: 4px; }
                    @page { size: A5 landscape; margin: 10mm; }
                    @media print { body { background: #fff; } .page { padding: 0; } .sheet { border: 0; box-shadow: none; max-width: none; padding: 0; }
                                   .toolbar { display: none; } }
                  </style>
                </head>
                <body>
                  <div class="page">
                    <div>
                      <div class="toolbar"><span>Nhấn Ctrl + P (⌘ + P trên macOS) để in phiếu</span></div>
                      <article class="sheet">
                        %s
                        <div class="top">
                          <div class="center">
                            <div class="name">%s</div>
                            %s
                          </div>
                          <div class="no">
                            <div>Số phiếu: <strong>%s</strong></div>
                            <div>Mã giao dịch: %s</div>
                          </div>
                        </div>
                        <h1>%s</h1>
                        <div class="date">Ngày %s</div>
                        <table>
                          <tr><th>%s</th><td>%s</td></tr>
                          <tr><th>Học sinh</th><td>%s (%s)</td></tr>
                          <tr><th>Lớp</th><td>%s</td></tr>
                          <tr><th>%s</th><td>%s</td></tr>
                          <tr><th>Số tiền</th><td class="amount">%s</td></tr>
                          <tr><th>Bằng chữ</th><td class="words">%s</td></tr>
                          <tr><th>Hình thức</th><td>%s</td></tr>
                          %s
                          %s
                        </table>
                        <div class="sign">
                          <div><div class="role">%s</div><div class="hint">(Ký, ghi rõ họ tên)</div><div class="who">%s</div></div>
                          <div><div class="role">Thu ngân</div><div class="hint">(Ký, ghi rõ họ tên)</div><div class="who">%s</div></div>
                        </div>
                      </article>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                esc(refund ? "Phiếu chi" : "Phiếu thu"),
                esc(receiptNo),
                stamp,
                esc(emptyTo(data.getCenterName(), "Trung tâm")),
                centerLines,
                esc(receiptNo),
                esc(emptyTo(data.getTransactionCode(), "")),
                esc(title),
                esc(formatDateTime(data.getPaymentDate())),
                esc(payerLabel),
                esc(emptyTo(data.getPayerName(), "")),
                esc(emptyTo(data.getStudentName(), "")),
                esc(emptyTo(data.getStudentCode(), "")),
                esc(emptyTo(data.getClassName(), "-")),
                esc(reasonLabel),
                esc(content),
                esc(formatVnd(data.getAmount())),
                esc(capitalize(toVietnameseWords(data.getAmount())) + " đồng"),
                esc(methodLabel(data.getPaymentMethod())),
                bankRefRow,
                noteRow,
                esc(payerLabel),
                esc(emptyTo(data.getPayerName(), "")),
                esc(emptyTo(data.getCashierName(), "")));
    }

    static String formatVnd(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.setScale(0, RoundingMode.HALF_UP);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("vi-VN"));
        symbols.setGroupingSeparator('.');
        DecimalFormat format = new DecimalFormat("#,###", symbols);
        return format.format(value) + "đ";
    }

    static String methodLabel(String method) {
        if (method == null) {
            return "";
        }
        return switch (method) {
            case "CASH" -> "Tiền mặt";
            case "BANK_TRANSFER" -> "Chuyển khoản";
            case "VIETQR" -> "VietQR";
            case "CARD" -> "Thẻ";
            case "EWALLET" -> "Ví điện tử";
            default -> method;
        };
    }

    /**
     * Đọc số tiền (làm tròn đến đồng) bằng chữ tiếng Việt, ví dụ 1.250.000 → "một triệu hai trăm năm mươi nghìn".
     */
    static String toVietnameseWords(BigDecimal amount) {
        long value = amount == null ? 0L : amount.setScale(0, RoundingMode.HALF_UP).longValue();
        if (value == 0) {
            return DIGITS[0];
        }
        String sign = value < 0 ? "âm " : "";
        value = Math.abs(value);

        // Tách thành các "tỷ" (mỗi tỷ là một khối 9 chữ số) để đọc lặp lại "tỷ tỷ" với số rất lớn.
        StringBuilder out = new StringBuilder();
        long billions = value / 1_000_000_000L;
        long rest = value % 1_000_000_000L;
        if (billions > 0) {
            out.append(toVietnameseWords(BigDecimal.valueOf(billions))).append(" tỷ");
        }
        if (rest > 0) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(readBelowBillion(rest, billions > 0));
        }
        return sign + out.toString().trim().replaceAll("\\s+", " ");
    }

    private static String readBelowBillion(long value, boolean hasHigher) {
        int[] groups = {(int) (value / 1_000_000 % 1000), (int) (value / 1000 % 1000), (int) (value % 1000)};
        String[] units = {GROUP_UNITS[2], GROUP_UNITS[1], GROUP_UNITS[0]};
        StringBuilder out = new StringBuilder();
        boolean started = hasHigher;
        for (int i = 0; i < groups.length; i++) {
            int group = groups[i];
            if (group == 0) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(readTriple(group, started)).append(units[i]);
            started = true;
        }
        return out.toString();
    }

    /** Đọc một nhóm 3 chữ số; {@code full} = đã có nhóm cao hơn (phải đọc "không trăm", "linh"). */
    private static String readTriple(int number, boolean full) {
        int hundreds = number / 100;
        int tens = number / 10 % 10;
        int ones = number % 10;
        StringBuilder out = new StringBuilder();
        if (hundreds > 0 || full) {
            out.append(DIGITS[hundreds]).append(" trăm");
        }
        if (tens == 0) {
            if (ones > 0) {
                if (out.length() > 0) {
                    out.append(" linh");
                }
                out.append(' ').append(DIGITS[ones]);
            }
        } else {
            out.append(' ').append(tens == 1 ? "mười" : DIGITS[tens] + " mươi");
            if (ones == 1) {
                out.append(tens == 1 ? " một" : " mốt");
            } else if (ones == 4) {
                out.append(tens == 1 ? " bốn" : " tư");
            } else if (ones == 5) {
                out.append(" lăm");
            } else if (ones > 0) {
                out.append(' ').append(DIGITS[ones]);
            }
        }
        return out.toString().trim();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.forLanguageTag("vi-VN")) + value.substring(1);
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME);
    }

    private static String line(String value) {
        return blank(value) ? "" : "<div>" + esc(value.trim()) + "</div>";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String esc(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String emptyTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
