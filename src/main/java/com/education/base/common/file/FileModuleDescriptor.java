package com.education.base.common.file;

import com.education.base.entity.FileEntity;
import com.education.base.security.AuthUserPrincipal;

import java.util.Optional;

/**
 * Khai báo một module file (registry fail-closed - QĐ plan mục 4.2). Mỗi module 1 bean Spring, được
 * {@code FileModuleRegistry} thu thập theo {@link #code()}. Module không có descriptor -> từ chối upload/đọc
 * (trừ admin / {@code MENU_FILE_MANAGER:*}).
 * <p>
 * Bean hiện có/dự kiến: {@code StudentFileModule}, {@code StudentImportFileModule}, {@code ClassFileModule},
 * {@code LeadFileModule}, {@code TuitionFileModule}, {@code CommonFileModule} (Luồng A);
 * {@code LibraryFileModule} (Luồng D, module {@code LIBRARY}); GĐ2: Assignment/Submission/Feedback.
 * <p>
 * Kiểm tra quyền chung (admin luôn qua, file DELETED/PURGED/PENDING, tham chiếu đã xoá - QĐ-F9, trạng thái quét)
 * do {@code FileAccessPolicy} làm TRƯỚC khi gọi descriptor; descriptor chỉ trả lời luật nghiệp vụ của module.
 * <p>
 * Hợp đồng Luồng 0. Chỉ Luồng A sửa interface này.
 */
public interface FileModuleDescriptor {

    /** Mã module ({@link FileConstants.Module}), viết HOA. */
    String code();

    /** Người dùng (đã đăng nhập, không phải admin) có được xem/tải file này của module không. */
    boolean canRead(AuthUserPrincipal user, FileEntity file);

    /** Có được upload file gắn vào bản ghi {@code referenceId} (có thể NULL nếu module cho phép) không. */
    boolean canUpload(AuthUserPrincipal user, Long referenceId);

    /** Có được xoá mềm file này qua {@code DELETE /api/v1/files/{id}} không. */
    boolean canDelete(AuthUserPrincipal user, FileEntity file);

    /** {@code true} nếu upload của module bắt buộc có {@code referenceId}. */
    default boolean referenceRequired() {
        return true;
    }

    /**
     * {@code false} nếu module chỉ nhận upload qua API nghiệp vụ riêng (ví dụ {@code LIBRARY} qua
     * {@code /api/v1/documents/items}, {@code STUDENT_IMPORT} qua import) - khi đó {@code POST /api/v1/files/upload}
     * trả {@code INVALID_MODULE}.
     */
    default boolean allowGenericUpload() {
        return true;
    }

    /**
     * Ném {@code OracleBusinessException} {@code FILE_REFERENCE_NOT_FOUND} / {@code FILE_REFERENCE_INACTIVE} nếu
     * bản ghi tham chiếu không tồn tại / đã xoá mềm / không hoạt động (sửa S3).
     */
    void assertReferenceUsable(Long referenceId);

    /** "Nơi sử dụng" cho màn quản trị: nhãn, route UI, trạng thái bản ghi. */
    FileReferenceInfo describeReference(Long referenceId);

    /** Học sinh sở hữu (tính quota) - rỗng nếu module không tính quota học sinh. */
    default Optional<Long> ownerStudentId(Long referenceId) {
        return Optional.empty();
    }
}
