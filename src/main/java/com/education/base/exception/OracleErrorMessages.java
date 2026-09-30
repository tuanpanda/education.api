package com.education.base.exception;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chuẩn hóa thông báo lỗi từ Oracle trước khi trả về client.
 * <ul>
 *     <li>Lỗi nghiệp vụ chủ động raise bằng {@code RAISE_APPLICATION_ERROR(-20000..-20999, 'text')}
 *     ({@code ORA-20xxx: text}): trả {@code text}, bỏ tiền tố {@code ORA-20xxx:} và call stack
 *     {@code ORA-06512} phía sau.</li>
 *     <li>Lỗi Oracle "thô" (có mã {@code ORA-/PLS-/TNS-...} khác, hoặc chuỗi kỹ thuật JDBC): trả
 *     {@link #GENERIC_MESSAGE}; chi tiết chỉ ghi log ở phía gọi.</li>
 *     <li>Các thông báo nghiệp vụ khác (do procedure gán {@code O_ERR_MSG} hoặc tầng Java ném): giữ nguyên.</li>
 * </ul>
 */
public final class OracleErrorMessages {

    /** Thông báo chung khi không được phép lộ chi tiết lỗi Database. */
    public static final String GENERIC_MESSAGE =
            "Có lỗi xử lý dữ liệu, vui lòng thử lại hoặc liên hệ quản trị viên.";

    /** {@code ORA-20000} .. {@code ORA-20999}: dải mã lỗi do ứng dụng tự định nghĩa. */
    private static final Pattern USER_DEFINED_ERROR =
            Pattern.compile("^\\s*ORA-20\\d{3}:\\s*(.*)$", Pattern.DOTALL);

    /** Mã lỗi Oracle / PL/SQL / Net / SQL*Plus. */
    private static final Pattern ORACLE_ERROR_CODE =
            Pattern.compile("\\b(?:ORA|PLS|TNS|SP2|OCI|NNE|DRG|LPX|KUP|JZN)-\\d{4,5}\\b");

    /** Dấu hiệu chuỗi kỹ thuật JDBC/Java lọt vào message. */
    private static final Pattern TECHNICAL_TEXT =
            Pattern.compile("(?i)java\\.sql\\.|oracle\\.jdbc|SQLException|SQLSyntaxError|SQLIntegrityConstraint"
                    + "|SQL state|\\bSQLSTATE\\b|bad SQL grammar|CallableStatementCallback");

    private OracleErrorMessages() {
    }

    /**
     * @return {@code true} nếu {@code message} là lỗi Oracle/JDBC thô không nên hiển thị cho người dùng
     * (tức {@link #toClientMessage} sẽ thay bằng {@link #GENERIC_MESSAGE}).
     */
    public static boolean isRawDatabaseError(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        Matcher userDefined = USER_DEFINED_ERROR.matcher(message);
        if (userDefined.matches()) {
            return extractUserDefinedText(userDefined.group(1)) == null;
        }
        return looksTechnical(message);
    }

    /**
     * Thông báo an toàn để trả về client.
     *
     * @param message thông báo gốc (có thể là {@code SQLERRM} từ procedure).
     * @return text nghiệp vụ, text của {@code ORA-20xxx} không kèm tiền tố, hoặc {@link #GENERIC_MESSAGE}.
     */
    public static String toClientMessage(String message) {
        if (message == null || message.isBlank()) {
            return GENERIC_MESSAGE;
        }
        Matcher userDefined = USER_DEFINED_ERROR.matcher(message);
        if (userDefined.matches()) {
            String text = extractUserDefinedText(userDefined.group(1));
            return text != null ? text : GENERIC_MESSAGE;
        }
        return looksTechnical(message) ? GENERIC_MESSAGE : message;
    }

    /**
     * Lấy phần text nghiệp vụ sau {@code ORA-20xxx:}; cắt tại dòng mới hoặc mã {@code ORA-} kế tiếp
     * (call stack {@code ORA-06512: at ...}).
     *
     * @return text đã trim, hoặc {@code null} nếu rỗng / vẫn còn dấu hiệu lỗi kỹ thuật.
     */
    private static String extractUserDefinedText(String rest) {
        String text = rest;
        int newline = indexOfLineBreak(text);
        if (newline >= 0) {
            text = text.substring(0, newline);
        }
        Matcher nextCode = ORACLE_ERROR_CODE.matcher(text);
        if (nextCode.find()) {
            text = text.substring(0, nextCode.start());
        }
        text = text.trim();
        if (text.isEmpty() || TECHNICAL_TEXT.matcher(text).find()) {
            return null;
        }
        return text;
    }

    private static boolean looksTechnical(String message) {
        return ORACLE_ERROR_CODE.matcher(message).find() || TECHNICAL_TEXT.matcher(message).find();
    }

    private static int indexOfLineBreak(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                return i;
            }
        }
        return -1;
    }
}
