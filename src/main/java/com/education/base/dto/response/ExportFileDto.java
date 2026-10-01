package com.education.base.dto.response;

/**
 * File sinh ra để tải về (Excel), kèm tên file gợi ý cho {@code Content-Disposition}.
 *
 * @param fileName    tên file, ví dụ {@code tong_hop_tai_chinh_20260101_20261002.xlsx}.
 * @param contentType MIME type.
 * @param content     nội dung file.
 */
public record ExportFileDto(String fileName, String contentType, byte[] content) {
}
