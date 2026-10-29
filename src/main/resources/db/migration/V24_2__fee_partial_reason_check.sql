-- =============================================================================
-- MIGRATION V24_2__fee_partial_reason_check: RANG BUOC PHIEU GOI CHUA DU 10 BUOI
--
-- CK_FEES_PARTIAL_REASON tren FIN_TUITION_FEES:
--   IS_PARTIAL = 0 thi PARTIAL_REASON IS NULL;
--   IS_PARTIAL = 1 thi IS_RANGE = 1 va PARTIAL_REASON IS NOT NULL.
-- Du lieu cu (IS_PARTIAL = 0, PARTIAL_REASON NULL sau V24_1) hop le; neu ORA-02293 thi co dong sai,
-- tra bang SELECT o muc KIEM TRA truoc khi chay lai.
--
-- Spec: docs/SPEC-fee-date-range.md muc 13. Huong dan chay: docs/RUN-V24-fee-partial.md
-- Moi file 1 khoi lenh. Chay lai an toan (idempotent).
--
-- THU TU CHAY: sau V24_1.
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe /nolog
--   CONNECT <DB_USER>@//localhost:1521/<SERVICE>   (mat khau nhap tai prompt)
--   @V24_2__fee_partial_reason_check.sql
--
-- KIEM TRA:
--   SELECT CONSTRAINT_NAME, STATUS, VALIDATED FROM USER_CONSTRAINTS
--    WHERE TABLE_NAME = 'FIN_TUITION_FEES' AND CONSTRAINT_NAME IN ('CK_FEES_IS_PARTIAL', 'CK_FEES_PARTIAL_REASON');
--   SELECT COUNT(*) FROM FIN_TUITION_FEES WHERE (IS_PARTIAL = 0 AND PARTIAL_REASON IS NOT NULL)
--    OR (IS_PARTIAL = 1 AND (IS_RANGE <> 1 OR PARTIAL_REASON IS NULL));
--
-- ROLLBACK:
--   ALTER TABLE FIN_TUITION_FEES DROP CONSTRAINT CK_FEES_PARTIAL_REASON;
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED

BEGIN
    EXECUTE IMMEDIATE q'[ALTER TABLE FIN_TUITION_FEES ADD CONSTRAINT CK_FEES_PARTIAL_REASON CHECK ((IS_PARTIAL = 0 AND PARTIAL_REASON IS NULL) OR (IS_PARTIAL = 1 AND IS_RANGE = 1 AND PARTIAL_REASON IS NOT NULL))]';
    DBMS_OUTPUT.PUT_LINE('CK_FEES_PARTIAL_REASON: created');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE IN (-2264) THEN
            DBMS_OUTPUT.PUT_LINE('CK_FEES_PARTIAL_REASON: already exists');
        ELSE
            RAISE;
        END IF;
END;
/
