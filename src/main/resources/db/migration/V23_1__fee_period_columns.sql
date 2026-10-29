-- =============================================================================
-- MIGRATION V23_1__fee_period_columns: THU HOC PHI THEO KHOANG NGAY
--
-- Them cot vao FIN_TUITION_FEES:
--   PERIOD_FROM, PERIOD_TO (DATE, nullable): ky thu theo khoang ngay.
--   IS_RANGE NUMBER(1) DEFAULT 0 NOT NULL + CK_FEES_IS_RANGE CHECK (IS_RANGE IN (0,1)):
--     1 = phieu sinh boi "thu theo khoang ngay" (chi generate-range ghi 1),
--     0 = phieu thang cu / khoan tao tay. Phieu cu tu nhan DEFAULT 0, khong can backfill.
-- Moi cot them rieng trong 1 khoi con: cot nao da co thi bao "already exists" va them tiep cot con thieu.
--
-- Spec: docs/SPEC-fee-date-range.md muc 4. Huong dan chay: docs/RUN-V23-fee-range.md
-- Moi file 1 khoi lenh. Chay lai an toan (idempotent).
--
-- THU TU CHAY: dau tien cua V23 (sau V22_4).
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe /nolog
--   CONNECT <DB_USER>@//localhost:1521/<SERVICE>   (mat khau nhap tai prompt)
--   @V23_1__fee_period_columns.sql
--
-- ROLLBACK:
--   ALTER TABLE FIN_TUITION_FEES DROP (PERIOD_FROM, PERIOD_TO, IS_RANGE);
--   (DROP cot IS_RANGE xoa luon rang buoc CK_FEES_IS_RANGE; chay sau khi da rollback V23_2 CK_FEES_PERIOD)
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED

BEGIN
    BEGIN
        EXECUTE IMMEDIATE q'[ALTER TABLE FIN_TUITION_FEES ADD (PERIOD_FROM DATE)]';
        DBMS_OUTPUT.PUT_LINE('FIN_TUITION_FEES.PERIOD_FROM: created');
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE IN (-1430) THEN
                DBMS_OUTPUT.PUT_LINE('FIN_TUITION_FEES.PERIOD_FROM: already exists');
            ELSE
                RAISE;
            END IF;
    END;
    BEGIN
        EXECUTE IMMEDIATE q'[ALTER TABLE FIN_TUITION_FEES ADD (PERIOD_TO DATE)]';
        DBMS_OUTPUT.PUT_LINE('FIN_TUITION_FEES.PERIOD_TO: created');
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE IN (-1430) THEN
                DBMS_OUTPUT.PUT_LINE('FIN_TUITION_FEES.PERIOD_TO: already exists');
            ELSE
                RAISE;
            END IF;
    END;
    BEGIN
        EXECUTE IMMEDIATE q'[ALTER TABLE FIN_TUITION_FEES ADD (IS_RANGE NUMBER(1) DEFAULT 0 NOT NULL CONSTRAINT CK_FEES_IS_RANGE CHECK (IS_RANGE IN (0, 1)))]';
        DBMS_OUTPUT.PUT_LINE('FIN_TUITION_FEES.IS_RANGE: created');
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE IN (-1430, -2264) THEN
                DBMS_OUTPUT.PUT_LINE('FIN_TUITION_FEES.IS_RANGE: already exists');
            ELSE
                RAISE;
            END IF;
    END;
END;
/
