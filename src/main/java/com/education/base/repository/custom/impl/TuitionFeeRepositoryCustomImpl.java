package com.education.base.repository.custom.impl;

import com.education.base.dto.response.PaymentTransactionDto;
import com.education.base.dto.response.TuitionFeeDetailResponse;
import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.base.OracleProcExecutor;
import com.education.base.repository.custom.TuitionFeeRepositoryCustom;
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
 * Triển khai {@link TuitionFeeRepositoryCustom} bằng Standalone Procedure
 * {@code PRC_GET_TUITION_FEE_DETAIL}.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TuitionFeeRepositoryCustomImpl implements TuitionFeeRepositoryCustom {

    private static final String PROC_NAME = "PRC_GET_TUITION_FEE_DETAIL";

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

    private final OracleProcExecutor oracleProcExecutor;

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
}
