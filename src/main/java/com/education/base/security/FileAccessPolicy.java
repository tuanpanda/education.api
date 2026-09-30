package com.education.base.security;

import com.education.base.common.DomainConstants;
import com.education.base.entity.FileEntity;
import com.education.base.exception.ForbiddenException;
import com.education.base.exception.OracleBusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phân quyền file đính kèm theo nghiệp vụ sở hữu ({@code MODULE_NAME}/{@code REFERENCE_ID}).
 * <p>
 * Quyền trên endpoint {@code /api/v1/files/**} chỉ là "cổng" thô; quyền thực sự phụ thuộc Module
 * lưu trong metadata của file (không lấy từ tham số client khi xem/tải):
 * <table>
 *     <tr><th>Module</th><th>Xem / tải / liệt kê</th><th>Upload</th></tr>
 *     <tr><td>STUDENT</td><td>{@link Permissions#STUDENT_VIEW}</td>
 *         <td>{@link Permissions#STUDENT_CREATE} / {@link Permissions#STUDENT_UPDATE}</td></tr>
 *     <tr><td>STUDENT/IMPORT (file Excel import)</td>
 *         <td>{@link Permissions#STUDENT_IMPORT} / {@link Permissions#STUDENT_CREATE}</td><td>(chỉ qua API import)</td></tr>
 *     <tr><td>CLASS</td><td>{@link Permissions#CLASS_VIEW}</td>
 *         <td>{@link Permissions#CLASS_CREATE} / {@link Permissions#CLASS_UPDATE}</td></tr>
 *     <tr><td>LEAD</td><td>{@link Permissions#LEAD_VIEW}</td>
 *         <td>{@link Permissions#LEAD_CREATE} / {@link Permissions#LEAD_UPDATE}</td></tr>
 *     <tr><td>TUITION</td><td>{@link Permissions#TUITION_FEE_VIEW}</td>
 *         <td>{@link Permissions#TUITION_FEE_CREATE} / {@link Permissions#TUITION_FEE_UPDATE}</td></tr>
 *     <tr><td>COMMON (Kho tài liệu) và module khác</td>
 *         <td>{@link Permissions#FILE_VIEW} (xem) / {@link Permissions#FILE_DOWNLOAD} (tải)</td>
 *         <td>{@link Permissions#FILE_UPLOAD} (chỉ COMMON)</td></tr>
 * </table>
 * {@link Permissions#ADMIN_ROLE} luôn được phép.
 */
@Component
public class FileAccessPolicy {

    public static final String FORBIDDEN_CODE = "FILE_FORBIDDEN";

    /** Tiền tố đường dẫn tương đối của file Excel import học sinh ({@code STUDENT/IMPORT/YYYY/MM/...}). */
    static final String STUDENT_IMPORT_PATH_PREFIX = DomainConstants.Module.STUDENT + "/IMPORT/";

    public enum Action { VIEW, DOWNLOAD }

    private record ModuleRule(List<String> read, List<String> upload, boolean referenceRequired) {
    }

    private static final Map<String, ModuleRule> RULES = Map.of(
            DomainConstants.Module.STUDENT, new ModuleRule(
                    List.of(Permissions.STUDENT_VIEW),
                    List.of(Permissions.STUDENT_CREATE, Permissions.STUDENT_UPDATE), true),
            DomainConstants.Module.CLASS, new ModuleRule(
                    List.of(Permissions.CLASS_VIEW),
                    List.of(Permissions.CLASS_CREATE, Permissions.CLASS_UPDATE), true),
            DomainConstants.Module.LEAD, new ModuleRule(
                    List.of(Permissions.LEAD_VIEW),
                    List.of(Permissions.LEAD_CREATE, Permissions.LEAD_UPDATE), true),
            DomainConstants.Module.TUITION, new ModuleRule(
                    List.of(Permissions.TUITION_FEE_VIEW),
                    List.of(Permissions.TUITION_FEE_CREATE, Permissions.TUITION_FEE_UPDATE), true),
            DomainConstants.Module.COMMON, new ModuleRule(
                    List.of(Permissions.FILE_VIEW),
                    List.of(Permissions.FILE_UPLOAD), false));

    private static final List<String> STUDENT_IMPORT_READ =
            List.of(Permissions.STUDENT_IMPORT, Permissions.STUDENT_CREATE);

    /** Kiểm tra quyền xem/tải một file đã lưu, dựa trên Module + đường dẫn trong metadata. */
    public void checkRead(FileEntity file, Action action) {
        AuthUserPrincipal user = SecurityUtils.requireCurrentUser();
        if (user.isAdmin()) {
            return;
        }
        String module = normalize(file.getModuleName());
        List<String> required;
        if (DomainConstants.Module.STUDENT.equals(module) && isStudentImportFile(file)) {
            required = STUDENT_IMPORT_READ;
        } else {
            required = readPermissions(module, action);
        }
        requireAny(user, required);
    }

    /** Kiểm tra quyền liệt kê file theo {@code moduleName}/{@code referenceId}. */
    public void checkList(String moduleName) {
        AuthUserPrincipal user = SecurityUtils.requireCurrentUser();
        if (user.isAdmin()) {
            return;
        }
        requireAny(user, readPermissions(normalize(moduleName), Action.VIEW));
    }

    /**
     * Kiểm tra quyền upload qua {@code /api/v1/files/upload}: Module phải thuộc allow-list,
     * Module nghiệp vụ bắt buộc có {@code referenceId}.
     */
    public void checkUpload(String moduleName, Long referenceId) {
        String module = normalize(moduleName);
        ModuleRule rule = RULES.get(module);
        if (rule == null) {
            throw new OracleBusinessException("INVALID_MODULE",
                    "Module '" + module + "' không hỗ trợ upload file.");
        }
        if (rule.referenceRequired() && referenceId == null) {
            throw new OracleBusinessException("FILE_REFERENCE_REQUIRED",
                    "Vui lòng chọn bản ghi (referenceId) để đính kèm file cho module " + module + ".");
        }
        AuthUserPrincipal user = SecurityUtils.requireCurrentUser();
        if (user.isAdmin()) {
            return;
        }
        requireAny(user, rule.upload());
    }

    private static List<String> readPermissions(String module, Action action) {
        ModuleRule rule = RULES.get(module);
        if (rule == null || DomainConstants.Module.COMMON.equals(module)) {
            return action == Action.DOWNLOAD
                    ? List.of(Permissions.FILE_DOWNLOAD)
                    : List.of(Permissions.FILE_VIEW);
        }
        return rule.read();
    }

    private static boolean isStudentImportFile(FileEntity file) {
        String path = file.getFilePath();
        return path != null && path.replace('\\', '/').toUpperCase(Locale.ROOT).startsWith(STUDENT_IMPORT_PATH_PREFIX);
    }

    private static void requireAny(AuthUserPrincipal user, List<String> permissions) {
        for (String permission : permissions) {
            if (user.hasPermission(permission)) {
                return;
            }
        }
        throw new ForbiddenException(FORBIDDEN_CODE, "Bạn không có quyền truy cập file của chức năng này.");
    }

    private static String normalize(String moduleName) {
        return moduleName == null || moduleName.isBlank()
                ? DomainConstants.Module.COMMON
                : moduleName.trim().toUpperCase(Locale.ROOT);
    }
}
