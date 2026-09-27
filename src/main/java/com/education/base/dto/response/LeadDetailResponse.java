package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Chi tiết một lead, kèm tài liệu đính kèm (đơn đăng ký, giấy tờ) module {@code LEAD}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadDetailResponse {

    private Long id;
    private String leadCode;
    private String fullName;
    private String phone;
    private String email;
    private String source;
    private String interestedSubject;
    private String status;

    private Long assignedToId;
    private String assignedToName;

    private Long convertedStudentId;
    private String convertedStudentCode;
    private String convertedStudentName;

    private LocalDate nextFollowUpDate;
    private String note;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    @Builder.Default
    private List<FileResponseDto> attachments = new ArrayList<>();
}
