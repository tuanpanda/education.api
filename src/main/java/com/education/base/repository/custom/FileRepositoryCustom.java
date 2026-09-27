package com.education.base.repository.custom;

import com.education.base.entity.FileEntity;

import java.util.List;

/**
 * Truy vấn danh sách file đính kèm, xử lý qua Standalone Procedure (Spring JDBC).
 */
public interface FileRepositoryCustom {

    /**
     * Lấy danh sách file đính kèm theo Module nghiệp vụ và ID tham chiếu,
     * gọi Standalone Procedure {@code PRC_GET_FILES_BY_REF}.
     * <p>
     * Procedure chỉ trả về phần metadata cần cho danh sách (không gồm {@code FILE_PATH}
     * và {@code STORED_NAME}), nên các trường đó không được set trong kết quả.
     *
     * @param moduleName  tên Module nghiệp vụ (ví dụ: {@code STUDENT}).
     * @param referenceId ID bản ghi nghiệp vụ liên quan.
     * @return danh sách file chưa bị xóa mềm.
     */
    List<FileEntity> getFilesByRef(String moduleName, Long referenceId);
}
