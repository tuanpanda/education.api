package com.education.base.dto.request;

import com.education.base.common.document.DocumentConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tìm mục tài liệu người dùng nhìn thấy ({@code GET /api/v1/documents/items/search}, query string).
 * Phân trang 1-based. Hợp đồng Luồng 0 - chỉ Luồng D sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocItemFilterRequest {

    /** NULL + {@code includeSubfolders=false} = mục ở gốc; NULL + true = toàn kho. */
    private Long folderId;

    @Builder.Default
    private boolean includeSubfolders = false;

    /** Tìm gần đúng theo tiêu đề / mô tả / tên file. */
    @Size(max = 100, message = "Từ khóa không được vượt quá 100 ký tự")
    private String q;

    /** Lọc theo một thẻ (khớp chính xác sau chuẩn hoá). */
    @Size(max = DocumentConstants.TAG_MAX)
    private String tag;

    @Pattern(regexp = "[A-Za-z0-9]{1,10}", message = "Đuôi file không hợp lệ")
    private String ext;

    /** Chỉ mục do tôi tạo. */
    @Builder.Default
    private boolean mine = false;

    /** Chỉ mục được chia sẻ (trực tiếp hoặc kế thừa) cho lớp này. */
    private Long classId;

    @Min(value = 1, message = "Số trang phải lớn hơn hoặc bằng 1")
    @Builder.Default
    private int pageNo = 1;

    @Min(1)
    @Max(DocumentConstants.MAX_PAGE_SIZE)
    @Builder.Default
    private int pageSize = DocumentConstants.DEFAULT_PAGE_SIZE;

    @Pattern(regexp = "title|createdAt|updatedAt|fileSize", message = "Trường sắp xếp không hợp lệ")
    @Builder.Default
    private String sortBy = "title";

    @Pattern(regexp = "(?i)asc|desc", message = "Chiều sắp xếp chỉ nhận asc/desc")
    @Builder.Default
    private String sortDir = "asc";
}
