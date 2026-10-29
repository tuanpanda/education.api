package com.education.base.repository.projection;

import java.time.LocalDate;

/** Một buổi đã nằm trên phiếu còn hiệu lực (chưa hủy, chưa xóa) - dùng cho kiểm tra xung đột. */
public record FeeSessionConflictRow(Long attendanceId, Long studentId, LocalDate sessionDate, Long feeId,
                                    String feeCode) {
}
