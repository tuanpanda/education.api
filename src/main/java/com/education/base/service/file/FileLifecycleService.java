package com.education.base.service.file;

import com.education.base.entity.FileEntity;

/**
 * Vòng đời của một bản ghi {@code SYS_ATTACHED_FILES}: thùng rác (xoá mềm), khôi phục, xoá vĩnh viễn.
 * <p>
 * Luồng A cài đặt (cùng transaction với metadata, đồng bộ {@code IS_DELETED} và {@code STATUS},
 * ghi {@code DELETED_AT/BY}, {@code DELETE_REASON}, {@code PURGE_AFTER}); Luồng B (quản trị file, job dọn dẹp)
 * và Luồng D (thay file của tài liệu, xoá tài liệu) chỉ gọi qua interface này, không tự cập nhật cột trạng thái.
 * Kiểm tra quyền nằm ở tầng gọi; các hàm dưới đây chỉ kiểm tra trạng thái.
 * <p>
 * Chỉ Luồng A sửa file này.
 */
public interface FileLifecycleService {

    /**
     * Chuyển file {@code ACTIVE} vào thùng rác: {@code STATUS=DELETED}, {@code IS_DELETED=1},
     * {@code PURGE_AFTER = now + app.storage.retention.trash-days}. Blob vật lý giữ nguyên.
     *
     * @param fileId ID file.
     * @param reason lý do (tối đa {@code FileConstants.MAX_REASON_LENGTH}), có thể {@code null}.
     * @return bản ghi sau cập nhật.
     * @throws com.education.base.exception.OracleBusinessException {@code FILE_NOT_FOUND} hoặc
     *         {@code FILE_INVALID_STATE} khi file không ở trạng thái {@code ACTIVE}.
     */
    FileEntity softDelete(Long fileId, String reason);

    /**
     * Khôi phục file {@code DELETED} về {@code ACTIVE}. Từ chối khi blob không còn
     * ({@code FILE_BLOB_MISSING}) hoặc bản ghi nghiệp vụ tham chiếu đã bị xoá ({@code FILE_REFERENCE_INACTIVE}).
     */
    FileEntity restore(Long fileId);

    /**
     * Xoá vĩnh viễn file đang ở thùng rác: xoá blob (idempotent) rồi đặt {@code STATUS=PURGED},
     * {@code PURGED_AT=now}; bản ghi metadata được giữ để tra cứu nhật ký.
     *
     * @throws com.education.base.exception.OracleBusinessException {@code FILE_INVALID_STATE} khi file
     *         chưa ở thùng rác.
     */
    FileEntity purge(Long fileId, String reason);
}
