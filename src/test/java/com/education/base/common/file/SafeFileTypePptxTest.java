package com.education.base.common.file;

import com.education.base.exception.OracleBusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** GĐ3: thêm PPTX (magic ZIP như docx, không inline); không nhận .ppt cũ. */
class SafeFileTypePptxTest {

    static final byte[] ZIP = {'P', 'K', 0x03, 0x04, 0, 0};
    static final String MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    @Test
    void pptx_detectedByZipMagic_notInline() {
        assertThat(SafeFileType.detect("bai giang.PPTX", ZIP)).isEqualTo(SafeFileType.PPTX);
        assertThat(SafeFileType.PPTX.mimeType()).isEqualTo(MIME);
        assertThat(SafeFileType.PPTX.isInlineSafe()).isFalse();
        assertThat(SafeFileType.forStoredFile(MIME, "a.pptx")).contains(SafeFileType.PPTX);
        assertThat(SafeFileType.allExtensions()).contains("pptx").doesNotContain("ppt");
        assertThat(SafeFileType.inlineExtensions()).doesNotContain("pptx");
    }

    @Test
    void pptx_wrongContent_orLegacyPpt_rejected() {
        assertThatThrownBy(() -> SafeFileType.detect("a.pptx", "%PDF-1.7".getBytes()))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("FILE_CONTENT_MISMATCH");
        assertThatThrownBy(() -> SafeFileType.detect("a.ppt", new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}))
                .isInstanceOf(OracleBusinessException.class).extracting("errorCode").isEqualTo("FILE_TYPE_NOT_ALLOWED");
    }
}
