package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Giới hạn upload của một module ({@code GET /api/v1/files/policies?module=}) để frontend kiểm tra trước khi gửi.
 * Server vẫn kiểm tra lại. Hợp đồng Luồng 0 - chỉ Luồng A sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileUploadPolicyDto {

    private String moduleName;

    /** Dung lượng tối đa một file (byte). */
    private Long maxFileSizeBytes;

    /** Số file tối đa trên một bản ghi (NULL = không giới hạn). */
    private Integer maxFilesPerRef;

    /** Tổng dung lượng tối đa trên một bản ghi (byte, NULL = không giới hạn). */
    private Long maxTotalBytesPerRef;

    /** Đuôi cho phép (chữ thường, không dấu chấm), đã giao với allow-list {@code SafeFileType}. */
    @Builder.Default
    private List<String> allowedExtensions = List.of();

    /** Đuôi được xem trực tiếp (inline) - hiện tại ảnh + pdf. */
    @Builder.Default
    private List<String> inlineExtensions = List.of();

    /** {@code true} nếu upload bắt buộc có {@code referenceId}. */
    private boolean referenceRequired;
}
