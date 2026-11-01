-- =============================================================================
-- MIGRATION V25_1__fee_text_columns_char: COT VAN BAN NGUOI DUNG NHAP -> VARCHAR2(n CHAR)
--
-- BUG-FEE-RANGE-04: TEACHER_COMMENT 1000 ky tu co dau -> ORA-12899 vi cot khai bao VARCHAR2(1000) khi
-- NLS_LENGTH_SEMANTICS = BYTE (mac dinh) = 1000 BYTE, ma 1 ky tu tieng Viet chiem 2-3 byte UTF-8.
-- Contract fee-range muc 16: gioi han tinh theo KY TU.
--
-- Doi sang CHAR (giu nguyen so n, chi doi don vi) - chi khi CHAR_USED = 'B':
--   FIN_TUITION_FEES.NOTE             VARCHAR2(255)  -> VARCHAR2(255 CHAR)   (V1)
--   FIN_TUITION_FEES.CANCEL_REASON    VARCHAR2(255)  -> VARCHAR2(255 CHAR)   (V14_1)
--   FIN_TUITION_FEES.TEACHER_COMMENT  VARCHAR2(1000) -> VARCHAR2(1000 CHAR)  (V10)
--   FIN_TUITION_FEES.FOOTER_WISH      VARCHAR2(500)  -> VARCHAR2(500 CHAR)   (V10)
--   FIN_TUITION_FEES.SLIP_LABEL       VARCHAR2(50)   -> VARCHAR2(50 CHAR)    (V10)
--   FIN_PAYMENT_TRANSACTIONS.NOTE        VARCHAR2(255) -> VARCHAR2(255 CHAR) (V1)
--   FIN_PAYMENT_TRANSACTIONS.PAYER_NAME  VARCHAR2(150) -> VARCHAR2(150 CHAR) (V14_2)
--   FIN_PAYMENT_TRANSACTIONS.VOID_REASON VARCHAR2(255) -> VARCHAR2(255 CHAR) (V14_2)
-- Chi kiem tra (khong sua): FIN_TUITION_FEES.PARTIAL_REASON phai la VARCHAR2(200 CHAR) (V24_1);
--   neu chua co cot hoac chua la CHAR thi dung lai bang loi -20025 (chay V24 truoc).
-- So n moi = GREATEST(n hien tai, n mong doi): khong bao gio thu nho cot. Du lieu cu khong doi.
-- Cot khong ton tai -> bao "not found" va dung lai bang loi -20025.
-- Chi DDL (tu commit), khong co lenh ghi du lieu.
--
-- Spec: docs/SPEC-fee-date-range.md muc 16. Huong dan chay: docs/RUN-V25-fee-text-char.md
-- Moi file 1 khoi lenh. Chay lai an toan (idempotent): cot da CHAR in "already CHAR".
--
-- THU TU CHAY: sau V24_2.
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe /nolog
--   CONNECT <DB_USER>@//localhost:1521/<SERVICE>   (mat khau nhap tai prompt)
--   @V25_1__fee_text_columns_char.sql
--
-- KIEM TRA:
--   SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHAR_LENGTH, CHAR_USED FROM USER_TAB_COLUMNS
--    WHERE TABLE_NAME IN ('FIN_TUITION_FEES', 'FIN_PAYMENT_TRANSACTIONS') AND DATA_TYPE = 'VARCHAR2'
--    ORDER BY TABLE_NAME, COLUMN_NAME;
--
-- ROLLBACK (chi khi moi gia tri con vua so byte; neu khong se ORA-01441):
--   ALTER TABLE FIN_TUITION_FEES MODIFY (NOTE VARCHAR2(255 BYTE), CANCEL_REASON VARCHAR2(255 BYTE),
--     TEACHER_COMMENT VARCHAR2(1000 BYTE), FOOTER_WISH VARCHAR2(500 BYTE), SLIP_LABEL VARCHAR2(50 BYTE));
--   ALTER TABLE FIN_PAYMENT_TRANSACTIONS MODIFY (NOTE VARCHAR2(255 BYTE), PAYER_NAME VARCHAR2(150 BYTE),
--     VOID_REASON VARCHAR2(255 BYTE));
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED

