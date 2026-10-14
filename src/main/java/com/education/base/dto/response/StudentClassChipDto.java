package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lop hoc sinh dang hoc (ghi danh ENROLLED, lop chua xoa, khong CLOSED/CANCELLED) - hien tren danh sach hoc sinh.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentClassChipDto {
    private Long classId;
    private String classCode;
    private String className;
}