package com.education.base.repository.custom.impl;

import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.dto.response.TuitionFeeDetailResponse;
import com.education.base.dto.response.TuitionSlipResponseDto;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.base.OracleProcExecutor;
import com.education.base.repository.custom.TuitionFeeRepositoryCustom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oracle.jdbc.OracleTypes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Triển khai {@link TuitionFeeRepositoryCustom} bằng Standalone Procedure
 * {@code PRC_GET_TUITION_FEE_DETAIL} và {@code PRC_GET_TUITION_SLIP_DATA}.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TuitionFeeRepositoryCustomImpl implements TuitionFeeRepositoryCustom {

    private static final String PROC_NAME = "PRC_GET_TUITION_FEE_DETAIL";
    private static final String SLIP_PROC = "PRC_GET_TUITION_SLIP_DATA";
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private static final RowMapper<TuitionFeeDetailResponse> FEE_ROW_MAPPER = (rs, rowNum) ->
            TuitionFeeDetailResponse.builder()
                    .id(rs.getLong("ID"))
                    .feeCode(rs.getString("FEE_CODE"))
                    .studentId(JdbcValueReaders.getLong(rs, "STUDENT_ID"))
                    .studentCode(rs.getString("STUDENT_CODE"))
                    .studentName(rs.getString("STUDENT_NAME"))
                    .classId(JdbcValueReaders.getLong(rs, "CLASS_ID"))
                    .classCode(rs.getString("CLASS_CODE"))
                    .className(rs.getString("CLASS_NAME"))
                    .totalAmount(JdbcValueReaders.getBigDecimal(rs, "TOTAL_AMOUNT"))
                    .discountAmount(JdbcValueReaders.getBigDecimal(rs, "DISCOUNT_AMOUNT"))
                    .paidAmount(JdbcValueReaders.getBigDecimal(rs, "PAID_AMOUNT"))
                    .remainingAmount(JdbcValueReaders.getBigDecimal(rs, "REMAINING_AMOUNT"))
                    .dueDate(JdbcValueReaders.getLocalDate(rs, "DUE_DATE"))
                    .status(rs.getString("STATUS"))
                    .note(rs.getString("NOTE"))
                    .createdAt(JdbcValueReaders.getLocalDateTime(rs, "CREATED_AT"))
                    .updatedAt(JdbcValueReaders.getLocalDateTime(rs, "UPDATED_AT"))
                    .build();

    private static final RowMapper<PaymentTransactionDto> TRANSACTION_ROW_MAPPER = (rs, rowNum) ->
            PaymentTransactionDto.builder()
                    .id(rs.getLong("ID"))
                    .transactionCode(rs.getString("TRANSACTION_CODE"))
                    .tuitionFeeId(JdbcValueReaders.getLong(rs, "TUITION_FEE_ID"))
                    .amount(JdbcValueReaders.getBigDecimal(rs, "AMOUNT"))
                    .paymentMethod(rs.getString("PAYMENT_METHOD"))
                    .paymentDate(JdbcValueReaders.getLocalDateTime(rs, "PAYMENT_DATE"))
                    .bankBin(rs.getString("BANK_BIN"))
                    .accountNo(rs.getString("ACCOUNT_NO"))
                    .bankReferenceNo(rs.getString("BANK_REFERENCE_NO"))
                    .status(rs.getString("STATUS"))
                    .note(rs.getString("NOTE"))
                    .build();

    private static final RowMapper<TuitionSlipResponseDto> SLIP_INFO_ROW_MAPPER = (rs, rowNum) ->
            TuitionSlipResponseDto.builder()
                    .invoiceId(rs.getLong("ID"))
                    .invoiceCode(rs.getString("FEE_CODE"))
                    .month(JdbcValueReaders.getInteger(rs, "FEE_MONTH"))
                    .year(JdbcValueReaders.getInteger(rs, "FEE_YEAR"))
                    .classCode(rs.getString("CLASS_CODE"))
                    .className(rs.getString("CLASS_NAME"))
                    .studentCode(rs.getString("STUDENT_CODE"))
                    .studentName(rs.getString("STUDENT_NAME"))
                    .pricePerSession(JdbcValueReaders.getBigDecimal(rs, "PRICE_PER_SESSION"))
                    .totalSessions(JdbcValueReaders.getInteger(rs, "TOTAL_SESSIONS"))
                    .totalAmount(JdbcValueReaders.getBigDecimal(rs, "TOTAL_AMOUNT"))
                    .teacherComment(rs.getString("TEACHER_COMMENT"))
                    .footerWish(rs.getString("FOOTER_WISH"))
                    .slipLabel(rs.getString("SLIP_LABEL"))
                    .attendedDates(new ArrayList<>())
                    .build();

    private static final RowMapper<LocalDate> ATTENDANCE_DATE_ROW_MAPPER =
            (rs, rowNum) -> JdbcValueReaders.getLocalDate(rs, "ATTENDANCE_DATE");

    private final OracleProcExecutor oracleProcExecutor;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public TuitionFeeDetailResponse getFeeDetail(Long tuitionFeeId) {
        if (tuitionFeeId == null) {
            throw new OracleBusinessException("FEE_ID_REQUIRED", "Thiếu ID khoản học phí.");
        }

        SimpleJdbcCall call = oracleProcExecutor.createCall(PROC_NAME)
                .declareParameters(
                        new SqlParameter("P_TUITION_FEE_ID", Types.NUMERIC),
                        new SqlOutParameter("O_FEE_CURSOR", OracleTypes.CURSOR, FEE_ROW_MAPPER),
                        new SqlOutParameter("O_TRANSACTION_CURSOR", OracleTypes.CURSOR, TRANSACTION_ROW_MAPPER),
                        new SqlOutParameter("O_ERR_CODE", Types.VARCHAR),
                        new SqlOutParameter("O_ERR_MSG", Types.VARCHAR));

        Map<String, Object> in = new HashMap<>();
        in.put("P_TUITION_FEE_ID", tuitionFeeId);

        Map<String, Object> out = call.execute(in);
        oracleProcExecutor.validateResult(out);

        List<TuitionFeeDetailResponse> fees =
                ProcCursorReader.readCursor(out, "O_FEE_CURSOR", TuitionFeeDetailResponse.class);
        if (fees.isEmpty()) {
            throw new OracleBusinessException("FEE_NOT_FOUND",
                    "Không tìm thấy khoản học phí ID: " + tuitionFeeId);
        }

        TuitionFeeDetailResponse detail = fees.getFirst();
        detail.setTransactions(
                ProcCursorReader.readCursor(out, "O_TRANSACTION_CURSOR", PaymentTransactionDto.class));

        log.debug("{} trả về khoản phí {} với {} giao dịch",
                PROC_NAME, detail.getFeeCode(), detail.getTransactions().size());
        return detail;
    }

    @Override
    public TuitionSlipResponseDto getTuitionSlipData(Long invoiceId) {
        if (invoiceId == null) {
            throw new OracleBusinessException("FEE_ID_REQUIRED", "Thiếu ID khoản học phí.");
        }

        SimpleJdbcCall call = oracleProcExecutor.createCall(SLIP_PROC)
                .declareParameters(
                        new SqlParameter("P_INVOICE_ID", Types.NUMERIC),
                        new SqlOutParameter("O_INFO_CURSOR", OracleTypes.CURSOR, SLIP_INFO_ROW_MAPPER),
                        new SqlOutParameter("O_ATTENDANCE_CURSOR", OracleTypes.CURSOR, ATTENDANCE_DATE_ROW_MAPPER),
                        new SqlOutParameter("O_ERR_CODE", Types.VARCHAR),
                        new SqlOutParameter("O_ERR_MSG", Types.VARCHAR));

        Map<String, Object> out = call.execute(Map.of("P_INVOICE_ID", invoiceId));
        oracleProcExecutor.validateResult(out);

        List<TuitionSlipResponseDto> infos =
                ProcCursorReader.readCursor(out, "O_INFO_CURSOR", TuitionSlipResponseDto.class);
        if (infos.isEmpty()) {
            throw new OracleBusinessException("FEE_NOT_FOUND",
                    "Không tìm thấy khoản học phí ID: " + invoiceId);
        }

        TuitionSlipResponseDto slip = infos.getFirst();
        List<LocalDate> dates =
                ProcCursorReader.readCursor(out, "O_ATTENDANCE_CURSOR", LocalDate.class);
        List<String> badges = new ArrayList<>();
        for (LocalDate date : dates) {
            if (date != null) {
                badges.add(date.format(DAY_MONTH));
            }
        }
        slip.setAttendedDates(badges);
        return slip;
    }

    @Override
    public String nextTuitionFeeCode() {
        String code = jdbcTemplate.queryForObject("SELECT FN_NEXT_BIZ_CODE('TUITION') FROM DUAL", String.class);
        if (code == null || code.isBlank()) {
            throw new OracleBusinessException("FEE_CODE_GENERATE_FAILED",
                    "Không sinh được mã khoản học phí từ SYS_CODE_RULES.");
        }
        return code.trim();
    }
}
