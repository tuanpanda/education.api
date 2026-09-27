package com.education.base.repository.custom.impl;

import com.education.base.dto.request.ClassFilterRequest;
import com.education.base.dto.response.ClassReportDto;
import com.education.base.dto.response.PageResponse;
import com.education.base.repository.base.OracleProcExecutor;
import com.education.base.repository.custom.ClassRepositoryCustom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oracle.jdbc.OracleTypes;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Triển khai {@link ClassRepositoryCustom} bằng Standalone Procedure
 * {@code PRC_SEARCH_CLASSES_PAGING}.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ClassRepositoryCustomImpl implements ClassRepositoryCustom {

    private static final String PROC_NAME = "PRC_SEARCH_CLASSES_PAGING";

    private static final RowMapper<ClassReportDto> CLASS_ROW_MAPPER = (rs, rowNum) -> ClassReportDto.builder()
            .id(rs.getLong("ID"))
            .classCode(rs.getString("CLASS_CODE"))
            .className(rs.getString("CLASS_NAME"))
            .subjectName(rs.getString("SUBJECT_NAME"))
            .gradeLevel(JdbcValueReaders.getInteger(rs, "GRADE_LEVEL"))
            .teacherId(JdbcValueReaders.getLong(rs, "TEACHER_ID"))
            .teacherName(rs.getString("TEACHER_NAME"))
            .roomName(rs.getString("ROOM_NAME"))
            .startDate(JdbcValueReaders.getLocalDate(rs, "START_DATE"))
            .endDate(JdbcValueReaders.getLocalDate(rs, "END_DATE"))
            .capacity(JdbcValueReaders.getInteger(rs, "CAPACITY"))
            .enrolledCount(JdbcValueReaders.getInteger(rs, "ENROLLED_COUNT"))
            .tuitionAmount(JdbcValueReaders.getBigDecimal(rs, "TUITION_AMOUNT"))
            .status(rs.getString("STATUS"))
            .createdAt(JdbcValueReaders.getLocalDateTime(rs, "CREATED_AT"))
            .updatedAt(JdbcValueReaders.getLocalDateTime(rs, "UPDATED_AT"))
            .build();

    private final OracleProcExecutor oracleProcExecutor;

    @Override
    public PageResponse<ClassReportDto> searchWithPaging(ClassFilterRequest filter) {
        ClassFilterRequest criteria = filter == null ? new ClassFilterRequest() : filter;
        int pageNo = criteria.resolvePageNo();
        int pageSize = criteria.resolvePageSize();

        SimpleJdbcCall call = oracleProcExecutor.createCall(PROC_NAME)
                .declareParameters(
                        new SqlParameter("P_KEYWORD", Types.VARCHAR),
                        new SqlParameter("P_STATUS", Types.VARCHAR),
                        new SqlParameter("P_TEACHER_ID", Types.NUMERIC),
                        new SqlParameter("P_PAGE_NO", Types.NUMERIC),
                        new SqlParameter("P_PAGE_SIZE", Types.NUMERIC),
                        new SqlOutParameter("O_DATA_CURSOR", OracleTypes.CURSOR, CLASS_ROW_MAPPER),
                        new SqlOutParameter("O_TOTAL_ROWS", Types.NUMERIC),
                        new SqlOutParameter("O_ERR_CODE", Types.VARCHAR),
                        new SqlOutParameter("O_ERR_MSG", Types.VARCHAR));

        Map<String, Object> in = new HashMap<>();
        in.put("P_KEYWORD", normalize(criteria.getKeyword()));
        in.put("P_STATUS", normalize(criteria.getStatus()));
        in.put("P_TEACHER_ID", criteria.getTeacherId());
        in.put("P_PAGE_NO", pageNo);
        in.put("P_PAGE_SIZE", pageSize);

        Map<String, Object> out = call.execute(in);
        oracleProcExecutor.validateResult(out);

        List<ClassReportDto> content = ProcCursorReader.readCursor(out, "O_DATA_CURSOR", ClassReportDto.class);
        long totalRows = toLong(out.get("O_TOTAL_ROWS"));

        log.debug("{} trả về {}/{} lớp (trang {}, cỡ {}, keyword='{}', status='{}', teacherId={})",
                PROC_NAME, content.size(), totalRows, pageNo, pageSize,
                criteria.getKeyword(), criteria.getStatus(), criteria.getTeacherId());

        return PageResponse.of(content, pageNo, pageSize, totalRows);
    }

    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
