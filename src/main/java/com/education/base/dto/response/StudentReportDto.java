package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO báo cáo/hiển thị thông tin học sinh, dùng cho kết quả tìm kiếm qua Oracle Procedure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentReportDto {

    private Long id;
    private String studentCode;
    private String fullName;
    private String email;
    private String status;
    private LocalDate dateOfBirth;
    private String parentName;
    private String phone;
    private String address;
    private String note;
}
