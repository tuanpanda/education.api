package com.education.base.repository.custom.impl;

import com.education.base.exception.OracleBusinessException;
import com.education.base.repository.base.OracleProcExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.CallableStatement;
import java.sql.Types;

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
}
