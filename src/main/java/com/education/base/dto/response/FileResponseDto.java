package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO trả về thông tin file kèm link để xem/tải trực tiếp qua API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileResponseDto {

    private Long id;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private String moduleName;
    private Long referenceId;

    /**
     * Đường dẫn API để xem trực tiếp file trên trình duyệt (inline).
     */
    private String viewUrl;

    /**
     * Đường dẫn API để tải file về máy (attachment).
     */
    private String downloadUrl;

    private LocalDateTime createdAt;
}
