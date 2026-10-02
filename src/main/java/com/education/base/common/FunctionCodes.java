package com.education.base.common;

import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mã chức năng ({@code SYS_FUNCTIONS.FUNCTION_CODE}) chuẩn và thứ tự hiển thị trên ma trận phân quyền.
 */
@UtilityClass
public class FunctionCodes {

    public static final String VIEW = "VIEW";

    /** Hủy khoản học phí (MENU_TUITION_FEE, Stream A / V14_1). */
    public static final String CANCEL = "CANCEL";

    /** Hủy (vô hiệu) một giao dịch ghi nhầm (MENU_PAYMENT_HISTORY, Stream B / V14_2). */
    public static final String VOID = "VOID";

    /** Hoàn tiền học phí (MENU_PAYMENT_HISTORY, Stream B / V14_2). */
    public static final String REFUND = "REFUND";

    /** Chức năng chuẩn theo thứ tự hiển thị; mã khác xếp sau theo bảng chữ cái. */
    public static final List<String> STANDARD = List.of("VIEW", "CREATE", "UPDATE", "DELETE", "EXPORT", "IMPORT");

    /** Chức năng mặc định khi tạo menu loại {@code DIR}. */
    public static final List<String> DEFAULT_FOR_DIR = List.of("VIEW");

    /** Chức năng mặc định khi tạo menu loại {@code MENU}. */
    public static final List<String> DEFAULT_FOR_MENU = List.of("VIEW", "CREATE", "UPDATE", "DELETE");

    /** Tên hiển thị mặc định (khớp seed V1). */
    public static final Map<String, String> DEFAULT_NAMES = defaultNames();

    public static final Comparator<String> DISPLAY_ORDER = Comparator
            .comparingInt(FunctionCodes::rank)
            .thenComparing(Comparator.naturalOrder());

    /** Tách chuỗi {@code ALLOWED_FUNCTIONS} dạng {@code VIEW,CREATE} thành danh sách mã. */
    public static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    /** Ghép danh sách mã chức năng thành chuỗi {@code ALLOWED_FUNCTIONS} theo thứ tự hiển thị. */
    public static String joinCsv(Collection<String> codes) {
        return codes.stream().distinct().sorted(DISPLAY_ORDER).collect(Collectors.joining(","));
    }

    public static String defaultName(String code) {
        return DEFAULT_NAMES.getOrDefault(code, code);
    }

    private static int rank(String code) {
        int index = STANDARD.indexOf(code);
        return index < 0 ? STANDARD.size() : index;
    }

    private static Map<String, String> defaultNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("VIEW", "Xem danh sách");
        names.put("CREATE", "Thêm mới");
        names.put("UPDATE", "Cập nhật");
        names.put("DELETE", "Xóa");
        names.put("EXPORT", "Xuất dữ liệu");
        names.put("IMPORT", "Nhập dữ liệu");
        names.put("GEN_QR", "Sinh mã VietQR");
        names.put("CONVERT", "Chuyển thành học sinh");
        names.put("APPROVE", "Xác nhận giao dịch");
        names.put("UPLOAD", "Tải tài liệu lên");
        names.put("DOWNLOAD", "Tải tài liệu về");
        names.put("CONFIG_SCHEDULE", "Cấu hình lịch tuần");
        names.put("GENERATE_SESSIONS", "Sinh buổi học");
        names.put("CANCEL_SESSION", "Hủy buổi học");
        names.put(CANCEL, "Hủy khoản phí");
        names.put(VOID, "Hủy giao dịch");
        names.put(REFUND, "Hoàn tiền");
        return Map.copyOf(names);
    }
}
