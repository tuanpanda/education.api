package com.education.base.repository.projection;

import java.time.LocalDate;

/** Một buổi điểm danh tính phí ({@code PRESENT}/{@code LATE}) của lớp trong khoảng ngày. */
public record BillableAttendanceRow(Long attendanceId, Long studentId, LocalDate attendanceDate) {
}
