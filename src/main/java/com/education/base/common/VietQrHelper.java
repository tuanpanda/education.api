package com.education.base.common;

import com.education.base.exception.OracleBusinessException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Sinh mã VietQR thanh toán học phí theo chuẩn EMVCo (QR Code for Payment Systems).
 */
@Slf4j
@UtilityClass
public class VietQrHelper {

    /** GUID (AID) của NAPAS, dùng cho Merchant Account Information - Tag 38. */
    private static final String NAPAS_GUID = "A000000727";

    /** Mã tiền tệ VND theo ISO 4217. */
    private static final String CURRENCY_VND = "704";

    /** Mã quốc gia Việt Nam theo ISO 3166-1 alpha-2. */
    private static final String COUNTRY_VN = "VN";

    /** Service code: chuyển khoản tới số tài khoản. */
    private static final String SERVICE_CODE_ACCOUNT = "QRIBFTTA";

    /** QR tĩnh (không cố định số tiền). */
    private static final String INITIATION_STATIC = "11";

    /** QR động (đã cố định số tiền). */
    private static final String INITIATION_DYNAMIC = "12";

    /** Template ảnh QR của dịch vụ img.vietqr.io. */
    private static final String QUICK_LINK_TEMPLATE = "compact2";

    private static final String PNG_DATA_URL_PREFIX = "data:image/png;base64,";

    /**
     * Xây dựng chuỗi payload TLV (Tag - Length - Value) chuẩn EMVCo cho VietQR.
     * <p>
     * Các Tag sử dụng: {@code 00} (phiên bản), {@code 01} (phương thức khởi tạo),
     * {@code 38} (thông tin thụ hưởng qua NAPAS), {@code 53} (tiền tệ), {@code 54} (số tiền),
     * {@code 58} (quốc gia), {@code 62} (nội dung chuyển khoản) và {@code 63} (CRC16).
     *
     * @param bankBin     mã BIN ngân hàng theo NAPAS (ví dụ: {@code 970436} - Vietcombank).
     * @param accountNo   số tài khoản nhận tiền.
     * @param amount      số tiền (VND); {@code null} hoặc {@code <= 0} sẽ sinh QR tĩnh.
     * @param description nội dung chuyển khoản (có thể {@code null}).
     * @return chuỗi payload hoàn chỉnh đã gồm CRC16 tại Tag 63.
     */
    public String buildVietQrPayload(String bankBin, String accountNo, Long amount, String description) {
        String bin = requireValue(bankBin, "QR_INVALID_BANK_BIN", "Mã ngân hàng (BIN) không được để trống.");
        String account = requireValue(accountNo, "QR_INVALID_ACCOUNT_NO", "Số tài khoản không được để trống.");

        String beneficiaryInfo = tlv("00", bin) + tlv("01", account);
        String merchantAccountInfo = tlv("00", NAPAS_GUID)
                + tlv("01", beneficiaryInfo)
                + tlv("02", SERVICE_CODE_ACCOUNT);

        boolean hasAmount = hasAmount(amount);

        StringBuilder payload = new StringBuilder()
                .append(tlv("00", "01"))
                .append(tlv("01", hasAmount ? INITIATION_DYNAMIC : INITIATION_STATIC))
                .append(tlv("38", merchantAccountInfo))
                .append(tlv("53", CURRENCY_VND));

        if (hasAmount) {
            payload.append(tlv("54", String.valueOf(amount)));
        }

        payload.append(tlv("58", COUNTRY_VN));

        if (description != null && !description.isBlank()) {
            payload.append(tlv("62", tlv("08", description.trim())));
        }

        payload.append("6304");
        payload.append(String.format("%04X", crc16Ccitt(payload.toString())));
        return payload.toString();
    }

    /**
     * Vẽ mã QR bằng ZXing và trả về ảnh PNG dạng Base64 data URL,
     * có thể gán trực tiếp vào thuộc tính {@code src} của thẻ {@code <img>}.
     *
     * @param content nội dung cần mã hóa (thường là payload VietQR).
     * @param width   chiều rộng ảnh (px).
     * @param height  chiều cao ảnh (px).
     * @return chuỗi {@code data:image/png;base64,...}.
     */
    public String generateQrBase64(String content, int width, int height) {
        if (content == null || content.isBlank()) {
            throw new OracleBusinessException("QR_EMPTY_CONTENT", "Nội dung mã QR không được để trống.");
        }
        if (width <= 0 || height <= 0) {
            throw new OracleBusinessException("QR_INVALID_SIZE", "Kích thước mã QR phải lớn hơn 0.");
        }

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);

        try {
            BitMatrix bitMatrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                ImageIO.write(image, "PNG", out);
                return PNG_DATA_URL_PREFIX + Base64.getEncoder().encodeToString(out.toByteArray());
            }
        } catch (WriterException | IOException e) {
            log.error("Không thể tạo ảnh mã QR (width={}, height={})", width, height, e);
            throw new OracleBusinessException("QR_GENERATE_ERROR", "Không thể tạo mã QR, vui lòng thử lại.", e);
        }
    }

    /**
     * Sinh link nhanh tới ảnh QR của dịch vụ {@code img.vietqr.io}, không cần tự vẽ ảnh.
     *
     * @param bankBin     mã BIN ngân hàng.
     * @param accountNo   số tài khoản.
     * @param amount      số tiền (VND), có thể {@code null}.
     * @param description nội dung chuyển khoản, có thể {@code null}.
     * @param accountName tên chủ tài khoản hiển thị trên ảnh QR, có thể {@code null}.
     * @return URL đầy đủ trỏ tới ảnh QR PNG.
     */
    public String buildQuickUrl(String bankBin, String accountNo, Long amount, String description, String accountName) {
        String bin = requireValue(bankBin, "QR_INVALID_BANK_BIN", "Mã ngân hàng (BIN) không được để trống.");
        String account = requireValue(accountNo, "QR_INVALID_ACCOUNT_NO", "Số tài khoản không được để trống.");

        StringBuilder url = new StringBuilder("https://img.vietqr.io/image/")
                .append(bin).append("-")
                .append(account).append("-")
                .append(QUICK_LINK_TEMPLATE)
                .append(".png");

        List<String> params = new ArrayList<>();
        if (hasAmount(amount)) {
            params.add("amount=" + amount);
        }
        if (description != null && !description.isBlank()) {
            params.add("addInfo=" + encode(description.trim()));
        }
        if (accountName != null && !accountName.isBlank()) {
            params.add("accountName=" + encode(accountName.trim()));
        }

        if (!params.isEmpty()) {
            url.append("?").append(String.join("&", params));
        }
        return url.toString();
    }

    /**
     * Tạo một trường TLV chuẩn EMVCo; độ dài tính theo số byte UTF-8 và luôn gồm 2 chữ số.
     */
    private String tlv(String tag, String value) {
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        return tag + String.format("%02d", length) + value;
    }

    /**
     * Tính CRC16-CCITT (False): polynomial {@code 0x1021}, giá trị khởi tạo {@code 0xFFFF},
     * đúng chuẩn EMVCo dùng cho Tag 63.
     */
    private int crc16Ccitt(String input) {
        int crc = 0xFFFF;
        for (byte b : input.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return crc;
    }

    private boolean hasAmount(Long amount) {
        return amount != null && amount > 0;
    }

    private String requireValue(String value, String errorCode, String message) {
        if (value == null || value.isBlank()) {
            throw new OracleBusinessException(errorCode, message);
        }
        return value.trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
