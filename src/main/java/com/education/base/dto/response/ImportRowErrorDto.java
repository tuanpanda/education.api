package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chi tiết một lỗi trên dòng Excel khi import học sinh.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportRowErrorDto {

    private int rowNumber;

    private String columnName;

    private String studentCode;

    private String errorMessage;
}
