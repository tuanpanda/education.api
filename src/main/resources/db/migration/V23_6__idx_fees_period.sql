-- =============================================================================
-- MIGRATION V23_6__idx_fees_period: THU HOC PHI THEO KHOANG NGAY
--
-- Index IDX_FEES_PERIOD (CLASS_ID, PERIOD_FROM, PERIOD_TO) tren FIN_TUITION_FEES.
--
-- Spec: docs/SPEC-fee-date-range.md muc 4. Huong dan chay: docs/RUN-V23-fee-range.md
-- Moi file 1 khoi lenh. Chay lai an toan (idempotent).
--
-- THU TU CHAY: sau V23_5.
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe /nolog
--   CONNECT <DB_USER>@//localhost:1521/<SERVICE>   (mat khau nhap tai prompt)
--   @V23_6__idx_fees_period.sql
--
-- ROLLBACK:
--   DROP INDEX IDX_FEES_PERIOD;
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED

BEGIN
    EXECUTE IMMEDIATE q'[CREATE INDEX IDX_FEES_PERIOD ON FIN_TUITION_FEES (CLASS_ID, PERIOD_FROM, PERIOD_TO)]';
    DBMS_OUTPUT.PUT_LINE('IDX_FEES_PERIOD: created');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE IN (-955, -1408) THEN
            DBMS_OUTPUT.PUT_LINE('IDX_FEES_PERIOD: already exists');
        ELSE
            RAISE;
        END IF;
END;
/
