package com.education.base.common;

import com.education.base.exception.OracleBusinessException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VietQrHelperTest {

    private static final String BANK_BIN = "970436";
    private static final String ACCOUNT_NO = "1234567890";

    @Test
    void buildVietQrPayload_withoutAmount_usesStaticInitiationMethod() {
        String payload = VietQrHelper.buildVietQrPayload(BANK_BIN, ACCOUNT_NO, null, null);

        assertThat(payload).startsWith("000201010211");
        assertThat(payload).contains("0010A00000072701");
        assertThat(payload).contains("0208QRIBFTTA");
        assertThat(payload).contains("53037045802VN");
        assertThat(payload).matches(".*6304[0-9A-F]{4}$");
    }

    @Test
    void buildVietQrPayload_withAmount_usesDynamicInitiationAndAmountTag() {
        String payload = VietQrHelper.buildVietQrPayload(BANK_BIN, ACCOUNT_NO, 150000L, "HOC PHI KY 1");

        assertThat(payload).startsWith("000201010212");
        assertThat(payload).contains("5406150000");
        assertThat(payload).contains("0812HOC PHI KY 1");
        assertThat(payload).matches(".*6304[0-9A-F]{4}$");
    }

    @Test
    void buildVietQrPayload_zeroOrNegativeAmount_fallsBackToStaticQr() {
        assertThat(VietQrHelper.buildVietQrPayload(BANK_BIN, ACCOUNT_NO, 0L, null)).startsWith("000201010211");
        assertThat(VietQrHelper.buildVietQrPayload(BANK_BIN, ACCOUNT_NO, -5L, null)).startsWith("000201010211");
    }

    @Test
    void buildVietQrPayload_containsRequiredEmvCoTags() {
        String payload = VietQrHelper.buildVietQrPayload(BANK_BIN, ACCOUNT_NO, 250000L, "HP KY 1");

        assertThat(payload)
                .startsWith("000201")
                .contains("010212")
                .contains("0010A000000727")
                .contains("0006970436")
                .contains("01101234567890")
                .contains("0208QRIBFTTA")
                .contains("5303704")
                .contains("5406250000")
                .contains("5802VN")
                .contains("0807HP KY 1")
                .matches(".*6304[0-9A-F]{4}$");
    }

    @Test
    void buildVietQrPayload_crcMatchesEmvCoChecksum() {
        String payload = VietQrHelper.buildVietQrPayload(BANK_BIN, ACCOUNT_NO, 50000L, "TEST");

        String withoutChecksum = payload.substring(0, payload.length() - 4);
        String checksum = payload.substring(payload.length() - 4);

        assertThat(withoutChecksum).endsWith("6304");
        assertThat(checksum).isEqualTo(independentCrc16CcittFalse(withoutChecksum));
    }

    @Test
    void crc16CcittFalse_matchesPublishedTestVector123456789() {
        assertThat(independentCrc16CcittFalse("123456789")).isEqualTo("29B1");
    }

    @Test
    void buildVietQrPayload_blankBankBin_throwsBusinessException() {
        assertThatThrownBy(() -> VietQrHelper.buildVietQrPayload("  ", ACCOUNT_NO, null, null))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("QR_INVALID_BANK_BIN");
    }

    @Test
    void buildVietQrPayload_blankAccountNo_throwsBusinessException() {
        assertThatThrownBy(() -> VietQrHelper.buildVietQrPayload(BANK_BIN, "", null, null))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("QR_INVALID_ACCOUNT_NO");
    }

    @Test
    void generateQrBase64_returnsPngDataUrl() {
        String payload = VietQrHelper.buildVietQrPayload(BANK_BIN, ACCOUNT_NO, 1000L, "TEST");

        String dataUrl = VietQrHelper.generateQrBase64(payload, 128, 128);

        assertThat(dataUrl).startsWith("data:image/png;base64,");
        byte[] png = Base64.getDecoder().decode(dataUrl.substring("data:image/png;base64,".length()));
        assertThat(png[0]).isEqualTo((byte) 0x89);
        assertThat(new String(png, 1, 3)).isEqualTo("PNG");
    }

    @Test
    void generateQrBase64_invalidInput_throwsBusinessException() {
        assertThatThrownBy(() -> VietQrHelper.generateQrBase64("  ", 128, 128))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("QR_EMPTY_CONTENT");

        assertThatThrownBy(() -> VietQrHelper.generateQrBase64("content", 0, 128))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("QR_INVALID_SIZE");
    }

    @Test
    void buildQuickUrl_containsTemplateAndEncodedQueryParams() {
        String url = VietQrHelper.buildQuickUrl(BANK_BIN, ACCOUNT_NO, 10000L, "NOP HOC PHI", "NGUYEN VAN A");

        assertThat(url).startsWith("https://img.vietqr.io/image/970436-1234567890-compact2.png?");
        assertThat(url).contains("amount=10000");
        assertThat(url).contains("addInfo=NOP+HOC+PHI");
        assertThat(url).contains("accountName=NGUYEN+VAN+A");
    }

    @Test
    void buildQuickUrl_withoutOptionalValues_hasNoQueryString() {
        String url = VietQrHelper.buildQuickUrl(BANK_BIN, ACCOUNT_NO, null, null, null);

        assertThat(url).isEqualTo("https://img.vietqr.io/image/970436-1234567890-compact2.png");
    }

    /**
     * CRC-16/CCITT-FALSE độc lập (bảng lookup), không sao chép thuật toán bit-by-bit trong production.
     * Vector chuẩn: CRC("123456789") = 0x29B1.
     */
    private static String independentCrc16CcittFalse(String input) {
        int[] table = new int[256];
        for (int i = 0; i < 256; i++) {
            int crc = i << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
            table[i] = crc;
        }
        int crc = 0xFFFF;
        for (byte b : input.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            crc = ((crc << 8) ^ table[((crc >> 8) ^ (b & 0xFF)) & 0xFF]) & 0xFFFF;
        }
        return String.format("%04X", crc);
    }
}
