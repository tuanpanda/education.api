package com.education.base.service.impl;

import com.education.base.dto.response.TuitionSlipResponseDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Dựng HTML/CSS inline phiếu học phí điện tử (tỉ lệ card mobile) để in, xuất ảnh/PDF hoặc gửi Zalo.
 */
final class TuitionSlipHtmlRenderer {

    private TuitionSlipHtmlRenderer() {
    }

    static String render(TuitionSlipResponseDto data) {
        String className = esc(emptyTo(data.getClassName(), "Lớp học"));
        String titleMonth = esc(emptyTo(data.getMonthYearText(), ""));
        String label = esc(emptyTo(data.getSlipLabel(), "Mặc Định"));
        String studentName = esc(emptyTo(data.getStudentName(), ""));
        String studentCode = esc(emptyTo(data.getStudentCode(), ""));
        String comment = esc(emptyTo(data.getTeacherComment(), "Bé ngoan, chuyên cần trong tháng này."));
        String wish = esc(emptyTo(data.getFooterWish(), "Chúc em luôn vui vẻ và học tốt! ❤️"));
        String bankName = esc(emptyTo(data.getBankName(), ""));
        String accountNo = esc(emptyTo(data.getAccountNo(), ""));
        String accountName = esc(emptyTo(data.getAccountName(), ""));
        String qrSrc = safeDataUrl(data.getQrBase64());
        String price = formatVnd(data.getPricePerSession());
        String total = formatVnd(data.getTotalAmount());
        String status = data.getStatus() == null ? "" : data.getStatus().trim();
        String amountRows = amountRows(data);
        String statusText = esc(statusLabel(status));
        String qrBlock = qrSrc.isEmpty()
                ? "<div class=\"no-qr\">" + esc(noQrText(status)) + "</div>"
                : "<img alt=\"VietQR\" src=\"" + qrSrc + "\"/>";
        int sessions = data.getTotalSessions() == null ? 0 : data.getTotalSessions();

        StringBuilder badges = new StringBuilder();
        List<String> dates = data.getAttendedDates() == null ? List.of() : data.getAttendedDates();
        for (String day : dates) {
            badges.append("<span class=\"day\">").append(esc(day)).append("</span>");
        }
        if (dates.isEmpty()) {
            badges.append("<span class=\"day empty\">Chưa có buổi PRESENT</span>");
        }

        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>Phiếu học phí %s</title>
                  <style>
                    * { box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
                    body { margin: 0; background: #e8f5f2; font-family: "Nunito", "Segoe UI", "Trebuchet MS", sans-serif; color: #1f2937; }
                    .page { min-height: 100vh; display: flex; justify-content: center; padding: 16px; }
                    .card { width: 100%%; max-width: 390px; background: #fff; border-radius: 28px; overflow: hidden;
                            box-shadow: 0 18px 40px rgba(15, 118, 110, 0.18); }
                    .head { background: linear-gradient(160deg, #0f766e 0%%, #14b8a6 100%%); color: #fff; padding: 22px 22px 18px; position: relative; }
                    .head h1 { margin: 0; font-size: 13px; letter-spacing: .12em; font-weight: 800; text-transform: uppercase; opacity: .95; }
                    .head h2 { margin: 10px 0 4px; font-size: 26px; line-height: 1.15; font-weight: 800; }
                    .head .period { font-size: 15px; font-weight: 700; opacity: .95; }
                    .chip { position: absolute; top: 18px; right: 18px; background: #fff; color: #0f766e;
                            border-radius: 999px; padding: 6px 12px; font-size: 12px; font-weight: 800; }
                    .body { padding: 18px 20px 24px; }
                    .row { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
                    .label { font-size: 13px; color: #6b7280; font-weight: 700; }
                    .value { font-size: 16px; font-weight: 800; color: #111827; }
                    .student { font-size: 20px; font-weight: 800; margin: 0 0 14px; }
                    .total-box { border: 2px solid #99f6e4; background: #f0fdfa; border-radius: 18px; padding: 14px 16px; margin: 14px 0 18px; text-align: center; }
                    .total-box .cap { font-size: 12px; font-weight: 800; letter-spacing: .08em; color: #0f766e; }
                    .total-box .money { font-size: 32px; font-weight: 900; color: #0f766e; margin-top: 4px; }
                    .amounts { margin: -6px 0 16px; }
                    .amounts .remain .value { color: #b91c1c; }
                    .status { display: inline-block; border-radius: 999px; padding: 3px 10px; font-size: 12px; font-weight: 800;
                              background: #e5e7eb; color: #374151; margin-bottom: 12px; }
                    .status.PAID { background: #dcfce7; color: #166534; }
                    .status.PARTIAL { background: #fef3c7; color: #92400e; }
                    .status.OVERDUE { background: #fee2e2; color: #991b1b; }
                    .status.CANCELLED { background: #e5e7eb; color: #4b5563; }
                    .no-qr { padding: 18px 8px; font-size: 14px; font-weight: 800; color: #0f766e; }
                    .section { font-size: 13px; font-weight: 800; margin: 8px 0 10px; }
                    .days { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
                    .day { display: inline-flex; align-items: center; justify-content: center; background: #ccfbf1;
                           color: #0f766e; border-radius: 999px; padding: 7px 0; font-size: 12px; font-weight: 800; }
                    .day.empty { grid-column: 1 / -1; background: #f3f4f6; color: #6b7280; }
                    .comment { margin-top: 16px; border: 1.5px solid #fde68a; background: #fffbeb; border-radius: 16px; padding: 12px 14px; font-size: 14px; line-height: 1.45; }
                    .wish { text-align: center; margin: 16px 0 8px; font-size: 15px; font-weight: 800; color: #0f766e; }
                    .qr-box { margin-top: 8px; border: 2px dashed #14b8a6; border-radius: 20px; padding: 16px 14px 14px; text-align: center; }
                    .vietqr { font-weight: 900; color: #0f766e; letter-spacing: .04em; font-size: 18px; margin-bottom: 8px; }
                    .qr-box img { width: 168px; height: 168px; object-fit: contain; background: #fff; }
                    .bank { margin-top: 10px; font-size: 13px; line-height: 1.5; color: #374151; }
                    .bank strong { color: #111827; }
                    @media print { body { background: #fff; } .page { padding: 0; } .card { box-shadow: none; max-width: none; } }
                  </style>
                </head>
                <body>
                  <div class="page">
                    <article class="card">
                      <header class="head">
                        <div class="chip">%s</div>
                        <h1>PHIẾU HỌC PHÍ</h1>
                        <h2>%s</h2>
                        <div class="period">%s</div>
                      </header>
                      <div class="body">
                        <p class="student">📚 %s <span style="font-size:13px;color:#6b7280;font-weight:700">(%s)</span></p>
                        <div class="status %s">%s</div>
                        <div class="row"><span class="label">Học phí / buổi</span><span class="value">%s</span></div>
                        <div class="row"><span class="label">Số buổi học</span><span class="value">%d buổi</span></div>
                        <div class="total-box">
                          <div class="cap">💎 TỔNG HỌC PHÍ</div>
                          <div class="money">%s</div>
                        </div>
                        <div class="amounts">%s</div>
                        <div class="section">📝 Ngày đi học</div>
                        <div class="days">%s</div>
                        <div class="comment">%s</div>
                        <div class="wish">%s</div>
                        <div class="qr-box">
                          <div class="vietqr">VietQR</div>
                          %s
                          <div class="bank">
                            <div><strong>%s</strong></div>
                            <div>STK: <strong>%s</strong></div>
                            <div>%s</div>
                          </div>
                        </div>
                      </div>
                    </article>
                  </div>
                </body>
                </html>
                """.formatted(
                studentCode,
                label,
                className,
                titleMonth,
                studentName,
                studentCode,
                esc(status),
                statusText,
                price,
                sessions,
                total,
                amountRows,
                badges,
                comment,
                wish,
                qrBlock,
                bankName,
                accountNo,
                accountName);
    }

    /** Dòng miễn giảm / đã thu / còn phải đóng (B6); bỏ qua dòng giảm / đã thu khi bằng 0. */
    private static String amountRows(TuitionSlipResponseDto data) {
        StringBuilder rows = new StringBuilder();
        if (positive(data.getDiscountAmount())) {
            rows.append(row("", "Miễn giảm", "-" + formatVnd(data.getDiscountAmount())));
        }
        if (positive(data.getPaidAmount())) {
            rows.append(row("", "Đã thu", formatVnd(data.getPaidAmount())));
        }
        if (data.getRemainingAmount() != null) {
            rows.append(row("remain", "Còn phải đóng", formatVnd(data.getRemainingAmount())));
        }
        return rows.toString();
    }

    private static String row(String cssClass, String label, String value) {
        return "<div class=\"row " + cssClass + "\"><span class=\"label\">" + esc(label)
                + "</span><span class=\"value\">" + esc(value) + "</span></div>";
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    static String statusLabel(String status) {
        return switch (status) {
            case "UNPAID" -> "Chưa đóng";
            case "PARTIAL" -> "Đóng một phần";
            case "PAID" -> "Đã đóng đủ";
            case "OVERDUE" -> "Quá hạn";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }

    private static String noQrText(String status) {
        if ("CANCELLED".equals(status)) {
            return "Khoản học phí đã hủy - không cần thanh toán.";
        }
        return "Đã thanh toán đủ - không cần chuyển khoản.";
    }

    static String formatVnd(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.setScale(0, RoundingMode.HALF_UP);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("vi-VN"));
        symbols.setGroupingSeparator('.');
        DecimalFormat format = new DecimalFormat("#,###", symbols);
        return format.format(value) + "đ";
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

    private static String safeDataUrl(String qrBase64) {
        if (qrBase64 != null && qrBase64.startsWith("data:image/")) {
            return qrBase64;
        }
        return "";
    }
}
