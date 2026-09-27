package com.education.base.repository.custom.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Đọc kết quả REF CURSOR từ Map trả về của {@code SimpleJdbcCall}.
 * <p>
 * Spring JDBC trả về mỗi {@code SYS_REFCURSOR} dưới dạng {@code List} các dòng đã được
 * {@code RowMapper} chuyển đổi. Lớp này chuẩn hóa việc ép kiểu và xử lý trường hợp
 * cursor rỗng, tránh lặp lại logic tại từng Repository Custom Impl.
 */
final class ProcCursorReader {

    private ProcCursorReader() {
    }

    /**
     * Lấy danh sách dòng của một cursor trong kết quả Procedure.
     *
     * @param out            Map kết quả trả về từ {@code SimpleJdbcCall.execute(...)}.
     * @param cursorParamName tên tham số Out chứa cursor (ví dụ: {@code O_CURSOR}).
     * @param rowType        kiểu dữ liệu của mỗi dòng do {@code RowMapper} tạo ra.
     * @param <T>            kiểu dòng dữ liệu.
     * @return danh sách dòng dữ liệu; danh sách rỗng nếu cursor không có dữ liệu.
     */
    static <T> List<T> readCursor(Map<String, Object> out, String cursorParamName, Class<T> rowType) {
        Object value = out.get(cursorParamName);
        if (!(value instanceof List<?> rows)) {
            return List.of();
        }

        List<T> result = new ArrayList<>(rows.size());
        for (Object row : rows) {
            result.add(rowType.cast(row));
        }
        return result;
    }
}
