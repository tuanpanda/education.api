-- =============================================================================
-- MIGRATION V23_5__idx_fee_sessions_fee: THU HOC PHI THEO KHOANG NGAY
--
-- Index IDX_FEE_SESSIONS_FEE (FEE_ID, SESSION_DATE): doc ngay tren phieu, xoa dong khi huy phieu.
--
-- Spec: docs/SPEC-fee-date-range.md muc 4. Huong dan chay: docs/RUN-V23-fee-range.md
-- Moi file 1 khoi lenh. Chay lai an toan (idempotent).
--
-- THU TU CHAY: sau V23_4.
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe /nolog
--   CONNECT <DB_USER>@//localhost:1521/<SERVICE>   (mat khau nhap tai prompt)
--   @V23_5__idx_fee_sessions_fee.sql
--
-- ROLLBACK:
--   DROP INDEX IDX_FEE_SESSIONS_FEE;
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED

BEGIN
    EXECUTE IMMEDIATE q'[CREATE INDEX IDX_FEE_SESSIONS_FEE ON FIN_TUITION_FEE_SESSIONS (FEE_ID, SESSION_DATE)]';
    DBMS_OUTPUT.PUT_LINE('IDX_FEE_SESSIONS_FEE: created');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE IN (-955, -1408) THEN
            DBMS_OUTPUT.PUT_LINE('IDX_FEE_SESSIONS_FEE: already exists');
        ELSE
            RAISE;
        END IF;
END;
/
