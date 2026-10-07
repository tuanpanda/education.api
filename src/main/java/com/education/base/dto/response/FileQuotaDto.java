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
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Một quota ({@code SYS_FILE_QUOTAS}); dùng cho cả GET và PUT. Hợp đồng Luồng 0 - chỉ Luồng B sửa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileQuotaDto {

    /** NULL khi tạo mới trong PUT. */
    private Long id;

    @NotBlank
    @Pattern(regexp = "GLOBAL|STUDENT_DEFAULT|STUDENT|USER", message = "Phạm vi quota không hợp lệ")
    private String scopeType;

    /** Bắt buộc với STUDENT (EDU_STUDENTS.ID) / USER (SYS_USERS.ID); NULL với GLOBAL / STUDENT_DEFAULT. */
    private Long scopeId;

    /** Nhãn phạm vi (chỉ đọc), ví dụ "HS00012 - Nguyễn Văn A". */
    private String scopeLabel;

    @NotNull
    @Positive
    private Long maxBytes;

    @Min(1)
    @Max(100)
    @Builder.Default
    private Integer warnPercent = 80;

    @Size(max = 500)
    private String note;

    /** Chỉ đọc: dung lượng đang dùng trong phạm vi (byte). */
    private Long usedBytes;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
