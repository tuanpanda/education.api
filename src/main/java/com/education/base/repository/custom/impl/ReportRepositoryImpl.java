package com.education.base.repository.custom.impl;

import com.education.base.dto.request.DashboardFilterRequest;
import com.education.base.dto.request.FeeListExportFilterRequest;
import com.education.base.dto.request.TransactionListExportFilterRequest;
import com.education.base.dto.response.ClassCollectionDto;
import com.education.base.dto.response.DashboardMetricsResponse;
import com.education.base.dto.response.DebtAgingDto;
import com.education.base.dto.response.DebtAgingFeeDto;
import com.education.base.dto.response.DebtAgingStudentDto;
import com.education.base.dto.response.FeeExportRowDto;
import com.education.base.dto.response.FeeStatusSummaryDto;
import com.education.base.dto.response.FinanceMonthlyDto;
import com.education.base.dto.response.FinanceSummaryDto;
import com.education.base.dto.response.RevenueByMonthDto;
import com.education.base.dto.response.StudentLedgerDto;
import com.education.base.dto.response.StudentLedgerEntryDto;
import com.education.base.dto.response.TransactionExportRowDto;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.ReportRepository;
import com.education.base.repository.base.OracleProcExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oracle.jdbc.OracleTypes;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Triển khai {@link ReportRepository} bằng các Standalone Procedure {@code PRC_RPT_DASHBOARD_METRICS},
 * {@code PRC_RPT_FINANCE_SUMMARY}, {@code PRC_RPT_DEBT_AGING}, {@code PRC_RPT_CLASS_COLLECTION},
 * {@code PRC_RPT_STUDENT_LEDGER} (V14_3) và truy vấn SELECT tham số hóa cho file xuất Excel.
 * <p>
 * Chỉ đọc dữ liệu: không ghi bảng nghiệp vụ nào.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ReportRepositoryImpl implements ReportRepository {

    private static final String PROC_NAME = "PRC_RPT_DASHBOARD_METRICS";
    private static final String PROC_FINANCE_SUMMARY = "PRC_RPT_FINANCE_SUMMARY";
    private static final String PROC_DEBT_AGING = "PRC_RPT_DEBT_AGING";
    private static final String PROC_CLASS_COLLECTION = "PRC_RPT_CLASS_COLLECTION";
    private static final String PROC_STUDENT_LEDGER = "PRC_RPT_STUDENT_LEDGER";

    private static final String ERR_CODE = "O_ERR_CODE";
    private static final String ERR_MSG = "O_ERR_MSG";

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

    static final RowMapper<FinanceSummaryDto> FINANCE_SUMMARY_ROW_MAPPER = (rs, rowNum) ->
            FinanceSummaryDto.builder()
                    .totalBilled(JdbcValueReaders.getBigDecimal(rs, "TOTAL_BILLED"))
                    .totalDiscount(JdbcValueReaders.getBigDecimal(rs, "TOTAL_DISCOUNT"))
                    .netBilled(JdbcValueReaders.getBigDecimal(rs, "NET_BILLED"))
                    .totalCollected(JdbcValueReaders.getBigDecimal(rs, "TOTAL_COLLECTED"))
                    .transactionCount(JdbcValueReaders.getLong(rs, "TRANSACTION_COUNT"))
                    .totalOutstanding(JdbcValueReaders.getBigDecimal(rs, "TOTAL_OUTSTANDING"))
                    .overdueAmount(JdbcValueReaders.getBigDecimal(rs, "OVERDUE_AMOUNT"))
                    .overdueFees(JdbcValueReaders.getLong(rs, "OVERDUE_FEES"))
                    .feeCount(JdbcValueReaders.getLong(rs, "FEE_COUNT"))
                    .fromDate(JdbcValueReaders.getLocalDate(rs, "FROM_DATE"))
                    .toDate(JdbcValueReaders.getLocalDate(rs, "TO_DATE"))
                    .build();

    static final RowMapper<FeeStatusSummaryDto> FEE_STATUS_ROW_MAPPER = (rs, rowNum) ->
            FeeStatusSummaryDto.builder()
                    .status(rs.getString("STATUS"))
                    .feeCount(JdbcValueReaders.getLong(rs, "FEE_COUNT"))
                    .netAmount(JdbcValueReaders.getBigDecimal(rs, "NET_AMOUNT"))
                    .remainingAmount(JdbcValueReaders.getBigDecimal(rs, "REMAINING_AMOUNT"))
                    .build();

    static final RowMapper<FinanceMonthlyDto> FINANCE_MONTHLY_ROW_MAPPER = (rs, rowNum) ->
            FinanceMonthlyDto.builder()
                    .month(rs.getString("PERIOD_MONTH"))
                    .billedAmount(JdbcValueReaders.getBigDecimal(rs, "BILLED_AMOUNT"))
                    .feeCount(JdbcValueReaders.getLong(rs, "FEE_COUNT"))
                    .collectedAmount(JdbcValueReaders.getBigDecimal(rs, "COLLECTED_AMOUNT"))
                    .transactionCount(JdbcValueReaders.getLong(rs, "TRANSACTION_COUNT"))
                    .build();

    static final RowMapper<DebtAgingDto> DEBT_AGING_SUMMARY_ROW_MAPPER = (rs, rowNum) ->
            DebtAgingDto.builder()
                    .asOfDate(JdbcValueReaders.getLocalDate(rs, "AS_OF_DATE"))
                    .notDueAmount(JdbcValueReaders.getBigDecimal(rs, "NOT_DUE_AMOUNT"))
                    .due0To30Amount(JdbcValueReaders.getBigDecimal(rs, "D0_30_AMOUNT"))
                    .due31To60Amount(JdbcValueReaders.getBigDecimal(rs, "D31_60_AMOUNT"))
                    .due61To90Amount(JdbcValueReaders.getBigDecimal(rs, "D61_90_AMOUNT"))
                    .dueOver90Amount(JdbcValueReaders.getBigDecimal(rs, "D90_PLUS_AMOUNT"))
                    .totalOutstanding(JdbcValueReaders.getBigDecimal(rs, "TOTAL_OUTSTANDING"))
                    .feeCount(JdbcValueReaders.getLong(rs, "FEE_COUNT"))
                    .studentCount(JdbcValueReaders.getLong(rs, "STUDENT_COUNT"))
                    .build();

    static final RowMapper<DebtAgingStudentDto> DEBT_AGING_STUDENT_ROW_MAPPER = (rs, rowNum) ->
            DebtAgingStudentDto.builder()
                    .studentId(JdbcValueReaders.getLong(rs, "STUDENT_ID"))
                    .studentCode(rs.getString("STUDENT_CODE"))
                    .studentName(rs.getString("STUDENT_NAME"))
                    .studentStatus(rs.getString("STUDENT_STATUS"))
                    .notDueAmount(JdbcValueReaders.getBigDecimal(rs, "NOT_DUE_AMOUNT"))
                    .due0To30Amount(JdbcValueReaders.getBigDecimal(rs, "D0_30_AMOUNT"))
                    .due31To60Amount(JdbcValueReaders.getBigDecimal(rs, "D31_60_AMOUNT"))
                    .due61To90Amount(JdbcValueReaders.getBigDecimal(rs, "D61_90_AMOUNT"))
                    .dueOver90Amount(JdbcValueReaders.getBigDecimal(rs, "D90_PLUS_AMOUNT"))
                    .totalOutstanding(JdbcValueReaders.getBigDecimal(rs, "TOTAL_OUTSTANDING"))
                    .feeCount(JdbcValueReaders.getLong(rs, "FEE_COUNT"))
                    .oldestDueDate(JdbcValueReaders.getLocalDate(rs, "OLDEST_DUE_DATE"))
                    .maxDaysPastDue(JdbcValueReaders.getInteger(rs, "MAX_DAYS_PAST_DUE"))
                    .build();

    static final RowMapper<DebtAgingFeeDto> DEBT_AGING_FEE_ROW_MAPPER = (rs, rowNum) ->
            DebtAgingFeeDto.builder()
                    .feeId(JdbcValueReaders.getLong(rs, "FEE_ID"))
                    .feeCode(rs.getString("FEE_CODE"))
                    .studentId(JdbcValueReaders.getLong(rs, "STUDENT_ID"))
                    .studentCode(rs.getString("STUDENT_CODE"))
                    .studentName(rs.getString("STUDENT_NAME"))
                    .studentStatus(rs.getString("STUDENT_STATUS"))
                    .classId(JdbcValueReaders.getLong(rs, "CLASS_ID"))
                    .classCode(rs.getString("CLASS_CODE"))
                    .className(rs.getString("CLASS_NAME"))
                    .feeYear(JdbcValueReaders.getInteger(rs, "FEE_YEAR"))
                    .feeMonth(JdbcValueReaders.getInteger(rs, "FEE_MONTH"))
                    .dueDate(JdbcValueReaders.getLocalDate(rs, "DUE_DATE"))
                    .status(rs.getString("STATUS"))
                    .netAmount(JdbcValueReaders.getBigDecimal(rs, "NET_AMOUNT"))
                    .paidAmount(JdbcValueReaders.getBigDecimal(rs, "PAID_AMOUNT"))
                    .remainingAmount(JdbcValueReaders.getBigDecimal(rs, "REMAINING_AMOUNT"))
                    .daysPastDue(JdbcValueReaders.getInteger(rs, "DAYS_PAST_DUE"))
                    .agingBucket(rs.getString("AGING_BUCKET"))
                    .build();

    static final RowMapper<ClassCollectionDto> CLASS_COLLECTION_ROW_MAPPER = (rs, rowNum) ->
            ClassCollectionDto.builder()
                    .classId(JdbcValueReaders.getLong(rs, "CLASS_ID"))
                    .classCode(rs.getString("CLASS_CODE"))
                    .className(rs.getString("CLASS_NAME"))
                    .classStatus(rs.getString("CLASS_STATUS"))
                    .feeCount(JdbcValueReaders.getLong(rs, "FEE_COUNT"))
                    .studentCount(JdbcValueReaders.getLong(rs, "STUDENT_COUNT"))
                    .billedAmount(JdbcValueReaders.getBigDecimal(rs, "BILLED_AMOUNT"))
                    .discountAmount(JdbcValueReaders.getBigDecimal(rs, "DISCOUNT_AMOUNT"))
                    .netAmount(JdbcValueReaders.getBigDecimal(rs, "NET_AMOUNT"))
                    .collectedAmount(JdbcValueReaders.getBigDecimal(rs, "COLLECTED_AMOUNT"))
                    .outstandingAmount(JdbcValueReaders.getBigDecimal(rs, "OUTSTANDING_AMOUNT"))
                    .collectionRate(JdbcValueReaders.getBigDecimal(rs, "COLLECTION_RATE"))
                    .build();

    static final RowMapper<StudentLedgerDto> LEDGER_STUDENT_ROW_MAPPER = (rs, rowNum) ->
            StudentLedgerDto.builder()
                    .studentId(JdbcValueReaders.getLong(rs, "ID"))
                    .studentCode(rs.getString("STUDENT_CODE"))
                    .studentName(rs.getString("FULL_NAME"))
                    .studentStatus(rs.getString("STUDENT_STATUS"))
                    .build();

    static final RowMapper<StudentLedgerEntryDto> LEDGER_ENTRY_ROW_MAPPER = (rs, rowNum) ->
            StudentLedgerEntryDto.builder()
                    .entryType(rs.getString("ENTRY_TYPE"))
                    .entryDate(JdbcValueReaders.getLocalDateTime(rs, "ENTRY_DATE"))
                    .refId(JdbcValueReaders.getLong(rs, "REF_ID"))
                    .refCode(rs.getString("REF_CODE"))
                    .feeId(JdbcValueReaders.getLong(rs, "FEE_ID"))
                    .feeCode(rs.getString("FEE_CODE"))
                    .feeYear(JdbcValueReaders.getInteger(rs, "FEE_YEAR"))
                    .feeMonth(JdbcValueReaders.getInteger(rs, "FEE_MONTH"))
                    .dueDate(JdbcValueReaders.getLocalDate(rs, "DUE_DATE"))
                    .classCode(rs.getString("CLASS_CODE"))
                    .className(rs.getString("CLASS_NAME"))
                    .paymentMethod(rs.getString("PAYMENT_METHOD"))
                    .status(rs.getString("STATUS"))
                    .note(rs.getString("NOTE"))
                    .debitAmount(JdbcValueReaders.getBigDecimal(rs, "DEBIT_AMOUNT"))
                    .creditAmount(JdbcValueReaders.getBigDecimal(rs, "CREDIT_AMOUNT"))
                    .balance(JdbcValueReaders.getBigDecimal(rs, "BALANCE"))
                    .build();

    static final RowMapper<FeeExportRowDto> FEE_EXPORT_ROW_MAPPER = (rs, rowNum) ->
            FeeExportRowDto.builder()
                    .id(JdbcValueReaders.getLong(rs, "ID"))
                    .feeCode(rs.getString("FEE_CODE"))
                    .studentId(JdbcValueReaders.getLong(rs, "STUDENT_ID"))
                    .studentCode(rs.getString("STUDENT_CODE"))
                    .studentName(rs.getString("STUDENT_NAME"))
                    .studentStatus(rs.getString("STUDENT_STATUS"))
                    .classId(JdbcValueReaders.getLong(rs, "CLASS_ID"))
                    .classCode(rs.getString("CLASS_CODE"))
                    .className(rs.getString("CLASS_NAME"))
                    .feeYear(JdbcValueReaders.getInteger(rs, "FEE_YEAR"))
                    .feeMonth(JdbcValueReaders.getInteger(rs, "FEE_MONTH"))
                    .totalAmount(JdbcValueReaders.getBigDecimal(rs, "TOTAL_AMOUNT"))
                    .discountAmount(JdbcValueReaders.getBigDecimal(rs, "DISCOUNT_AMOUNT"))
                    .paidAmount(JdbcValueReaders.getBigDecimal(rs, "PAID_AMOUNT"))
                    .remainingAmount(JdbcValueReaders.getBigDecimal(rs, "REMAINING_AMOUNT"))
                    .dueDate(JdbcValueReaders.getLocalDate(rs, "DUE_DATE"))
                    .status(rs.getString("STATUS"))
                    .note(rs.getString("NOTE"))
                    .createdAt(JdbcValueReaders.getLocalDateTime(rs, "CREATED_AT"))
                    .build();

    static final RowMapper<TransactionExportRowDto> TRANSACTION_EXPORT_ROW_MAPPER = (rs, rowNum) ->
            TransactionExportRowDto.builder()
                    .id(JdbcValueReaders.getLong(rs, "ID"))
                    .transactionCode(rs.getString("TRANSACTION_CODE"))
                    .tuitionFeeId(JdbcValueReaders.getLong(rs, "TUITION_FEE_ID"))
                    .feeCode(rs.getString("FEE_CODE"))
                    .studentId(JdbcValueReaders.getLong(rs, "STUDENT_ID"))
                    .studentCode(rs.getString("STUDENT_CODE"))
                    .studentName(rs.getString("STUDENT_NAME"))
                    .classId(JdbcValueReaders.getLong(rs, "CLASS_ID"))
                    .classCode(rs.getString("CLASS_CODE"))
                    .className(rs.getString("CLASS_NAME"))
                    .amount(JdbcValueReaders.getBigDecimal(rs, "AMOUNT"))
                    .paymentMethod(rs.getString("PAYMENT_METHOD"))
                    .paymentDate(JdbcValueReaders.getLocalDateTime(rs, "PAYMENT_DATE"))
                    .bankBin(rs.getString("BANK_BIN"))
                    .accountNo(rs.getString("ACCOUNT_NO"))
                    .bankReferenceNo(rs.getString("BANK_REFERENCE_NO"))
                    .status(rs.getString("STATUS"))
                    .note(rs.getString("NOTE"))
                    .createdBy(rs.getString("CREATED_BY"))
                    .build();

    static final String FEE_EXPORT_SELECT = """
            SELECT f.ID, f.FEE_CODE, f.STUDENT_ID, s.STUDENT_CODE, s.FULL_NAME AS STUDENT_NAME,
                   CASE WHEN s.IS_DELETED = 1 THEN 'DELETED' ELSE s.STATUS END AS STUDENT_STATUS,
                   f.CLASS_ID, c.CLASS_CODE, c.CLASS_NAME, f.FEE_YEAR, f.FEE_MONTH,
                   f.TOTAL_AMOUNT, f.DISCOUNT_AMOUNT, f.PAID_AMOUNT,
                   GREATEST(f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT - f.PAID_AMOUNT, 0) AS REMAINING_AMOUNT,
                   f.DUE_DATE, f.STATUS, f.NOTE, f.CREATED_AT
              FROM FIN_TUITION_FEES f
              JOIN EDU_STUDENTS s ON s.ID = f.STUDENT_ID
              LEFT JOIN EDU_CLASSES c ON c.ID = f.CLASS_ID
             WHERE f.IS_DELETED = 0""";

    static final String TRANSACTION_EXPORT_SELECT = """
            SELECT t.ID, t.TRANSACTION_CODE, t.TUITION_FEE_ID, f.FEE_CODE, f.STUDENT_ID, s.STUDENT_CODE,
                   s.FULL_NAME AS STUDENT_NAME, f.CLASS_ID, c.CLASS_CODE, c.CLASS_NAME,
                   t.AMOUNT, t.PAYMENT_METHOD, t.PAYMENT_DATE, t.BANK_BIN, t.ACCOUNT_NO, t.BANK_REFERENCE_NO,
                   t.STATUS, t.NOTE, t.CREATED_BY
              FROM FIN_PAYMENT_TRANSACTIONS t
              JOIN FIN_TUITION_FEES f ON f.ID = t.TUITION_FEE_ID
              JOIN EDU_STUDENTS s ON s.ID = f.STUDENT_ID
              LEFT JOIN EDU_CLASSES c ON c.ID = f.CLASS_ID
             WHERE t.IS_DELETED = 0""";

    /** Câu SELECT đã ghép điều kiện, tham số theo vị trí ({@code ?}). */
    record ExportQuery(String sql, List<Object> args) {
    }

    private final OracleProcExecutor oracleProcExecutor;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public DashboardMetricsResponse getDashboardMetrics(DashboardFilterRequest filter) {
        DashboardFilterRequest criteria = filter == null ? new DashboardFilterRequest() : filter;

        SimpleJdbcCall call = oracleProcExecutor.createCall(PROC_NAME)
                .declareParameters(
                        new SqlParameter("P_FROM_DATE", Types.DATE),
                        new SqlParameter("P_TO_DATE", Types.DATE),
                        new SqlOutParameter("O_SUMMARY_CURSOR", OracleTypes.CURSOR, SUMMARY_ROW_MAPPER),
                        new SqlOutParameter("O_REVENUE_CURSOR", OracleTypes.CURSOR, REVENUE_ROW_MAPPER),
                        new SqlOutParameter(ERR_CODE, Types.VARCHAR),
                        new SqlOutParameter(ERR_MSG, Types.VARCHAR));

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

    @Override
    public FinanceSummaryDto getFinanceSummary(LocalDate fromDate, LocalDate toDate) {
        SimpleJdbcCall call = oracleProcExecutor.createCall(PROC_FINANCE_SUMMARY)
                .declareParameters(
                        new SqlParameter("P_FROM_DATE", Types.DATE),
                        new SqlParameter("P_TO_DATE", Types.DATE),
                        new SqlOutParameter("O_SUMMARY_CURSOR", OracleTypes.CURSOR, FINANCE_SUMMARY_ROW_MAPPER),
                        new SqlOutParameter("O_STATUS_CURSOR", OracleTypes.CURSOR, FEE_STATUS_ROW_MAPPER),
                        new SqlOutParameter("O_MONTHLY_CURSOR", OracleTypes.CURSOR, FINANCE_MONTHLY_ROW_MAPPER),
                        new SqlOutParameter(ERR_CODE, Types.VARCHAR),
                        new SqlOutParameter(ERR_MSG, Types.VARCHAR));

        Map<String, Object> in = new HashMap<>();
        in.put("P_FROM_DATE", toSqlDate(fromDate));
        in.put("P_TO_DATE", toSqlDate(toDate));

        Map<String, Object> out = call.execute(in);
        oracleProcExecutor.validateResult(out);

        List<FinanceSummaryDto> summaries =
                ProcCursorReader.readCursor(out, "O_SUMMARY_CURSOR", FinanceSummaryDto.class);
        if (summaries.isEmpty()) {
            throw new OracleBusinessException("FINANCE_SUMMARY_EMPTY",
                    "Procedure không trả về số liệu tổng hợp tài chính.");
        }
        FinanceSummaryDto summary = summaries.getFirst();
        summary.setStatusBreakdown(
                ProcCursorReader.readCursor(out, "O_STATUS_CURSOR", FeeStatusSummaryDto.class));
        summary.setMonthly(
                ProcCursorReader.readCursor(out, "O_MONTHLY_CURSOR", FinanceMonthlyDto.class));
        return summary;
    }

    @Override
    public DebtAgingDto getDebtAging(LocalDate asOfDate) {
        SimpleJdbcCall call = oracleProcExecutor.createCall(PROC_DEBT_AGING)
                .declareParameters(
                        new SqlParameter("P_AS_OF_DATE", Types.DATE),
                        new SqlOutParameter("O_SUMMARY_CURSOR", OracleTypes.CURSOR, DEBT_AGING_SUMMARY_ROW_MAPPER),
                        new SqlOutParameter("O_STUDENT_CURSOR", OracleTypes.CURSOR, DEBT_AGING_STUDENT_ROW_MAPPER),
                        new SqlOutParameter("O_DATA_CURSOR", OracleTypes.CURSOR, DEBT_AGING_FEE_ROW_MAPPER),
                        new SqlOutParameter(ERR_CODE, Types.VARCHAR),
                        new SqlOutParameter(ERR_MSG, Types.VARCHAR));

        Map<String, Object> in = new HashMap<>();
        in.put("P_AS_OF_DATE", toSqlDate(asOfDate));

        Map<String, Object> out = call.execute(in);
        oracleProcExecutor.validateResult(out);

        List<DebtAgingDto> summaries = ProcCursorReader.readCursor(out, "O_SUMMARY_CURSOR", DebtAgingDto.class);
        if (summaries.isEmpty()) {
            throw new OracleBusinessException("DEBT_AGING_EMPTY",
                    "Procedure không trả về số liệu tuổi nợ.");
        }
        DebtAgingDto aging = summaries.getFirst();
        aging.setStudents(ProcCursorReader.readCursor(out, "O_STUDENT_CURSOR", DebtAgingStudentDto.class));
        aging.setFees(ProcCursorReader.readCursor(out, "O_DATA_CURSOR", DebtAgingFeeDto.class));
        return aging;
    }

    @Override
    public List<ClassCollectionDto> getClassCollection(Integer year, Integer month) {
        SimpleJdbcCall call = oracleProcExecutor.createCall(PROC_CLASS_COLLECTION)
                .declareParameters(
                        new SqlParameter("P_YEAR", Types.NUMERIC),
                        new SqlParameter("P_MONTH", Types.NUMERIC),
                        new SqlOutParameter("O_DATA_CURSOR", OracleTypes.CURSOR, CLASS_COLLECTION_ROW_MAPPER),
                        new SqlOutParameter(ERR_CODE, Types.VARCHAR),
                        new SqlOutParameter(ERR_MSG, Types.VARCHAR));

        Map<String, Object> in = new HashMap<>();
        in.put("P_YEAR", year);
        in.put("P_MONTH", month);

        Map<String, Object> out = call.execute(in);
        oracleProcExecutor.validateResult(out);
        return ProcCursorReader.readCursor(out, "O_DATA_CURSOR", ClassCollectionDto.class);
    }

    @Override
    public StudentLedgerDto getStudentLedger(Long studentId) {
        if (studentId == null) {
            throw new OracleBusinessException("STUDENT_ID_REQUIRED", "Thiếu ID học sinh.");
        }
        SimpleJdbcCall call = oracleProcExecutor.createCall(PROC_STUDENT_LEDGER)
                .declareParameters(
                        new SqlParameter("P_STUDENT_ID", Types.NUMERIC),
                        new SqlOutParameter("O_STUDENT_CURSOR", OracleTypes.CURSOR, LEDGER_STUDENT_ROW_MAPPER),
                        new SqlOutParameter("O_DATA_CURSOR", OracleTypes.CURSOR, LEDGER_ENTRY_ROW_MAPPER),
                        new SqlOutParameter(ERR_CODE, Types.VARCHAR),
                        new SqlOutParameter(ERR_MSG, Types.VARCHAR));

        Map<String, Object> in = new HashMap<>();
        in.put("P_STUDENT_ID", studentId);

        Map<String, Object> out = call.execute(in);
        oracleProcExecutor.validateResult(out);

        List<StudentLedgerDto> students = ProcCursorReader.readCursor(out, "O_STUDENT_CURSOR", StudentLedgerDto.class);
        if (students.isEmpty()) {
            throw new OracleBusinessException("STUDENT_NOT_FOUND", "Không tìm thấy học sinh ID: " + studentId);
        }
        StudentLedgerDto ledger = students.getFirst();
        ledger.setEntries(ProcCursorReader.readCursor(out, "O_DATA_CURSOR", StudentLedgerEntryDto.class));
        return ledger;
    }

    @Override
    public List<FeeExportRowDto> findFeesForExport(FeeListExportFilterRequest filter, int limit) {
        ExportQuery query = buildFeeExportQuery(filter, limit);
        return jdbcTemplate.query(query.sql(), FEE_EXPORT_ROW_MAPPER, query.args().toArray());
    }

    @Override
    public List<TransactionExportRowDto> findTransactionsForExport(TransactionListExportFilterRequest filter,
                                                                   int limit) {
        ExportQuery query = buildTransactionExportQuery(filter, limit);
        return jdbcTemplate.query(query.sql(), TRANSACTION_EXPORT_ROW_MAPPER, query.args().toArray());
    }

    /**
     * Ghép điều kiện lọc khoản học phí (cùng ý nghĩa với {@code TuitionFeeSpecifications}, nhưng không giới hạn
     * học sinh {@code ACTIVE}). Mọi giá trị đều là tham số {@code ?}, không nối chuỗi người dùng nhập.
     */
    static ExportQuery buildFeeExportQuery(FeeListExportFilterRequest filter, int limit) {
        FeeListExportFilterRequest criteria = filter == null ? new FeeListExportFilterRequest() : filter;
        StringBuilder sql = new StringBuilder(FEE_EXPORT_SELECT);
        List<Object> args = new ArrayList<>();

        if (hasText(criteria.getKeyword())) {
            String like = contains(criteria.getKeyword());
            sql.append("\n   AND (LOWER(f.FEE_CODE) LIKE ? ESCAPE '\\'")
                    .append(" OR LOWER(s.STUDENT_CODE) LIKE ? ESCAPE '\\'")
                    .append(" OR LOWER(s.FULL_NAME) LIKE ? ESCAPE '\\')");
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (hasText(criteria.getStatus())) {
            sql.append("\n   AND f.STATUS = ?");
            args.add(criteria.getStatus().trim());
        }
        if (criteria.getStudentId() != null) {
            sql.append("\n   AND f.STUDENT_ID = ?");
            args.add(criteria.getStudentId());
        }
        if (criteria.getClassId() != null) {
            sql.append("\n   AND f.CLASS_ID = ?");
            args.add(criteria.getClassId());
        }
        if (criteria.getDueFromDate() != null) {
            sql.append("\n   AND f.DUE_DATE >= ?");
            args.add(Date.valueOf(criteria.getDueFromDate()));
        }
        if (criteria.getDueToDate() != null) {
            sql.append("\n   AND f.DUE_DATE < ?");
            args.add(Date.valueOf(criteria.getDueToDate().plusDays(1)));
        }
        if (Boolean.TRUE.equals(criteria.getOverdueOnly())) {
            sql.append("\n   AND f.DUE_DATE < TRUNC(SYSDATE)")
                    .append("\n   AND f.STATUS IN ('UNPAID', 'PARTIAL', 'OVERDUE')")
                    .append("\n   AND f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT - f.PAID_AMOUNT > 0");
        }
        sql.append("\n ORDER BY f.ID DESC\n FETCH FIRST ? ROWS ONLY");
        args.add(limit);
        return new ExportQuery(sql.toString(), args);
    }

    /** Ghép điều kiện lọc giao dịch; {@code toDate} bao gồm cả ngày đó. */
    static ExportQuery buildTransactionExportQuery(TransactionListExportFilterRequest filter, int limit) {
        TransactionListExportFilterRequest criteria =
                filter == null ? new TransactionListExportFilterRequest() : filter;
        StringBuilder sql = new StringBuilder(TRANSACTION_EXPORT_SELECT);
        List<Object> args = new ArrayList<>();

        if (criteria.getFromDate() != null) {
            sql.append("\n   AND t.PAYMENT_DATE >= ?");
            args.add(Timestamp.valueOf(criteria.getFromDate().atStartOfDay()));
        }
        if (criteria.getToDate() != null) {
            sql.append("\n   AND t.PAYMENT_DATE < ?");
            args.add(Timestamp.valueOf(criteria.getToDate().plusDays(1).atStartOfDay()));
        }
        if (hasText(criteria.getPaymentMethod())) {
            sql.append("\n   AND t.PAYMENT_METHOD = ?");
            args.add(criteria.getPaymentMethod().trim());
        }
        if (hasText(criteria.getStatus())) {
            sql.append("\n   AND t.STATUS = ?");
            args.add(criteria.getStatus().trim());
        }
        if (criteria.getStudentId() != null) {
            sql.append("\n   AND f.STUDENT_ID = ?");
            args.add(criteria.getStudentId());
        }
        if (criteria.getClassId() != null) {
            sql.append("\n   AND f.CLASS_ID = ?");
            args.add(criteria.getClassId());
        }
        if (hasText(criteria.getFeeCode())) {
            sql.append("\n   AND LOWER(f.FEE_CODE) LIKE ? ESCAPE '\\'");
            args.add(contains(criteria.getFeeCode()));
        }
        if (hasText(criteria.getKeyword())) {
            String like = contains(criteria.getKeyword());
            sql.append("\n   AND (LOWER(t.TRANSACTION_CODE) LIKE ? ESCAPE '\\'")
                    .append(" OR LOWER(NVL(t.BANK_REFERENCE_NO, ' ')) LIKE ? ESCAPE '\\'")
                    .append(" OR LOWER(s.STUDENT_CODE) LIKE ? ESCAPE '\\'")
                    .append(" OR LOWER(s.FULL_NAME) LIKE ? ESCAPE '\\')");
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append("\n ORDER BY t.PAYMENT_DATE DESC, t.ID DESC\n FETCH FIRST ? ROWS ONLY");
        args.add(limit);
        return new ExportQuery(sql.toString(), args);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** {@code %từ khóa%} chữ thường, thoát {@code \ % _} (dùng với {@code ESCAPE '\'}). */
    static String contains(String raw) {
        String escaped = raw.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
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

    private static Date toSqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }
}
