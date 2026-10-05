package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Lớp giảng viên / nhân viên được phép gắn thông báo phạm vi CLASS. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementClassOptionDto {

    private Long id;
    private String classCode;
    private String className;
}