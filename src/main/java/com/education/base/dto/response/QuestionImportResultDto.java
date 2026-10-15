package com.education.base.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GĐ4 - kết quả nhập Excel câu hỏi. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionImportResultDto {

    private Boolean dryRun;
    private Integer totalRows;
    private Integer imported;
    private List<RowError> errors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowError {
        private Integer row;
        private String field;
        private String code;
        private String message;
    }
}
