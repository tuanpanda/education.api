package com.education.base.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementUpsertRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(max = 200, message = "Tiêu đề không được vượt quá 200 ký tự")
    private String title;

    @NotBlank(message = "Nội dung không được để trống")
    private String content;

    @NotBlank(message = "Phạm vi không được để trống")
    @Pattern(regexp = "ALL|CLASS", message = "Phạm vi phải là ALL hoặc CLASS")
    private String scopeType;

    /** Bắt buộc khi {@code scopeType = CLASS}. */
    private Long classId;

    @NotBlank(message = "Đối tượng không được để trống")
    @Pattern(regexp = "STUDENT|PARENT|ALL", message = "Đối tượng phải là STUDENT, PARENT hoặc ALL")
    private String audience;

    private Boolean pinned;

    private LocalDateTime expiresAt;
}
