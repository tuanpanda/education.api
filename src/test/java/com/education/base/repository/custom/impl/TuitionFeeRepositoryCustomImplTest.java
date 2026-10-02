package com.education.base.repository.custom.impl;

import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.base.OracleProcExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import com.education.base.dto.response.PaymentTransactionDto;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TuitionFeeRepositoryCustomImplTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TuitionFeeRepositoryCustomImpl repository =
            new TuitionFeeRepositoryCustomImpl(mock(OracleProcExecutor.class), jdbcTemplate);

    @Test
    @SuppressWarnings("unchecked")
    void nextTuitionFeeCode_callsFunctionAsPlsqlCall_notInsideSelect() throws Exception {
        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getString(1)).thenReturn(" HP2026100001 ");
        when(jdbcTemplate.execute(eq(TuitionFeeRepositoryCustomImpl.NEXT_FEE_CODE_CALL),
                any(CallableStatementCallback.class))).thenAnswer(inv ->
                ((CallableStatementCallback<String>) inv.getArgument(1)).doInCallableStatement(cs));

        assertThat(repository.nextTuitionFeeCode()).isEqualTo("HP2026100001");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(sql.capture(), any(CallableStatementCallback.class));
        assertThat(sql.getValue()).isEqualTo("{? = call FN_NEXT_BIZ_CODE(?)}");
        verify(cs).registerOutParameter(1, Types.VARCHAR);
        verify(cs).setString(2, "TUITION");
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(String.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void nextTuitionFeeCode_blankResult_throwsBusinessError() {
        when(jdbcTemplate.execute(anyString(), any(CallableStatementCallback.class))).thenReturn(" ");

        assertThatThrownBy(repository::nextTuitionFeeCode)
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("FEE_CODE_GENERATE_FAILED");
    }

    @Test
    void transactionRowMapper_readsV14_2Columns() throws Exception {
        Map<String, Object> row = new HashMap<>();
        row.put("ID", 9L);
        row.put("TRANSACTION_CODE", "RFD9");
        row.put("TUITION_FEE_ID", new BigDecimal("4"));
        row.put("AMOUNT", new BigDecimal("200000"));
        row.put("STATUS", "SUCCESS");
        row.put("RECEIPT_NO", "PT20261000009");
        row.put("TRANSACTION_TYPE", "REFUND");
        row.put("PAYER_NAME", "Phụ huynh A");
        row.put("REF_TRANSACTION_ID", new BigDecimal("7"));
        row.put("VOID_REASON", null);
        row.put("CREATED_BY", "ketoan");
        row.put("CREATED_AT", Timestamp.valueOf("2026-10-01 09:00:00"));

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong(anyString())).thenAnswer(inv -> ((Number) row.get(inv.<String>getArgument(0))).longValue());
        when(rs.getObject(anyString())).thenAnswer(inv -> row.get(inv.<String>getArgument(0)));
        when(rs.getString(anyString())).thenAnswer(inv -> (String) row.get(inv.<String>getArgument(0)));
        when(rs.getBigDecimal(anyString())).thenAnswer(inv -> (BigDecimal) row.get(inv.<String>getArgument(0)));
        when(rs.getTimestamp(anyString())).thenAnswer(inv -> (Timestamp) row.get(inv.<String>getArgument(0)));

        PaymentTransactionDto dto = TuitionFeeRepositoryCustomImpl.TRANSACTION_ROW_MAPPER.mapRow(rs, 0);

        assertThat(dto.getTransactionType()).isEqualTo("REFUND");
        assertThat(dto.getReceiptNo()).isEqualTo("PT20261000009");
        assertThat(dto.getPayerName()).isEqualTo("Phụ huynh A");
        assertThat(dto.getRefTransactionId()).isEqualTo(7L);
        assertThat(dto.getCreatedBy()).isEqualTo("ketoan");
        assertThat(dto.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 0));
        assertThat(dto.getVoidedAt()).isNull();
    }
}
