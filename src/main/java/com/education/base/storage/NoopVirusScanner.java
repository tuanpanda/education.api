package com.education.base.storage;

import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Không quét ({@code app.storage.scan.mode=none}, QĐ-F2): mọi file nhận {@code SKIPPED}.
 * {@code ClamAvVirusScanner} sẽ thay bean này khi bật ClamAV (trước khi học sinh nộp bài qua Internet).
 */
@Component
public class NoopVirusScanner implements VirusScanner {

    @Override
    public ScanResult scan(InputStream in, String displayName) {
        return ScanResult.skipped();
    }

    @Override
    public String mode() {
        return "none";
    }
}
