-- =============================================================================
-- MIGRATION V23_3__seq_fee_sessions: THU HOC PHI THEO KHOANG NGAY
--
-- Sequence SEQ_FIN_TUITION_FEE_SESSIONS cho bang FIN_TUITION_FEE_SESSIONS.
--
-- Spec: docs/SPEC-fee-date-range.md muc 4. Huong dan chay: docs/RUN-V23-fee-range.md
-- Moi file 1 khoi lenh. Chay lai an toan (idempotent).
--
-- THU TU CHAY: sau V23_2.
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe /nolog
--   CONNECT <DB_USER>@//localhost:1521/<SERVICE>   (mat khau nhap tai prompt)
--   @V23_3__seq_fee_sessions.sql
--
-- ROLLBACK:
--   DROP SEQUENCE SEQ_FIN_TUITION_FEE_SESSIONS;
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED

BEGIN
    EXECUTE IMMEDIATE q'[CREATE SEQUENCE SEQ_FIN_TUITION_FEE_SESSIONS START WITH 1 INCREMENT BY 1 NOCACHE]';
    DBMS_OUTPUT.PUT_LINE('SEQ_FIN_TUITION_FEE_SESSIONS: created');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE IN (-955) THEN
            DBMS_OUTPUT.PUT_LINE('SEQ_FIN_TUITION_FEE_SESSIONS: already exists');
        ELSE
            RAISE;
        END IF;
END;
/
