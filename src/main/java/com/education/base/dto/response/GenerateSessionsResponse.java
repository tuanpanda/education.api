package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả sinh buổi học: số tạo mới, số bỏ qua vì đã tồn tại, danh sách buổi vừa tạo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateSessionsResponse {

    private Long classId;

    private LocalDate fromDate;

    private LocalDate toDate;

    private int createdCount;

    private int skippedCount;

    @Builder.Default
    private List<TimetableItemDto> sessions = new ArrayList<>();
}
