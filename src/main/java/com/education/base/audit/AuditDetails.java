package com.education.base.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Chuẩn hóa chi tiết nhật ký: che khóa nhạy cảm, rút gọn chuỗi dài, cắt theo số byte UTF-8 của cột Oracle.
 */
@UtilityClass
public class AuditDetails {

    public static final String REDACTED = "[REDACTED]";

    /** Khóa có chứa một trong các từ này (không phân biệt hoa thường) luôn bị che. */
    static final Pattern SENSITIVE_KEY = Pattern.compile(
            "pass(word|wd)?|pwd|token|secret|cookie|authorization|credential|otp|jwt|session|csrf|api[-_]?key",
            Pattern.CASE_INSENSITIVE);

    static final int MAX_VALUE_CHARS = 500;
    static final int MAX_COLLECTION_ITEMS = 50;
    private static final int MAX_DEPTH = 3;

    public static boolean isSensitiveKey(String key) {
        return key != null && SENSITIVE_KEY.matcher(key).find();
    }

    /** Bản sao đã che khóa nhạy cảm và rút gọn giá trị (giữ thứ tự khóa). */
    public static Map<String, Object> sanitize(Map<String, ?> details) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (details == null) {
            return out;
        }
        details.forEach((key, value) -> {
            if (key != null) {
                out.put(key, isSensitiveKey(key) ? REDACTED : sanitizeValue(value, 0));
            }
        });
        return out;
    }

    private static Object sanitizeValue(Object value, int depth) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            if (depth >= MAX_DEPTH) {
                return "{...}";
            }
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                String key = String.valueOf(k);
                out.put(key, isSensitiveKey(key) ? REDACTED : sanitizeValue(v, depth + 1));
            });
            return out;
        }
        if (value instanceof Collection<?> collection) {
            if (depth >= MAX_DEPTH) {
                return "[...]";
            }
            List<Object> out = new ArrayList<>();
            for (Object item : collection) {
                if (out.size() >= MAX_COLLECTION_ITEMS) {
                    out.add("...(+" + (collection.size() - MAX_COLLECTION_ITEMS) + ")");
                    break;
                }
                out.add(sanitizeValue(item, depth + 1));
            }
            return out;
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        String text = String.valueOf(value);
        return text.length() <= MAX_VALUE_CHARS ? text : text.substring(0, MAX_VALUE_CHARS) + "...";
    }

    /** JSON của chi tiết đã chuẩn hóa, cắt tối đa {@code maxBytes} byte UTF-8; rỗng -> {@code null}. */
    public static String toJson(ObjectMapper objectMapper, Map<String, ?> details, int maxBytes) {
        Map<String, Object> clean = sanitize(details);
        if (clean.isEmpty()) {
            return null;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(clean);
        } catch (JsonProcessingException | RuntimeException ex) {
            json = clean.toString();
        }
        return truncateUtf8(json, maxBytes);
    }

    /**
     * Cắt chuỗi để không vượt {@code maxBytes} byte UTF-8 (cột {@code VARCHAR2(n BYTE)}), không cắt giữa ký tự
     * nhiều byte / cặp surrogate.
     */
    public static String truncateUtf8(String value, int maxBytes) {
        if (value == null) {
            return null;
        }
        if (value.length() * 3 <= maxBytes || value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return value;
        }
        int bytes = 0;
        int end = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int size = codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
            if (bytes + size > maxBytes) {
                break;
            }
            bytes += size;
            end += Character.charCount(codePoint);
        }
        return value.substring(0, end);
    }

    /** Mã định danh dạng chữ hoa (ACTION / RESOURCE_TYPE). */
    public static String code(String value, int maxBytes) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return truncateUtf8(value.trim().toUpperCase(Locale.ROOT), maxBytes);
    }
}
