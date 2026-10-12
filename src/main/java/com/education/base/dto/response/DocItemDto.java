package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mục tài liệu ({@code DOC_ITEMS}) kèm file hiện tại. Xem/tải qua {@code file.viewUrl} / {@code file.downloadUrl}
 * ({@code /api/v1/files/view|download/{fileId}}, quyền do {@code LibraryFileModule} kiểm tra theo chia sẻ).
 * Hợp đồng Luồng 0 - chỉ Luồng D sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocItemDto {

    private Long id;
    /** NULL = mục ở gốc kho. */
    private Long folderId;
    private String folderName;
    private String title;
    private String description;
    @Builder.Default
    private List<String> tags = List.of();
    /** Bắt đầu từ 1, tăng mỗi lần thay file. */
    private Integer versionNo;
    private String versionNote;
    /** File hiện tại (module LIBRARY). */
    private FileResponseDto file;
    private Long ownerUserId;
    private String ownerUsername;
    private String ownerFullName;
    /** Chia sẻ trực tiếp + kế thừa từ thư mục cha (chỉ trả khi người dùng có quyền chia sẻ mục; ngược lại rỗng). */
    @Builder.Default
    private List<DocShareDto> shares = List.of();
    /** {@code true} nếu mục được chia sẻ cho ít nhất một lớp với {@code visibleToStudents=1} (trực tiếp hoặc kế thừa). */
    private boolean visibleToStudents;
    private DocPermissionsDto permissions;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
