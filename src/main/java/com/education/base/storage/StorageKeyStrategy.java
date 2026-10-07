package com.education.base.storage;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Sinh storage key cho blob mới (QĐ-F10): {@code {MODULE}[/{SUB}]/{yyyy}/{MM}/{uuid}.{ext}}.
 * <p>
 * Key luôn do server sinh: module / thư mục con chỉ gồm {@code [A-Z0-9_]}, đuôi chỉ {@code [a-z0-9]}, tên vật lý là
 * UUID - tên gốc (Unicode) chỉ nằm trong DB ({@code ORIGINAL_NAME}). File cũ (trước V19) giữ nguyên đường dẫn.
 */
@Component
public class StorageKeyStrategy {

    private static final Pattern SEGMENT = Pattern.compile("[A-Z0-9_]{1,50}");
    private static final Pattern EXT = Pattern.compile("[a-z0-9]{1,10}");

    private final Clock clock;

    public StorageKeyStrategy() {
        this(Clock.systemDefaultZone());
    }

    StorageKeyStrategy(Clock clock) {
        this.clock = clock;
    }

    /** Key mới + tên vật lý ({@code STORED_NAME}). */
    public record StorageKey(String key, String storedName) {
    }

    public StorageKey newKey(String module, String subFolder, String ext) {
        String mod = segment(module, "module");
        String sub = subFolder == null || subFolder.isBlank() ? null : segment(subFolder, "subFolder");
        String extension = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        if (!extension.isEmpty() && !EXT.matcher(extension).matches()) {
            throw new IllegalArgumentException("Invalid extension");
        }
        LocalDate today = LocalDate.now(clock);
        String storedName = UUID.randomUUID().toString().replace("-", "")
                + (extension.isEmpty() ? "" : "." + extension);
        StringBuilder key = new StringBuilder(mod).append('/');
        if (sub != null) {
            key.append(sub).append('/');
        }
        key.append(String.format("%04d/%02d/", today.getYear(), today.getMonthValue())).append(storedName);
        return new StorageKey(key.toString(), storedName);
    }

    private static String segment(String value, String name) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!SEGMENT.matcher(v).matches()) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return v;
    }
}
