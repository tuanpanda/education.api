package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Kết quả phân trang dùng chung cho các API tìm kiếm.
 *
 * @param <T> kiểu phần tử trong danh sách dữ liệu.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponse<T> {

    /** Dữ liệu của trang hiện tại. */
    @Builder.Default
    private List<T> content = List.of();

    private int pageNo;

    private int pageSize;

    /** Tổng số dòng khớp điều kiện tìm kiếm (không chỉ riêng trang hiện tại). */
    private long totalRows;

    private int totalPages;

    /**
     * Tạo {@link PageResponse} và tự tính {@code totalPages} từ {@code totalRows} và {@code pageSize}.
     */
    public static <T> PageResponse<T> of(List<T> content, int pageNo, int pageSize, long totalRows) {
        int safePageSize = Math.max(pageSize, 1);
        int totalPages = (int) ((totalRows + safePageSize - 1) / safePageSize);
        return PageResponse.<T>builder()
                .content(content == null ? List.of() : content)
                .pageNo(pageNo)
                .pageSize(safePageSize)
                .totalRows(totalRows)
                .totalPages(totalPages)
                .build();
    }
}
