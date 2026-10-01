package com.education.base.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OracleErrorMessagesTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "ORA-00001: unique constraint (EDUCATION.UK_STUDENT_CODE) violated",
            "ORA-06502: PL/SQL: numeric or value error: character string buffer too small",
            "ORA-01400: cannot insert NULL into (\"EDUCATION\".\"EDU_STUDENTS\".\"FULL_NAME\")",
            "ORA-00942: table or view does not exist",
            "PLS-00201: identifier 'PRC_X' must be declared",
            "Loi: ORA-02291: integrity constraint (EDUCATION.FK_X) violated - parent key not found",
            "java.sql.SQLSyntaxErrorException: invalid identifier",
            "CallableStatementCallback; bad SQL grammar [{call PRC_X(?, ?)}]"
    })
    void rawDatabaseErrors_areReplacedByGenericMessage(String raw) {
        assertThat(OracleErrorMessages.isRawDatabaseError(raw)).isTrue();
        assertThat(OracleErrorMessages.toClientMessage(raw)).isEqualTo(OracleErrorMessages.GENERIC_MESSAGE);
    }

    @Test
    void userDefinedError_returnsCustomTextWithoutPrefix() {
        String raw = "ORA-20012: Ma nghiep vu khong hop le";

        assertThat(OracleErrorMessages.isRawDatabaseError(raw)).isFalse();
        assertThat(OracleErrorMessages.toClientMessage(raw)).isEqualTo("Ma nghiep vu khong hop le");
    }

    @Test
    void userDefinedError_dropsOracleCallStack() {
        String raw = "ORA-20014: Ma sinh ra vuot 30 ky tu: HS2026-000001\n"
                + "ORA-06512: at \"EDUCATION.FN_NEXT_CODE\", line 57\n"
                + "ORA-06512: at line 1";

        assertThat(OracleErrorMessages.toClientMessage(raw)).isEqualTo("Ma sinh ra vuot 30 ky tu: HS2026-000001");
    }

    @Test
    void userDefinedError_singleLineStack_isCutAtNextOraCode() {
        String raw = "ORA-20001: Hoc sinh da ton tai. ORA-06512: at line 12";

        assertThat(OracleErrorMessages.toClientMessage(raw)).isEqualTo("Hoc sinh da ton tai.");
    }

    @Test
    void userDefinedError_withEmptyText_fallsBackToGenericMessage() {
        assertThat(OracleErrorMessages.toClientMessage("ORA-20001: ")).isEqualTo(OracleErrorMessages.GENERIC_MESSAGE);
        assertThat(OracleErrorMessages.isRawDatabaseError("ORA-20001: ")).isTrue();
    }

    @Test
    void oracleCodeOutsideUserRange_isNotTreatedAsBusinessError() {
        // ORA-2xxxx ngoài dải 20000-20999 (ví dụ ORA-29283) vẫn là lỗi hệ thống.
        assertThat(OracleErrorMessages.toClientMessage("ORA-29283: invalid file operation"))
                .isEqualTo(OracleErrorMessages.GENERIC_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Khong tim thay khoan hoc phi ID: 15",
            "Tu ngay phai nho hon hoac bang den ngay.",
            "Không tìm thấy học sinh với ID: 99",
            "Mã lớp CLS-2026 đã tồn tại."
    })
    void businessMessages_areKeptAsIs(String message) {
        assertThat(OracleErrorMessages.isRawDatabaseError(message)).isFalse();
        assertThat(OracleErrorMessages.toClientMessage(message)).isEqualTo(message);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankMessage_returnsGenericMessage(String message) {
        assertThat(OracleErrorMessages.isRawDatabaseError(message)).isFalse();
        assertThat(OracleErrorMessages.toClientMessage(message)).isEqualTo(OracleErrorMessages.GENERIC_MESSAGE);
    }
}
