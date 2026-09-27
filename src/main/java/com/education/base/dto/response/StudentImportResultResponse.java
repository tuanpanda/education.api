package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả tổng hợp import học sinh từ Excel (chế độ linh hoạt: lưu dòng hợp lệ, báo dòng lỗi).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentImportResultResponse {

    private int totalRows;

    private int successCount;

    private int failureCount;

    @Builder.Default
    private List<ImportRowErrorDto> errors = new ArrayList<>();
}
