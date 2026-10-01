package com.education.base.repository.custom.impl;

import com.education.base.dto.request.FeeListExportFilterRequest;
import com.education.base.dto.request.TransactionListExportFilterRequest;
import com.education.base.dto.response.ClassCollectionDto;
import com.education.base.dto.response.DashboardMetricsResponse;
import com.education.base.dto.response.DebtAgingFeeDto;
import com.education.base.dto.response.StudentLedgerEntryDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * Kiểm tra ánh xạ cột cursor (đặc biệt các cột B7 mới) và câu SELECT xuất Excel; không cần CSDL.
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

    @Test
    void debtAgingFeeMapper_mapsBucketAndDays() throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("FEE_ID", new BigDecimal("15"));
        row.put("FEE_CODE", "HP015");
        row.put("STUDENT_STATUS", "DELETED");
        row.put("DUE_DATE", Date.valueOf("2026-07-01"));
        row.put("REMAINING_AMOUNT", new BigDecimal("500000"));
        row.put("DAYS_PAST_DUE", new BigDecimal("93"));
        row.put("AGING_BUCKET", "D90_PLUS");

        DebtAgingFeeDto fee = ReportRepositoryImpl.DEBT_AGING_FEE_ROW_MAPPER.mapRow(resultSet(row), 0);

        assertThat(fee.getFeeId()).isEqualTo(15L);
        assertThat(fee.getStudentStatus()).isEqualTo("DELETED");
        assertThat(fee.getDueDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(fee.getDaysPastDue()).isEqualTo(93);
        assertThat(fee.getAgingBucket()).isEqualTo("D90_PLUS");
        assertThat(fee.getClassId()).isNull();
    }

    @Test
    void classCollectionMapper_keepsNullClassForUnassignedFees() throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("FEE_COUNT", new BigDecimal("3"));
        row.put("NET_AMOUNT", new BigDecimal("1500000"));
        row.put("COLLECTED_AMOUNT", new BigDecimal("750000"));
        row.put("COLLECTION_RATE", new BigDecimal("50.00"));

        ClassCollectionDto dto = ReportRepositoryImpl.CLASS_COLLECTION_ROW_MAPPER.mapRow(resultSet(row), 0);

        assertThat(dto.getClassId()).isNull();
        assertThat(dto.getFeeCount()).isEqualTo(3L);
        assertThat(dto.getCollectionRate()).isEqualByComparingTo("50");
    }

    @Test
    void ledgerEntryMapper_mapsDebitCreditAndBalance() throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("ENTRY_TYPE", "PAYMENT");
        row.put("ENTRY_DATE", Timestamp.valueOf("2026-09-15 10:30:00"));
        row.put("REF_ID", new BigDecimal("88"));
        row.put("REF_CODE", "GD088");
        row.put("DEBIT_AMOUNT", BigDecimal.ZERO);
        row.put("CREDIT_AMOUNT", new BigDecimal("400000"));
        row.put("BALANCE", new BigDecimal("600000"));

        StudentLedgerEntryDto entry = ReportRepositoryImpl.LEDGER_ENTRY_ROW_MAPPER.mapRow(resultSet(row), 0);

        assertThat(entry.getEntryType()).isEqualTo("PAYMENT");
        assertThat(entry.getEntryDate()).isEqualTo(LocalDateTime.of(2026, 9, 15, 10, 30));
        assertThat(entry.getRefId()).isEqualTo(88L);
        assertThat(entry.getCreditAmount()).isEqualByComparingTo("400000");
        assertThat(entry.getBalance()).isEqualByComparingTo("600000");
    }

    @Test
    void feeExportQuery_withoutFilter_onlyLimits() {
        ReportRepositoryImpl.ExportQuery query = ReportRepositoryImpl.buildFeeExportQuery(null, 20001);

        assertThat(query.sql()).startsWith(ReportRepositoryImpl.FEE_EXPORT_SELECT);
        assertThat(query.sql()).endsWith("FETCH FIRST ? ROWS ONLY");
        assertThat(query.sql()).doesNotContain("s.STATUS = 'ACTIVE'");
        assertThat(query.args()).containsExactly(20001);
    }

    @Test
    void feeExportQuery_bindsEveryCriterionAsParameter() {
        FeeListExportFilterRequest filter = FeeListExportFilterRequest.builder()
                .keyword(" 50%_off ")
                .status("PARTIAL")
                .studentId(7L)
                .classId(3L)
                .dueFromDate(LocalDate.of(2026, 9, 1))
                .dueToDate(LocalDate.of(2026, 9, 30))
                .overdueOnly(true)
                .build();

        ReportRepositoryImpl.ExportQuery query = ReportRepositoryImpl.buildFeeExportQuery(filter, 10);

        assertThat(query.sql())
                .contains("LOWER(f.FEE_CODE) LIKE ? ESCAPE '\\'")
                .contains("f.STATUS = ?")
                .contains("f.STUDENT_ID = ?")
                .contains("f.CLASS_ID = ?")
                .contains("f.DUE_DATE >= ?")
                .contains("f.DUE_DATE < ?")
                .contains("f.DUE_DATE < TRUNC(SYSDATE)")
                .doesNotContain("50%");
        assertThat(query.args()).containsExactly(
                "%50\\%\\_off%", "%50\\%\\_off%", "%50\\%\\_off%",
                "PARTIAL", 7L, 3L,
                Date.valueOf("2026-09-01"), Date.valueOf("2026-10-01"),
                10);
    }

    @Test
    void transactionExportQuery_toDateIsInclusive() {
        TransactionListExportFilterRequest filter = TransactionListExportFilterRequest.builder()
                .fromDate(LocalDate.of(2026, 9, 1))
                .toDate(LocalDate.of(2026, 9, 30))
                .paymentMethod("BANK_TRANSFER")
                .status("SUCCESS")
                .feeCode("HP")
                .keyword("an")
                .build();

        ReportRepositoryImpl.ExportQuery query = ReportRepositoryImpl.buildTransactionExportQuery(filter, 5);

        assertThat(query.sql())
                .startsWith(ReportRepositoryImpl.TRANSACTION_EXPORT_SELECT)
                .contains("t.PAYMENT_DATE >= ?")
                .contains("t.PAYMENT_DATE < ?")
                .contains("t.PAYMENT_METHOD = ?")
                .contains("t.STATUS = ?")
                .contains("LOWER(t.TRANSACTION_CODE) LIKE ?")
                .endsWith("FETCH FIRST ? ROWS ONLY");
        List<Object> expected = new ArrayList<>(List.of(
                Timestamp.valueOf("2026-09-01 00:00:00"),
                Timestamp.valueOf("2026-10-01 00:00:00"),
                "BANK_TRANSFER", "SUCCESS", "%hp%",
                "%an%", "%an%", "%an%", "%an%",
                5));
        assertThat(query.args()).containsExactlyElementsOf(expected);
    }

    @Test
    void contains_escapesLikeWildcards() {
        assertThat(ReportRepositoryImpl.contains(" A\\B_C%D ")).isEqualTo("%a\\\\b\\_c\\%d%");
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
