package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Kết quả thao tác hàng loạt (xoá / khôi phục). Mỗi ID xử lý độc lập; lỗi một ID không làm hỏng ID khác.
 * Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileBulkResultDto {

    @Builder.Default
    private List<Long> succeeded = List.of();
    @Builder.Default
    private List<Failure> failed = List.of();

    /** Một ID lỗi: mã lỗi ({@code FILE_NOT_FOUND}, {@code FILE_INVALID_STATE}...) + thông báo tiếng Việt. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Failure {
        private Long id;
        private String errorCode;
        private String message;
    }
}
