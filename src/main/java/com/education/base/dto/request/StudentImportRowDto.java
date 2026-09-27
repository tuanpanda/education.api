package com.education.base.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một dòng dữ liệu thô đọc từ file Excel import học sinh.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentImportRowDto {

    /** Số dòng trên Excel (1-based, gồm header). */
    private int rowNumber;

    private String studentCode;

    private String fullName;

    private String status;

    private String email;

    private String phone;

    private String classCode;

    private String dateOfBirth;

    private String parentName;

    private String address;

    private String note;
}
