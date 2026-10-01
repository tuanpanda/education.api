package com.education.base.repository.custom.impl;

import com.education.base.dto.request.DashboardFilterRequest;
import com.education.base.dto.response.DashboardMetricsResponse;
import com.education.base.dto.response.RevenueByMonthDto;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ReportRepository;
import com.education.base.repository.base.OracleProcExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oracle.jdbc.OracleTypes;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Triển khai {@link ReportRepository} bằng Standalone Procedure
 * {@code PRC_RPT_DASHBOARD_METRICS}.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ReportRepositoryImpl implements ReportRepository {

    private static final String PROC_NAME = "PRC_RPT_DASHBOARD_METRICS";

    /**
     * Cột {@code TOTAL_BILLED} / {@code OVERDUE_AMOUNT} chỉ có sau khi chạy V14_3: đọc tùy chọn để backend mới vẫn
     * chạy được với procedure V1 cũ (khi đó hai trường là {@code null}).
     */
    static final RowMapper<DashboardMetricsResponse> SUMMARY_ROW_MAPPER = (rs, rowNum) ->
            DashboardMetricsResponse.builder()
                    .totalStudents(JdbcValueReaders.getLong(rs, "TOTAL_STUDENTS"))
                    .activeStudents(JdbcValueReaders.getLong(rs, "ACTIVE_STUDENTS"))
                    .totalClasses(JdbcValueReaders.getLong(rs, "TOTAL_CLASSES"))
                    .activeClasses(JdbcValueReaders.getLong(rs, "ACTIVE_CLASSES"))
                    .newLeads(JdbcValueReaders.getLong(rs, "NEW_LEADS"))
                    .convertedLeads(JdbcValueReaders.getLong(rs, "CONVERTED_LEADS"))
                    .totalReceivable(JdbcValueReaders.getBigDecimal(rs, "TOTAL_RECEIVABLE"))
                    .totalBilled(optionalBigDecimal(rs, "TOTAL_BILLED"))
                    .totalCollected(JdbcValueReaders.getBigDecimal(rs, "TOTAL_COLLECTED"))
                    .overdueFees(JdbcValueReaders.getLong(rs, "OVERDUE_FEES"))
                    .overdueAmount(optionalBigDecimal(rs, "OVERDUE_AMOUNT"))
                    .fromDate(JdbcValueReaders.getLocalDate(rs, "FROM_DATE"))
                    .toDate(JdbcValueReaders.getLocalDate(rs, "TO_DATE"))
                    .build();

    private static final RowMapper<RevenueByMonthDto> REVENUE_ROW_MAPPER = (rs, rowNum) ->
            RevenueByMonthDto.builder()
                    .revenueMonth(rs.getString("REVENUE_MONTH"))
                    .collectedAmount(JdbcValueReaders.getBigDecimal(rs, "COLLECTED_AMOUNT"))
                    .transactionCount(JdbcValueReaders.getInteger(rs, "TRANSACTION_COUNT"))
                    .build();

    private final OracleProcExecutor oracleProcExecutor;

    @Override
    public DashboardMetricsResponse getDashboardMetrics(DashboardFilterRequest filter) {
        DashboardFilterRequest criteria = filter == null ? new DashboardFilterRequest() : filter;

        SimpleJdbcCall call = oracleProcExecutor.createCall(PROC_NAME)
                .declareParameters(
                        new SqlParameter("P_FROM_DATE", Types.DATE),
                        new SqlParameter("P_TO_DATE", Types.DATE),
                        new SqlOutParameter("O_SUMMARY_CURSOR", OracleTypes.CURSOR, SUMMARY_ROW_MAPPER),
                        new SqlOutParameter("O_REVENUE_CURSOR", OracleTypes.CURSOR, REVENUE_ROW_MAPPER),
                        new SqlOutParameter("O_ERR_CODE", Types.VARCHAR),
                        new SqlOutParameter("O_ERR_MSG", Types.VARCHAR));

        Map<String, Object> in = new HashMap<>();
        in.put("P_FROM_DATE", toSqlDate(criteria.getFromDate()));
        in.put("P_TO_DATE", toSqlDate(criteria.getToDate()));

        Map<String, Object> out = call.execute(in);
        oracleProcExecutor.validateResult(out);

        List<DashboardMetricsResponse> summaries =
                ProcCursorReader.readCursor(out, "O_SUMMARY_CURSOR", DashboardMetricsResponse.class);
        if (summaries.isEmpty()) {
            throw new OracleBusinessException("DASHBOARD_EMPTY",
                    "Procedure không trả về chỉ số tổng hợp.");
        }

        DashboardMetricsResponse metrics = summaries.getFirst();
        metrics.setRevenueByMonth(
                ProcCursorReader.readCursor(out, "O_REVENUE_CURSOR", RevenueByMonthDto.class));

        log.debug("{} trả về chỉ số từ {} đến {}, {} tháng doanh thu",
                PROC_NAME, metrics.getFromDate(), metrics.getToDate(), metrics.getRevenueByMonth().size());
        return metrics;
    }

    /** Đọc cột số tiền nếu cursor có cột đó, ngược lại {@code null}. */
    static BigDecimal optionalBigDecimal(ResultSet rs, String column) throws SQLException {
        return hasColumn(rs, column) ? JdbcValueReaders.getBigDecimal(rs, column) : null;
    }

    private static boolean hasColumn(ResultSet rs, String column) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        if (metaData == null) {
            return false;
        }
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String label = metaData.getColumnLabel(i);
            if (label != null && label.equalsIgnoreCase(column)) {
                return true;
            }
        }
        return false;
    }

    private Date toSqlDate(java.time.LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }
}
