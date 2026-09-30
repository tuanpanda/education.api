package com.education.base.common.file;

import com.education.base.exception.OracleBusinessException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeFileTypeTest {

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void detect_acceptsAllowListedTypesByMagicBytes() {
        assertThat(SafeFileType.detect("a.JPG", bytes(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo(SafeFileType.JPEG);
        assertThat(SafeFileType.detect("a.jpeg", bytes(0xFF, 0xD8, 0xFF, 0xE1))).isEqualTo(SafeFileType.JPEG);
        assertThat(SafeFileType.detect("a.png", bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A))).isEqualTo(SafeFileType.PNG);
        assertThat(SafeFileType.detect("a.gif", ascii("GIF89a\u0001"))).isEqualTo(SafeFileType.GIF);
        assertThat(SafeFileType.detect("a.webp", ascii("RIFF\u0000\u0000\u0000\u0000WEBPVP8 "))).isEqualTo(SafeFileType.WEBP);
        assertThat(SafeFileType.detect("a.pdf", ascii("%PDF-1.7"))).isEqualTo(SafeFileType.PDF);
        assertThat(SafeFileType.detect("a.doc", bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1))).isEqualTo(SafeFileType.DOC);
        assertThat(SafeFileType.detect("a.xls", bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1))).isEqualTo(SafeFileType.XLS);
        assertThat(SafeFileType.detect("a.docx", ascii("PK\u0003\u0004"))).isEqualTo(SafeFileType.DOCX);
        assertThat(SafeFileType.detect("a.xlsx", ascii("PK\u0003\u0004"))).isEqualTo(SafeFileType.XLSX);
        assertThat(SafeFileType.detect("a.zip", ascii("PK\u0005\u0006"))).isEqualTo(SafeFileType.ZIP);
        assertThat(SafeFileType.detect("a.csv", "ma,ten\nHS01,Nguyễn Văn A\n".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(SafeFileType.CSV);
        assertThat(SafeFileType.detect("a.txt", "\uFEFFghi chú < 5".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(SafeFileType.TXT);
    }

    @Test
    void detect_rejectsSvgHtmlAndUnknownExtensions() {
        for (String name : new String[]{"x.svg", "x.SVG", "x.html", "x.htm", "x.xhtml", "x.xml", "x.js", "x.exe", "x", "x."}) {
            assertThatThrownBy(() -> SafeFileType.detect(name, ascii("<svg/>")))
                    .as(name)
                    .isInstanceOf(OracleBusinessException.class)
                    .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                    .isEqualTo("FILE_TYPE_NOT_ALLOWED");
        }
    }

    @Test
    void detect_rejectsMarkupInsideTextFiles() {
        for (String content : new String[]{"<!DOCTYPE html>", "  <html>", "<svg onload=x>", "<?xml version='1.0'?><svg/>",
                "hello <script>alert(1)</script>"}) {
            assertThatThrownBy(() -> SafeFileType.detect("x.txt", ascii(content)))
                    .as(content)
                    .isInstanceOf(OracleBusinessException.class)
                    .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                    .isEqualTo("FILE_CONTENT_MISMATCH");
        }
    }

    @Test
    void detect_rejectsEmptyHeaderForBinaryTypes() {
        assertThatThrownBy(() -> SafeFileType.detect("x.png", new byte[0]))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("FILE_CONTENT_MISMATCH");
    }

    @Test
    void forStoredFile_requiresAllowListedMimeMatchingExtension() {
        assertThat(SafeFileType.forStoredFile("image/png", "a.png")).contains(SafeFileType.PNG);
        assertThat(SafeFileType.forStoredFile("IMAGE/JPG", "a.jpg")).contains(SafeFileType.JPEG);
        assertThat(SafeFileType.forStoredFile("text/plain; charset=UTF-8", "a.txt")).contains(SafeFileType.TXT);
        assertThat(SafeFileType.forStoredFile("text/html", "a.html")).isEmpty();
        assertThat(SafeFileType.forStoredFile("image/svg+xml", "a.svg")).isEmpty();
        assertThat(SafeFileType.forStoredFile("image/png", "a.svg")).isEmpty();
        assertThat(SafeFileType.forStoredFile(null, "a.png")).isEmpty();
        assertThat(SafeFileType.PDF.isInlineSafe()).isTrue();
        assertThat(SafeFileType.TXT.isInlineSafe()).isFalse();
        assertThat(SafeFileType.DOCX.isInlineSafe()).isFalse();
    }
}
