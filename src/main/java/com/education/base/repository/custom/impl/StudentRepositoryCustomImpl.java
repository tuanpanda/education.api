package com.education.base.repository.custom.impl;

import com.education.base.common.DomainConstants;
import com.education.base.dto.request.StudentFilterRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentReportDto;
import com.education.base.repository.base.OracleProcExecutor;
import com.education.base.repository.custom.StudentRepositoryCustom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oracle.jdbc.OracleTypes;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Triển khai {@link StudentRepositoryCustom} bằng Standalone Procedure của Oracle:
 * <ul>
 *     <li>{@code PRC_SEARCH_STUDENTS(P_KEYWORD, O_CURSOR, O_ERR_CODE, O_ERR_MSG)}</li>
 *     <li>{@code PRC_SEARCH_STUDENTS_PAGING(P_KEYWORD, P_STATUS, P_PAGE_NO, P_PAGE_SIZE,
 *         O_DATA_CURSOR, O_TOTAL_ROWS, O_ERR_CODE, O_ERR_MSG)}</li>
 * </ul>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class StudentRepositoryCustomImpl implements StudentRepositoryCustom {

    private static final String PROC_SEARCH = "PRC_SEARCH_STUDENTS";
    private static final String PROC_SEARCH_PAGING = "PRC_SEARCH_STUDENTS_PAGING";

    /**
     * {@code PRC_SEARCH_STUDENTS} dùng alias không dấu gạch dưới ({@code STUDENT_CODE AS studentCode}),
     * nên Oracle trả về nhãn cột dạng {@code STUDENTCODE}, {@code FULLNAME}.
     */
    private static final RowMapper<StudentReportDto> STUDENT_ROW_MAPPER = (rs, rowNum) -> StudentReportDto.builder()
            .id(rs.getLong("ID"))
            .studentCode(rs.getString("STUDENTCODE"))
            .fullName(rs.getString("FULLNAME"))
            .email(rs.getString("EMAIL"))
            .status(rs.getString("STATUS"))
            .dateOfBirth(readLocalDate(rs, "DATEOFBIRTH"))
            .parentName(rs.getString("PARENTNAME"))
            .phone(rs.getString("PHONE"))
            .address(rs.getString("ADDRESS"))
            .note(rs.getString("NOTE"))
            .build();

    /**
     * {@code PRC_SEARCH_STUDENTS_PAGING} trả về đúng tên cột gốc của bảng nên đọc trực tiếp
     * theo {@code STUDENT_CODE}, {@code FULL_NAME}.
     */
    private static final RowMapper<StudentReportDto> STUDENT_PAGING_ROW_MAPPER =
            (rs, rowNum) -> StudentReportDto.builder()
                    .id(rs.getLong("ID"))
                    .studentCode(rs.getString("STUDENT_CODE"))
                    .fullName(rs.getString("FULL_NAME"))
                    .email(rs.getString("EMAIL"))
                    .status(rs.getString("STATUS"))
                    .dateOfBirth(readLocalDate(rs, "DATE_OF_BIRTH"))
                    .parentName(rs.getString("PARENT_NAME"))
                    .phone(rs.getString("PHONE"))
                    .address(rs.getString("ADDRESS"))
                    .note(rs.getString("NOTE"))
                    .build();

    private final OracleProcExecutor oracleProcExecutor;

    @Override
    public List<StudentReportDto> searchStudents(String keyword) {
        SimpleJdbcCall call = oracleProcExecutor.createCall(PROC_SEARCH)
                .declareParameters(
                        new SqlParameter("P_KEYWORD", Types.VARCHAR),
                        new SqlOutParameter("O_CURSOR", OracleTypes.CURSOR, STUDENT_ROW_MAPPER),
                        new SqlOutParameter("O_ERR_CODE", Types.VARCHAR),
                        new SqlOutParameter("O_ERR_MSG", Types.VARCHAR));

        Map<String, Object> out = call.execute(Map.of("P_KEYWORD", keyword == null ? "" : keyword));
        oracleProcExecutor.validateResult(out);

        List<StudentReportDto> students = ProcCursorReader.readCursor(out, "O_CURSOR", StudentReportDto.class);
        students = students.stream()
                .filter(row -> DomainConstants.STUDENT_STATUS_ACTIVE.equals(row.getStatus()))
                .toList();
        log.debug("{} trả về {} học sinh với keyword='{}'", PROC_SEARCH, students.size(), keyword);
        return students;
    }

    @Override
    public PageResponse<StudentReportDto> searchWithPaging(StudentFilterRequest filter) {
        StudentFilterRequest criteria = filter == null ? new StudentFilterRequest() : filter;
        int pageNo = criteria.resolvePageNo();
        int pageSize = criteria.resolvePageSize();

        SimpleJdbcCall call = oracleProcExecutor.createCall(PROC_SEARCH_PAGING)
                .declareParameters(
                        new SqlParameter("P_KEYWORD", Types.VARCHAR),
                        new SqlParameter("P_STATUS", Types.VARCHAR),
                        new SqlParameter("P_PAGE_NO", Types.NUMERIC),
                        new SqlParameter("P_PAGE_SIZE", Types.NUMERIC),
                        new SqlOutParameter("O_DATA_CURSOR", OracleTypes.CURSOR, STUDENT_PAGING_ROW_MAPPER),
                        new SqlOutParameter("O_TOTAL_ROWS", Types.NUMERIC),
                        new SqlOutParameter("O_ERR_CODE", Types.VARCHAR),
                        new SqlOutParameter("O_ERR_MSG", Types.VARCHAR));

        Map<String, Object> in = new HashMap<>();
        in.put("P_KEYWORD", normalize(criteria.getKeyword()));
        in.put("P_STATUS", DomainConstants.STUDENT_STATUS_ACTIVE);
        in.put("P_PAGE_NO", pageNo);
        in.put("P_PAGE_SIZE", pageSize);

        Map<String, Object> out = call.execute(in);
        oracleProcExecutor.validateResult(out);

        List<StudentReportDto> content =
                ProcCursorReader.readCursor(out, "O_DATA_CURSOR", StudentReportDto.class);
        long totalRows = toLong(out.get("O_TOTAL_ROWS"));

        log.debug("{} trả về {}/{} học sinh (trang {}, cỡ trang {}, keyword='{}', status='{}')",
                PROC_SEARCH_PAGING, content.size(), totalRows, pageNo, pageSize,
                criteria.getKeyword(), criteria.getStatus());

        return PageResponse.of(content, pageNo, pageSize, totalRows);
    }

    /**
     * Quy đổi chuỗi rỗng/toàn khoảng trắng thành {@code null} để Procedure bỏ qua điều kiện đó.
     */
    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static LocalDate readLocalDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }
}
