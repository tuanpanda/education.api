package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cấu hình giới hạn một module ({@code SYS_FILE_MODULE_SETTINGS}); dùng cho cả GET và PUT.
 * Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileModuleSettingDto {

    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,49}", message = "Mã module không hợp lệ")
    private String moduleName;

    /** Byte; 1 .. multipart toàn cục (50 MB). */
    @NotNull
    @Positive
    private Long maxFileSizeBytes;

    /** NULL = không giới hạn. */
    @Positive
    private Integer maxFilesPerRef;

    /** Byte; NULL = không giới hạn. */
    @Positive
    private Long maxTotalBytesPerRef;

    /** Đuôi cho phép (chữ thường, không dấu chấm); rỗng/NULL = theo SafeFileType. Mỗi đuôi phải thuộc SafeFileType. */
    private List<@Pattern(regexp = "[a-z0-9]{1,10}", message = "Đuôi file không hợp lệ") String> allowedExtensions;

    @NotNull
    @Min(1)
    @Max(3650)
    private Integer trashRetentionDays;

    /** Tính vào quota học sinh. */
    private boolean countToStudentQuota;

    private boolean active;

    /** Chỉ đọc (bỏ qua khi PUT). */
    private LocalDateTime updatedAt;
    private String updatedBy;
}