DECLARE
    PROCEDURE TO_CHAR_SEMANTICS(P_TAB IN VARCHAR2, P_COL IN VARCHAR2, P_LEN IN NUMBER) IS
        V_USED     VARCHAR2(1);
        V_CHAR_LEN NUMBER;
        V_NEW_LEN  NUMBER;
    BEGIN
        SELECT CHAR_USED, CHAR_LENGTH
          INTO V_USED, V_CHAR_LEN
          FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = P_TAB
           AND COLUMN_NAME = P_COL
           AND DATA_TYPE = 'VARCHAR2';
        IF V_USED = 'C' THEN
            DBMS_OUTPUT.PUT_LINE(P_TAB || '.' || P_COL || ': already CHAR (' || V_CHAR_LEN || ' CHAR)');
        ELSE
            V_NEW_LEN := GREATEST(V_CHAR_LEN, P_LEN);
            EXECUTE IMMEDIATE 'ALTER TABLE ' || P_TAB || ' MODIFY (' || P_COL
                || ' VARCHAR2(' || V_NEW_LEN || ' CHAR))';
            DBMS_OUTPUT.PUT_LINE(P_TAB || '.' || P_COL || ': ' || V_CHAR_LEN || ' BYTE -> '
                || V_NEW_LEN || ' CHAR');
        END IF;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            DBMS_OUTPUT.PUT_LINE(P_TAB || '.' || P_COL || ': not found');
            RAISE_APPLICATION_ERROR(-20025, P_TAB || '.' || P_COL || ' not found');
    END TO_CHAR_SEMANTICS;

    PROCEDURE VERIFY_CHAR(P_TAB IN VARCHAR2, P_COL IN VARCHAR2, P_LEN IN NUMBER) IS
        V_USED     VARCHAR2(1);
        V_CHAR_LEN NUMBER;
    BEGIN
        SELECT CHAR_USED, CHAR_LENGTH
          INTO V_USED, V_CHAR_LEN
          FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = P_TAB
           AND COLUMN_NAME = P_COL
           AND DATA_TYPE = 'VARCHAR2';
        IF V_USED = 'C' AND V_CHAR_LEN >= P_LEN THEN
            DBMS_OUTPUT.PUT_LINE(P_TAB || '.' || P_COL || ': OK (' || V_CHAR_LEN || ' CHAR)');
        ELSE
            RAISE_APPLICATION_ERROR(-20025, P_TAB || '.' || P_COL || ' is ' || V_CHAR_LEN
                || ' ' || V_USED || ', expected ' || P_LEN || ' CHAR (run V24_1 first)');
        END IF;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20025, P_TAB || '.' || P_COL || ' not found (run V24_1 first)');
    END VERIFY_CHAR;
BEGIN
    VERIFY_CHAR('FIN_TUITION_FEES', 'PARTIAL_REASON', 200);
    TO_CHAR_SEMANTICS('FIN_TUITION_FEES', 'NOTE', 255);
    TO_CHAR_SEMANTICS('FIN_TUITION_FEES', 'CANCEL_REASON', 255);
    TO_CHAR_SEMANTICS('FIN_TUITION_FEES', 'TEACHER_COMMENT', 1000);
    TO_CHAR_SEMANTICS('FIN_TUITION_FEES', 'FOOTER_WISH', 500);
    TO_CHAR_SEMANTICS('FIN_TUITION_FEES', 'SLIP_LABEL', 50);
    TO_CHAR_SEMANTICS('FIN_PAYMENT_TRANSACTIONS', 'NOTE', 255);
    TO_CHAR_SEMANTICS('FIN_PAYMENT_TRANSACTIONS', 'PAYER_NAME', 150);
    TO_CHAR_SEMANTICS('FIN_PAYMENT_TRANSACTIONS', 'VOID_REASON', 255);
END;
/
