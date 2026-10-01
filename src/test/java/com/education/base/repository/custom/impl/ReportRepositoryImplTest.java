package com.education.base.repository.custom.impl;

import com.education.base.dto.response.DashboardMetricsResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kiểm tra ánh xạ cột cursor của dashboard (các cột B7 mới); không cần CSDL.
 */
class ReportRepositoryImplTest {

    @Test
    void dashboardMapper_readsB7ColumnsWhenProcedureIsV14_3() throws SQLException {
        Map<String, Object> row = dashboardRow();
        row.put("TOTAL_BILLED", new BigDecimal("9000000"));
        row.put("OVERDUE_AMOUNT", new BigDecimal("1200000"));

        DashboardMetricsResponse metrics = ReportRepositoryImpl.SUMMARY_ROW_MAPPER.mapRow(resultSet(row), 0);

        assertThat(metrics.getTotalReceivable()).isEqualByComparingTo("3000000");
        assertThat(metrics.getTotalBilled()).isEqualByComparingTo("9000000");
        assertThat(metrics.getOverdueAmount()).isEqualByComparingTo("1200000");
        assertThat(metrics.getOverdueFees()).isEqualTo(4L);
        assertThat(metrics.getFromDate()).isEqualTo(LocalDate.of(2025, 11, 1));
    }

    @Test
    void dashboardMapper_toleratesOldV1ProcedureWithoutB7Columns() throws SQLException {
        DashboardMetricsResponse metrics = ReportRepositoryImpl.SUMMARY_ROW_MAPPER.mapRow(resultSet(dashboardRow()), 0);

        assertThat(metrics.getTotalReceivable()).isEqualByComparingTo("3000000");
        assertThat(metrics.getTotalBilled()).isNull();
        assertThat(metrics.getOverdueAmount()).isNull();
        assertThat(metrics.getTotalStudents()).isEqualTo(120L);
    }

    private static Map<String, Object> dashboardRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("TOTAL_STUDENTS", new BigDecimal("120"));
        row.put("ACTIVE_STUDENTS", new BigDecimal("100"));
        row.put("TOTAL_CLASSES", new BigDecimal("10"));
        row.put("ACTIVE_CLASSES", new BigDecimal("8"));
        row.put("NEW_LEADS", new BigDecimal("5"));
        row.put("CONVERTED_LEADS", new BigDecimal("2"));
        row.put("TOTAL_RECEIVABLE", new BigDecimal("3000000"));
        row.put("TOTAL_COLLECTED", new BigDecimal("6000000"));
        row.put("OVERDUE_FEES", new BigDecimal("4"));
        row.put("FROM_DATE", Date.valueOf("2025-11-01"));
        row.put("TO_DATE", Date.valueOf("2026-10-02"));
        return row;
    }

    /** ResultSet giả: đọc theo tên cột từ map, metadata liệt kê đúng các cột có trong map. */
    private static ResultSet resultSet(Map<String, Object> row) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject(anyString())).thenAnswer(inv -> row.get(inv.<String>getArgument(0)));
        when(rs.getString(anyString())).thenAnswer(inv -> (String) row.get(inv.<String>getArgument(0)));
        when(rs.getBigDecimal(anyString())).thenAnswer(inv -> (BigDecimal) row.get(inv.<String>getArgument(0)));
        when(rs.getDate(anyString())).thenAnswer(inv -> (Date) row.get(inv.<String>getArgument(0)));
        when(rs.getTimestamp(anyString())).thenAnswer(inv -> (Timestamp) row.get(inv.<String>getArgument(0)));

        List<String> columns = new ArrayList<>(row.keySet());
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        when(metaData.getColumnCount()).thenReturn(columns.size());
        when(metaData.getColumnLabel(anyInt())).thenAnswer(inv -> columns.get(inv.<Integer>getArgument(0) - 1));
        when(rs.getMetaData()).thenReturn(metaData);
        return rs;
    }
}
