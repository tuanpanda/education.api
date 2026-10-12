package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Một chia sẻ ({@code DOC_SHARES}). Đúng một trong {@code folderId} / {@code itemId} khác NULL.
 * Hợp đồng Luồng 0 - chỉ Luồng D sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocShareDto {

    private Long id;
    private Long folderId;
    private Long itemId;
    /** ALL_STAFF / ROLE / CLASS. */
    private String targetType;
    /** {@code SYS_ROLES.ID} (ROLE) hoặc {@code EDU_CLASSES.ID} (CLASS); NULL với ALL_STAFF. */
    private Long targetId;
    /** Nhãn đích, ví dụ "Tất cả nhân viên", "Giảng viên", "LH0012 - Tiếng Anh 6A". */
    private String targetLabel;
    /** Chỉ với CLASS: học sinh đang học lớp được xem trên cổng học sinh (GĐ3 - chưa có endpoint). */
    private boolean visibleToStudents;
    /** NULL = không hết hạn. */
    private LocalDateTime expiresAt;
    /** {@code true} nếu chia sẻ đến từ thư mục cha (chỉ đọc trên mục / thư mục con). */
    private boolean inherited;
    /** Thư mục gốc của chia sẻ kế thừa. */
    private Long inheritedFromFolderId;
    private String inheritedFromFolderName;
    private LocalDateTime createdAt;
    private String createdBy;
}
