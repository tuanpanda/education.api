-- =============================================================================
-- MIGRATION V24_1__fee_partial_columns: PHIEU GOI CHUA DU 10 BUOI (contract fee-range-api v1.4 muc 13.2)
--
-- Them cot vao FIN_TUITION_FEES:
--   IS_PARTIAL NUMBER(1) DEFAULT 0 NOT NULL + CK_FEES_IS_PARTIAL CHECK (IS_PARTIAL IN (0,1)):
--     1 = phieu thu chua du goi 10 buoi (allowPartial + ly do, chi generate-range ghi 1),
--     0 = con lai. Phieu cu tu nhan DEFAULT 0, khong can backfill.
--   PARTIAL_REASON VARCHAR2(200 CHAR) nullable: ly do thu chua du goi.
-- Moi cot them rieng trong 1 khoi con: cot nao da co thi bao "already exists" va them tiep cot con thieu.
-- Chi DDL (tu commit), khong co lenh ghi du lieu.
--
-- Spec: docs/SPEC-fee-date-range.md muc 13. Huong dan chay: docs/RUN-V24-fee-partial.md
-- Moi file 1 khoi lenh. Chay lai an toan (idempotent).
--
-- THU TU CHAY: V24_1 roi V24_2 (sau toan bo V23).
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe /nolog
--   CONNECT <DB_USER>@//localhost:1521/<SERVICE>   (mat khau nhap tai prompt)
--   @V24_1__fee_partial_columns.sql
--
-- KIEM TRA:
--   SELECT COLUMN_NAME, DATA_TYPE, CHAR_LENGTH, NULLABLE, DATA_DEFAULT FROM USER_TAB_COLUMNS
--    WHERE TABLE_NAME = 'FIN_TUITION_FEES' AND COLUMN_NAME IN ('IS_PARTIAL', 'PARTIAL_REASON');
--
-- ROLLBACK:
--   ALTER TABLE FIN_TUITION_FEES DROP (IS_PARTIAL, PARTIAL_REASON);
--   (chay sau khi da rollback V24_2 CK_FEES_PARTIAL_REASON; DROP IS_PARTIAL xoa luon CK_FEES_IS_PARTIAL)
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED

BEGIN
    BEGIN
        EXECUTE IMMEDIATE q'[ALTER TABLE FIN_TUITION_FEES ADD (IS_PARTIAL NUMBER(1) DEFAULT 0 NOT NULL CONSTRAINT CK_FEES_IS_PARTIAL CHECK (IS_PARTIAL IN (0, 1)))]';
        DBMS_OUTPUT.PUT_LINE('FIN_TUITION_FEES.IS_PARTIAL: created');
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE IN (-1430, -2264) THEN
                DBMS_OUTPUT.PUT_LINE('FIN_TUITION_FEES.IS_PARTIAL: already exists');
            ELSE
                RAISE;
            END IF;
    END;
    BEGIN
        EXECUTE IMMEDIATE q'[ALTER TABLE FIN_TUITION_FEES ADD (PARTIAL_REASON VARCHAR2(200 CHAR))]';
        DBMS_OUTPUT.PUT_LINE('FIN_TUITION_FEES.PARTIAL_REASON: created');
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE IN (-1430) THEN
                DBMS_OUTPUT.PUT_LINE('FIN_TUITION_FEES.PARTIAL_REASON: already exists');
            ELSE
                RAISE;
            END IF;
    END;
END;
/
