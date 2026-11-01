-- =============================================================================
-- MIGRATION V23_2__fee_period_check: THU HOC PHI THEO KHOANG NGAY
--
-- Rang buoc CK_FEES_PERIOD: PERIOD_FROM <= PERIOD_TO.
--
-- Spec: docs/SPEC-fee-date-range.md muc 4. Huong dan chay: docs/RUN-V23-fee-range.md
-- Moi file 1 khoi lenh. Chay lai an toan (idempotent).
--
-- THU TU CHAY: sau V23_1.
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe /nolog
--   CONNECT <DB_USER>@//localhost:1521/<SERVICE>   (mat khau nhap tai prompt)
--   @V23_2__fee_period_check.sql
--
-- ROLLBACK:
--   ALTER TABLE FIN_TUITION_FEES DROP CONSTRAINT CK_FEES_PERIOD;
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED

BEGIN
    EXECUTE IMMEDIATE q'[ALTER TABLE FIN_TUITION_FEES ADD CONSTRAINT CK_FEES_PERIOD CHECK (PERIOD_FROM IS NULL OR PERIOD_TO IS NULL OR PERIOD_FROM <= PERIOD_TO)]';
    DBMS_OUTPUT.PUT_LINE('CK_FEES_PERIOD: created');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE IN (-2264) THEN
            DBMS_OUTPUT.PUT_LINE('CK_FEES_PERIOD: already exists');
        ELSE
            RAISE;
        END IF;
END;
/
