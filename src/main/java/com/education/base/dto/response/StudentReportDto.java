package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

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

    /** Lop dang hoc (chi danh sach phan trang dien). */
    private List<StudentClassChipDto> classes;

    /** true neu co it nhat 1 lop dang hoc. */
    private Boolean enrolled;
}
