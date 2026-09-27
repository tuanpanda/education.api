package com.education.base.repository.base;

import com.education.base.exception.OracleBusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OracleProcExecutorTest {

    private OracleProcExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new OracleProcExecutor(new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                throw new SQLException("Stub DataSource - unit test không kết nối Oracle.");
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                throw new SQLException("Stub DataSource - unit test không kết nối Oracle.");
            }
        });
    }

    @Test
    void createCall_setsProcedureNameWithoutCatalog() {
        SimpleJdbcCall call = executor.createCall("PRC_SEARCH_STUDENTS");

        assertThat(call.getProcedureName()).isEqualTo("PRC_SEARCH_STUDENTS");
        assertThat(call.getCatalogName())
                .as("Standalone Procedure không thuộc Package nên không được set catalog")
                .isNull();
    }

    @Test
    void validateResult_successWhenErrCodeIsZero() {
        Map<String, Object> out = new HashMap<>();
        out.put("O_ERR_CODE", OracleProcExecutor.SUCCESS_ERR_CODE);
        out.put("O_ERR_MSG", "SUCCESS");

        assertThatCode(() -> executor.validateResult(out)).doesNotThrowAnyException();
    }

    @Test
    void validateResult_trimsErrCodeBeforeComparing() {
        assertThatCode(() -> executor.validateResult(Map.of("O_ERR_CODE", " 0 "))).doesNotThrowAnyException();
    }

    @Test
    void validateResult_throwsWhenErrCodeIsNotZero() {
        Map<String, Object> out = new HashMap<>();
        out.put("O_ERR_CODE", "-1");
        out.put("O_ERR_MSG", "ORA-06502: numeric or value error");

        assertThatThrownBy(() -> executor.validateResult(out))
                .isInstanceOf(OracleBusinessException.class)
                .satisfies(ex -> {
                    OracleBusinessException business = (OracleBusinessException) ex;
                    assertThat(business.getErrorCode()).isEqualTo("-1");
                    assertThat(business.getMessage()).isEqualTo("ORA-06502: numeric or value error");
                });
    }

    @Test
    void validateResult_usesFallbackMessageWhenErrMsgMissing() {
        assertThatThrownBy(() -> executor.validateResult(Map.of("O_ERR_CODE", "9")))
                .isInstanceOf(OracleBusinessException.class)
                .hasMessage("Lỗi xử lý dữ liệu tại Oracle Procedure.");
    }

    @Test
    void validateResult_nullOutput_throwsGenericOracleError() {
        assertThatThrownBy(() -> executor.validateResult(null))
                .isInstanceOf(OracleBusinessException.class)
                .satisfies(ex -> assertThat(((OracleBusinessException) ex).getErrorCode()).isEqualTo("-1"));
    }

    @Test
    void validateResult_missingErrCode_throwsGenericOracleError() {
        assertThatThrownBy(() -> executor.validateResult(Map.of("O_CURSOR", "x")))
                .isInstanceOf(OracleBusinessException.class)
                .satisfies(ex -> {
                    OracleBusinessException business = (OracleBusinessException) ex;
                    assertThat(business.getErrorCode()).isEqualTo("-1");
                    assertThat(business.getMessage()).isEqualTo("Thiếu O_ERR_CODE từ Oracle Procedure.");
                });
    }

    @Test
    void validateResult_blankErrCode_throwsGenericOracleError() {
        assertThatThrownBy(() -> executor.validateResult(Map.of("O_ERR_CODE", "  ")))
                .isInstanceOf(OracleBusinessException.class)
                .extracting(ex -> ((OracleBusinessException) ex).getErrorCode())
                .isEqualTo("-1");
    }
}
