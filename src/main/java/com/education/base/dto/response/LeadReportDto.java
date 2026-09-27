package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một dòng trong danh sách nguồn tuyển sinh ({@code EDU_LEADS}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadReportDto {

    private Long id;
    private String leadCode;
    private String fullName;
    private String phone;
    private String email;

    /** {@code WEBSITE}, {@code FACEBOOK}, {@code ZALO}, {@code REFERRAL}, {@code WALK_IN}, {@code HOTLINE}, {@code OTHER}. */
    private String source;

    private String interestedSubject;

    /** {@code NEW}, {@code CONTACTED}, {@code QUALIFIED}, {@code CONVERTED}, {@code LOST}. */
    private String status;

    private Long assignedToId;
    private String assignedToName;

    /** ID học sinh được tạo ra khi lead đã chuyển đổi; {@code null} nếu chưa chuyển đổi. */
    private Long convertedStudentId;

    private String convertedStudentCode;

    private LocalDate nextFollowUpDate;
    private String note;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
