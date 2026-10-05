package com.education.base.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementFilterRequest {

    @Size(max = 100)
    private String keyword;

    @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED", message = "Trạng thái không hợp lệ")
    private String status;

    @Pattern(regexp = "ALL|CLASS", message = "Phạm vi không hợp lệ")
    private String scopeType;

    private Long classId;

    @Pattern(regexp = "STUDENT|PARENT|ALL", message = "Đối tượng không hợp lệ")
    private String audience;

    /** Số trang bắt đầu từ 1 (cùng quy ước các màn hình danh sách khác). */
    @Min(value = 1, message = "Số trang phải lớn hơn hoặc bằng 1")
    @Builder.Default
    private int pageNo = 1;

    @Min(1)
    @Max(100)
    @Builder.Default
    private int pageSize = 20;
}
