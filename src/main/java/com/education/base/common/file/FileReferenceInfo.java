package com.education.base.common.file;

/**
 * "Nơi sử dụng" của file (bản ghi nghiệp vụ được tham chiếu).
 *
 * @param moduleName  mã module.
 * @param referenceId ID bản ghi (NULL nếu không gắn).
 * @param label       nhãn hiển thị, ví dụ {@code "Học sinh HS00012 - Nguyễn Văn A"}.
 * @param uiRoute     route frontend để mở bản ghi (NULL nếu không có màn chi tiết), ví dụ {@code /students/list?id=12}.
 * @param state       {@link FileConstants.RefState}: ACTIVE / DELETED / NOT_FOUND / NONE.
 */
public record FileReferenceInfo(String moduleName, Long referenceId, String label, String uiRoute, String state) {

    public static FileReferenceInfo none(String moduleName) {
        return new FileReferenceInfo(moduleName, null, null, null, FileConstants.RefState.NONE);
    }

    public static FileReferenceInfo notFound(String moduleName, Long referenceId) {
        return new FileReferenceInfo(moduleName, referenceId, null, null, FileConstants.RefState.NOT_FOUND);
    }
}
