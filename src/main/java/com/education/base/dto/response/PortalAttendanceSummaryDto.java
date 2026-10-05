package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tổng hợp điểm danh của học sinh đang đăng nhập (một lớp hoặc mọi lớp đang học).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalAttendanceSummaryDto {

    /** {@code null} khi tổng hợp mọi lớp đang ghi danh. */
    private Long classId;

    private String classCode;

    private String className;

    private long presentCount;

    private long absentCount;

    private long lateCount;

    private long excusedCount;

    private long totalCount;
}
