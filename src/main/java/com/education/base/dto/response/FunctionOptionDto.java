package com.education.base.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một chức năng (VIEW, CREATE...) - cột trong ma trận phân quyền hoặc mục trong danh mục chức năng.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FunctionOptionDto {

    /** ID bản ghi {@code SYS_FUNCTIONS} (chỉ có khi thuộc một menu cụ thể). */
    private Long id;

    private String code;

    private String name;

    /** Số menu đang dùng chức năng này (chỉ có ở danh mục chức năng). */
    private Integer menuCount;
}
