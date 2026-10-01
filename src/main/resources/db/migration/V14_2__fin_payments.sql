-- =============================================================================
-- EDUCATION - MIGRATION V14_2: TAI CHINH - GIAO DICH, PHIEU THU, HUY / HOAN TIEN
--
-- Stream B (feat/fin-payments). CHI Stream B sua file nay; stream khac khong sua.
--
-- Pham vi:
--   * FIN_PAYMENT_TRANSACTIONS - cot moi:
--       RECEIPT_NO         VARCHAR2(30)                         so phieu thu / phieu chi, UNIQUE khi khac NULL
--                                                               (index UQ_FIN_TRANS_RECEIPT_NO; dong cu de NULL)
--       TRANSACTION_TYPE   VARCHAR2(20) DEFAULT 'PAYMENT' NOT NULL  CK_TRANS_TYPE: 'PAYMENT' | 'REFUND'
--       PAYER_NAME         VARCHAR2(150)                        nguoi nop (PAYMENT) / nguoi nhan (REFUND)
--       VOIDED_AT          TIMESTAMP(6)                         thoi diem huy
--       VOIDED_BY          VARCHAR2(50)                         nguoi huy (USERNAME)
--       VOID_REASON        VARCHAR2(255)                        ly do huy
--       REF_TRANSACTION_ID NUMBER(19)                           dong REFUND -> giao dich PAYMENT goc
--                                                               (FK_TRANS_REF_TRANS tu tham chieu, IDX_TRANS_REF;
--                                                               CK_TRANS_REFUND_REF: REFUND bat buoc co REF)
--   * CK_TRANS_STATUS tao lai: 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED' | 'VOIDED'.
--   * CK_TRANS_AMOUNT (AMOUNT > 0) GIU NGUYEN: hoan tien la so DUONG voi TRANSACTION_TYPE = 'REFUND'.
--   * Backfill TRANSACTION_TYPE = 'PAYMENT' cho cac dong cu.
--   * SYS_CODE_RULES 'RECEIPT': PT{YYYY}{MM}{SEQ}, SEQ 5 chu so, reset theo thang (mau V10.2 'TUITION').
--     Ung dung cap so qua FN_NEXT_BIZ_CODE('RECEIPT') cho ca phieu thu (PAYMENT) va phieu chi (REFUND).
--   * PRC_GET_TUITION_FEE_DETAIL (CREATE OR REPLACE, ban V1 muc 4.3): O_TRANSACTION_CURSOR them RECEIPT_NO,
--     TRANSACTION_TYPE, PAYER_NAME, VOIDED_AT/BY, VOID_REASON, REF_TRANSACTION_ID, CREATED_BY, CREATED_AT;
--     REMAINING_AMOUNT khong am. Tham so / ma loi giu nguyen; kiem tra VALID sau khi bien dich.
--   * SYS_FUNCTIONS: MENU_PAYMENT_HISTORY:VOID, MENU_PAYMENT_HISTORY:REFUND (khong tao menu moi);
--     cap cho ROLE_ADMIN (toan bo chuc nang cua menu) va ROLE_ACCOUNTANT (THEM VOID,REFUND vao quyen hien co,
--     khong ghi de cac chuc nang khac).
--
-- MA TRANG THAI / LOAI GIAO DICH (Stream C va cac bao cao doc FIN_PAYMENT_TRANSACTIONS can theo dung):
--   * TRANSACTION_TYPE = 'PAYMENT' : thu tien. STATUS:
--       'SUCCESS'  - da thu, con hieu luc;
--       'REFUNDED' - da HOAN TOAN BO (van la tien da thu; duoc bu tru boi cac dong REFUND cua no);
--       'VOIDED'   - da HUY (nhap sai...): KHONG tinh vao doanh thu / so da thu;
--       'PENDING' / 'FAILED' - khong tinh.
--   * TRANSACTION_TYPE = 'REFUND'  : hoan tien, AMOUNT duong, REF_TRANSACTION_ID = giao dich PAYMENT goc,
--       STATUS = 'SUCCESS' (dong REFUND khong bi huy qua API).
--   * So thuc thu cua khoan phi (= FIN_TUITION_FEES.PAID_AMOUNT sau moi lan huy / hoan):
--       SUM(AMOUNT) WHERE TRANSACTION_TYPE = 'PAYMENT' AND STATUS IN ('SUCCESS', 'REFUNDED')
--     - SUM(AMOUNT) WHERE TRANSACTION_TYPE = 'REFUND'  AND STATUS = 'SUCCESS'        (IS_DELETED = 0).
--
-- QUY UOC CHUNG CHO V14_x (kiem tra tu dong boi V14ScriptConventionTest):
--   * WHENEVER SQLERROR EXIT ... ROLLBACK truoc lenh dau tien; script ket thuc bang COMMIT + EXIT.
--   * Chay lai nhieu lan an toan (idempotent): DDL kiem tra USER_TAB_COLUMNS / USER_CONSTRAINTS / USER_INDEXES
--     truoc khi tao; seed dung MERGE.
--   * Menu: MERGE ON (MENU_CODE) va ID = SEQ_SYS_MENUS.NEXTVAL - KHONG dung ID co dinh.
--     Chuc nang: SEQ_SYS_FUNCTIONS.NEXTVAL; phan quyen: SEQ_SYS_ROLE_MENU_PERM.NEXTVAL.
--   * File UTF-8 co dau tieng Viet: chay voi NLS_LANG=AMERICAN_AMERICA.AL32UTF8.
--   * Thu tu chay: V14_1 -> V14_2 -> V14_3 (sau V13_3). Script nay khong phu thuoc doi tuong cua V14_1 / V14_3.
--   * Chay TRUOC khi deploy backend co Stream B (entity FIN_PAYMENT_TRANSACTIONS da map cac cot moi).
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V14_2__fin_payments.sql
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V14_2.1 Cot giao dich / phieu thu ============

DECLARE
    PROCEDURE ADD_COLUMN(p_column IN VARCHAR2, p_ddl IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = 'FIN_PAYMENT_TRANSACTIONS'
           AND COLUMN_NAME = p_column;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE FIN_PAYMENT_TRANSACTIONS ADD (' || p_ddl || ')';
            DBMS_OUTPUT.PUT_LINE('FIN_PAYMENT_TRANSACTIONS.' || p_column || ': added');
        ELSE
            DBMS_OUTPUT.PUT_LINE('FIN_PAYMENT_TRANSACTIONS.' || p_column || ': already exists');
        END IF;
    END;
BEGIN
    ADD_COLUMN('RECEIPT_NO',         'RECEIPT_NO VARCHAR2(30)');
    ADD_COLUMN('TRANSACTION_TYPE',   q'[TRANSACTION_TYPE VARCHAR2(20) DEFAULT 'PAYMENT' NOT NULL]');
    ADD_COLUMN('PAYER_NAME',         'PAYER_NAME VARCHAR2(150)');
    ADD_COLUMN('VOIDED_AT',          'VOIDED_AT TIMESTAMP(6)');
    ADD_COLUMN('VOIDED_BY',          'VOIDED_BY VARCHAR2(50)');
    ADD_COLUMN('VOID_REASON',        'VOID_REASON VARCHAR2(255)');
    ADD_COLUMN('REF_TRANSACTION_ID', 'REF_TRANSACTION_ID NUMBER(19)');
END;
/

PROMPT ============ V14_2.2 Backfill TRANSACTION_TYPE ============

UPDATE FIN_PAYMENT_TRANSACTIONS
   SET TRANSACTION_TYPE = 'PAYMENT'
 WHERE TRANSACTION_TYPE IS NULL;

COMMIT;

PROMPT ============ V14_2.3 Rang buoc / index ============

DECLARE
    PROCEDURE ADD_CONSTRAINT(p_name IN VARCHAR2, p_ddl IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM USER_CONSTRAINTS
         WHERE TABLE_NAME = 'FIN_PAYMENT_TRANSACTIONS'
           AND CONSTRAINT_NAME = p_name;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE FIN_PAYMENT_TRANSACTIONS ADD CONSTRAINT ' || p_name || ' ' || p_ddl;
            DBMS_OUTPUT.PUT_LINE(p_name || ': created');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_name || ': already exists');
        END IF;
    END;

    PROCEDURE ADD_INDEX(p_name IN VARCHAR2, p_ddl IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = p_name;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
            DBMS_OUTPUT.PUT_LINE(p_name || ': created');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_name || ': already exists');
        END IF;
    END;
BEGIN
    ADD_CONSTRAINT('CK_TRANS_TYPE', q'[CHECK (TRANSACTION_TYPE IN ('PAYMENT', 'REFUND'))]');
    ADD_CONSTRAINT('FK_TRANS_REF_TRANS',
                   'FOREIGN KEY (REF_TRANSACTION_ID) REFERENCES FIN_PAYMENT_TRANSACTIONS (ID)');
    ADD_CONSTRAINT('CK_TRANS_REFUND_REF',
                   q'[CHECK (TRANSACTION_TYPE = 'PAYMENT' OR REF_TRANSACTION_ID IS NOT NULL)]');

    -- Unique khi khac NULL: Oracle khong dua khoa NULL toan bo vao index, nen cac dong cu (NULL) khong vi pham.
    ADD_INDEX('UQ_FIN_TRANS_RECEIPT_NO',
              'CREATE UNIQUE INDEX UQ_FIN_TRANS_RECEIPT_NO ON FIN_PAYMENT_TRANSACTIONS (RECEIPT_NO)');
    ADD_INDEX('IDX_TRANS_REF',
              'CREATE INDEX IDX_TRANS_REF ON FIN_PAYMENT_TRANSACTIONS (REF_TRANSACTION_ID)');
END;
/

PROMPT ============ V14_2.4 CK_TRANS_STATUS them VOIDED ============

DECLARE
    v_condition VARCHAR2(4000);
    v_found     BOOLEAN := FALSE;
BEGIN
    FOR c IN (SELECT SEARCH_CONDITION
                FROM USER_CONSTRAINTS
               WHERE TABLE_NAME = 'FIN_PAYMENT_TRANSACTIONS'
                 AND CONSTRAINT_NAME = 'CK_TRANS_STATUS') LOOP
        v_condition := c.SEARCH_CONDITION;   -- LONG -> VARCHAR2 trong PL/SQL
        v_found := TRUE;
    END LOOP;

    IF v_found AND INSTR(UPPER(v_condition), '''VOIDED''') > 0 THEN
        DBMS_OUTPUT.PUT_LINE('CK_TRANS_STATUS: already includes VOIDED');
    ELSE
        IF v_found THEN
            EXECUTE IMMEDIATE 'ALTER TABLE FIN_PAYMENT_TRANSACTIONS DROP CONSTRAINT CK_TRANS_STATUS';
            DBMS_OUTPUT.PUT_LINE('CK_TRANS_STATUS: dropped');
        END IF;
        EXECUTE IMMEDIATE q'[ALTER TABLE FIN_PAYMENT_TRANSACTIONS ADD CONSTRAINT CK_TRANS_STATUS
                             CHECK (STATUS IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED', 'VOIDED'))]';
        DBMS_OUTPUT.PUT_LINE('CK_TRANS_STATUS: created with VOIDED');
    END IF;
END;
/

COMMENT ON COLUMN FIN_PAYMENT_TRANSACTIONS.RECEIPT_NO IS 'So phieu thu / phieu chi (SYS_CODE_RULES RECEIPT), unique khi khac NULL';
COMMENT ON COLUMN FIN_PAYMENT_TRANSACTIONS.TRANSACTION_TYPE IS 'PAYMENT = thu tien; REFUND = hoan tien (AMOUNT duong)';
COMMENT ON COLUMN FIN_PAYMENT_TRANSACTIONS.PAYER_NAME IS 'Nguoi nop tien (PAYMENT) / nguoi nhan tien (REFUND)';
COMMENT ON COLUMN FIN_PAYMENT_TRANSACTIONS.VOIDED_AT IS 'Thoi diem huy giao dich (STATUS = VOIDED)';
COMMENT ON COLUMN FIN_PAYMENT_TRANSACTIONS.VOIDED_BY IS 'Nguoi huy giao dich (USERNAME)';
COMMENT ON COLUMN FIN_PAYMENT_TRANSACTIONS.VOID_REASON IS 'Ly do huy giao dich';
COMMENT ON COLUMN FIN_PAYMENT_TRANSACTIONS.REF_TRANSACTION_ID IS 'Dong REFUND: ID giao dich PAYMENT goc';

PROMPT ============ V14_2.5 Quy luat ma phieu thu ============

MERGE INTO SYS_CODE_RULES t
USING (
    SELECT 'RECEIPT' AS RULE_CODE,
           'FINANCE' AS MODULE_NAME,
           'FIN_PAYMENT_TRANSACTIONS' AS TABLE_NAME,
           'PT' AS PREFIX,
           '{PREFIX}{YYYY}{MM}{SEQ}' AS PATTERN,
           5 AS SEQ_LENGTH,
           'MONTH' AS RESET_CYCLE
      FROM DUAL
) s
ON (t.RULE_CODE = s.RULE_CODE)
WHEN MATCHED THEN
    UPDATE SET t.PATTERN = s.PATTERN,
               t.PREFIX = s.PREFIX,
               t.SEQ_LENGTH = s.SEQ_LENGTH,
               t.RESET_CYCLE = s.RESET_CYCLE,
               t.IS_ACTIVE = 1,
               t.IS_DELETED = 0,
               t.UPDATED_AT = SYSTIMESTAMP
WHEN NOT MATCHED THEN
    INSERT (ID, RULE_CODE, MODULE_NAME, TABLE_NAME, PREFIX, PATTERN, SEQ_LENGTH, RESET_CYCLE,
            LAST_RESET_KEY, LAST_SEQ, IS_ACTIVE, IS_DELETED, CREATED_AT)
    VALUES (SEQ_SYS_CODE_RULES.NEXTVAL, s.RULE_CODE, s.MODULE_NAME, s.TABLE_NAME, s.PREFIX,
            s.PATTERN, s.SEQ_LENGTH, s.RESET_CYCLE, NULL, 0, 1, 0, SYSTIMESTAMP);

PROMPT SYS_CODE_RULES RECEIPT: OK

PROMPT ============ V14_2.6 Chuc nang VOID / REFUND ============

MERGE INTO SYS_FUNCTIONS t
USING (
    SELECT m.ID AS MENU_ID, x.FUNCTION_CODE, x.FUNCTION_NAME
      FROM SYS_MENUS m
      JOIN (
            SELECT 'MENU_PAYMENT_HISTORY' MENU_CODE, 'VOID' FUNCTION_CODE, 'Hủy giao dịch' FUNCTION_NAME FROM DUAL UNION ALL
            SELECT 'MENU_PAYMENT_HISTORY', 'REFUND', 'Hoàn tiền' FROM DUAL
           ) x ON x.MENU_CODE = m.MENU_CODE
     WHERE m.IS_DELETED = 0
) s ON (t.MENU_ID = s.MENU_ID AND t.FUNCTION_CODE = s.FUNCTION_CODE)
WHEN MATCHED THEN UPDATE SET t.FUNCTION_NAME = s.FUNCTION_NAME, t.IS_DELETED = 0
WHEN NOT MATCHED THEN INSERT (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
                      VALUES (SEQ_SYS_FUNCTIONS.NEXTVAL, s.MENU_ID, s.FUNCTION_CODE, s.FUNCTION_NAME, 0);

PROMPT ============ V14_2.7 Phan quyen ============

-- ROLE_ADMIN: toan quyen tren MENU_PAYMENT_HISTORY (sinh tu SYS_FUNCTIONS, khong liet ke tay).
MERGE INTO SYS_ROLE_MENU_PERMISSIONS t
USING (
    SELECT r.ID AS ROLE_ID,
           f.MENU_ID,
           LISTAGG(f.FUNCTION_CODE, ',') WITHIN GROUP (ORDER BY f.ID) AS ALLOWED_FUNCTIONS
      FROM SYS_FUNCTIONS f
      JOIN SYS_MENUS m ON m.ID = f.MENU_ID AND m.IS_DELETED = 0
                      AND m.MENU_CODE IN ('MENU_PAYMENT_HISTORY')
      CROSS JOIN SYS_ROLES r
     WHERE r.ROLE_CODE = 'ROLE_ADMIN'
       AND f.IS_DELETED = 0
     GROUP BY r.ID, f.MENU_ID
) s ON (t.ROLE_ID = s.ROLE_ID AND t.MENU_ID = s.MENU_ID)
WHEN MATCHED THEN UPDATE SET t.ALLOWED_FUNCTIONS = s.ALLOWED_FUNCTIONS, t.UPDATED_AT = SYSTIMESTAMP,
                             t.UPDATED_BY = 'V14_2_MIGRATION'
WHEN NOT MATCHED THEN INSERT (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
                      VALUES (SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, s.ROLE_ID, s.MENU_ID, s.ALLOWED_FUNCTIONS, 'V14_2_MIGRATION');

-- ROLE_ACCOUNTANT: THEM VOID, REFUND vao quyen hien co cua MENU_PAYMENT_HISTORY (giu nguyen cac chuc nang
-- khac, ke ca chuc nang do nguoi dung tu cap). Chua co dong quyen thi tao voi bo mac dinh cua V1 + VOID,REFUND.
MERGE INTO SYS_ROLE_MENU_PERMISSIONS t
USING (
    SELECT r.ID AS ROLE_ID, m.ID AS MENU_ID
      FROM SYS_ROLES r
      JOIN SYS_MENUS m ON m.MENU_CODE = 'MENU_PAYMENT_HISTORY' AND m.IS_DELETED = 0
     WHERE r.ROLE_CODE = 'ROLE_ACCOUNTANT'
) s ON (t.ROLE_ID = s.ROLE_ID AND t.MENU_ID = s.MENU_ID)
WHEN MATCHED THEN UPDATE SET
    t.ALLOWED_FUNCTIONS = LTRIM(
        t.ALLOWED_FUNCTIONS
        || CASE WHEN INSTR(',' || t.ALLOWED_FUNCTIONS || ',', ',VOID,') = 0 THEN ',VOID' END
        || CASE WHEN INSTR(',' || t.ALLOWED_FUNCTIONS || ',', ',REFUND,') = 0 THEN ',REFUND' END,
        ','),
    t.UPDATED_AT = SYSTIMESTAMP,
    t.UPDATED_BY = 'V14_2_MIGRATION'
WHEN NOT MATCHED THEN INSERT (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
                      VALUES (SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, s.ROLE_ID, s.MENU_ID,
                              'VIEW,CREATE,APPROVE,EXPORT,VOID,REFUND', 'V14_2_MIGRATION');

COMMIT;

PROMPT ============ V14_2.8 PRC_GET_TUITION_FEE_DETAIL them cot giao dich ============

-- ----------------------------------------------------------------------------
-- PRC_GET_TUITION_FEE_DETAIL - Chi tiet mot khoan hoc phi (thay the ban V1, muc 4.3)
--     Giu nguyen tham so, ma loi va O_FEE_CURSOR cua V1; REMAINING_AMOUNT khong am (GREATEST(..., 0), cung
--     quy tac FeeStatusCalculator.remaining).
--     O_TRANSACTION_CURSOR them cac cot V14_2: RECEIPT_NO, TRANSACTION_TYPE, PAYER_NAME, VOIDED_AT, VOIDED_BY,
--     VOID_REASON, REF_TRANSACTION_ID, CREATED_BY, CREATED_AT - de man hinh chi tiet khoan phi hien dung loai
--     (thu / hoan), trang thai (VOIDED) va so phieu. Dong REFUND co AMOUNT duong: ung dung tu hien dau tru.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE PRC_GET_TUITION_FEE_DETAIL(
    P_TUITION_FEE_ID     IN  NUMBER,
    O_FEE_CURSOR         OUT SYS_REFCURSOR,
    O_TRANSACTION_CURSOR OUT SYS_REFCURSOR,
    O_ERR_CODE           OUT VARCHAR2,
    O_ERR_MSG            OUT VARCHAR2
) AS
    V_EXISTS NUMBER;
BEGIN
    IF P_TUITION_FEE_ID IS NULL THEN
        O_ERR_CODE := 'FEE_ID_REQUIRED';
        O_ERR_MSG  := 'Thieu ID khoan hoc phi.';
        RETURN;
    END IF;

    SELECT COUNT(*)
      INTO V_EXISTS
      FROM FIN_TUITION_FEES
     WHERE ID = P_TUITION_FEE_ID
       AND IS_DELETED = 0;

    IF V_EXISTS = 0 THEN
        O_ERR_CODE := 'FEE_NOT_FOUND';
        O_ERR_MSG  := 'Khong tim thay khoan hoc phi ID: ' || P_TUITION_FEE_ID;
        RETURN;
    END IF;

    OPEN O_FEE_CURSOR FOR
        SELECT f.ID,
               f.FEE_CODE,
               f.STUDENT_ID,
               s.STUDENT_CODE,
               s.FULL_NAME AS STUDENT_NAME,
               f.CLASS_ID,
               c.CLASS_CODE,
               c.CLASS_NAME,
               f.TOTAL_AMOUNT,
               f.DISCOUNT_AMOUNT,
               f.PAID_AMOUNT,
               GREATEST(f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT - f.PAID_AMOUNT, 0) AS REMAINING_AMOUNT,
               f.DUE_DATE,
               f.STATUS,
               f.NOTE,
               f.CREATED_AT,
               f.UPDATED_AT
          FROM FIN_TUITION_FEES f
          JOIN EDU_STUDENTS s ON s.ID = f.STUDENT_ID
          LEFT JOIN EDU_CLASSES c ON c.ID = f.CLASS_ID
         WHERE f.ID = P_TUITION_FEE_ID
           AND f.IS_DELETED = 0;

    OPEN O_TRANSACTION_CURSOR FOR
        SELECT t.ID,
               t.TRANSACTION_CODE,
               t.TUITION_FEE_ID,
               t.AMOUNT,
               t.PAYMENT_METHOD,
               t.PAYMENT_DATE,
               t.BANK_BIN,
               t.ACCOUNT_NO,
               t.BANK_REFERENCE_NO,
               t.STATUS,
               t.NOTE,
               t.RECEIPT_NO,
               t.TRANSACTION_TYPE,
               t.PAYER_NAME,
               t.VOIDED_AT,
               t.VOIDED_BY,
               t.VOID_REASON,
               t.REF_TRANSACTION_ID,
               t.CREATED_BY,
               t.CREATED_AT
          FROM FIN_PAYMENT_TRANSACTIONS t
         WHERE t.TUITION_FEE_ID = P_TUITION_FEE_ID
           AND t.IS_DELETED = 0
         ORDER BY t.PAYMENT_DATE DESC, t.ID DESC;

    O_ERR_CODE := '0';
    O_ERR_MSG  := 'SUCCESS';
EXCEPTION
    WHEN OTHERS THEN
        IF O_FEE_CURSOR IS NOT NULL AND O_FEE_CURSOR%ISOPEN THEN CLOSE O_FEE_CURSOR; END IF;
        IF O_TRANSACTION_CURSOR IS NOT NULL AND O_TRANSACTION_CURSOR%ISOPEN THEN CLOSE O_TRANSACTION_CURSOR; END IF;
        O_ERR_CODE := TO_CHAR(SQLCODE);
        O_ERR_MSG  := SUBSTR(SQLERRM, 1, 255);
END PRC_GET_TUITION_FEE_DETAIL;
/
SHOW ERRORS PROCEDURE PRC_GET_TUITION_FEE_DETAIL

-- CREATE OR REPLACE PROCEDURE loi bien dich chi bao Warning (khong kich hoat WHENEVER SQLERROR): kiem tra tuong minh.
DECLARE
    V_STATUS VARCHAR2(10);
BEGIN
    SELECT NVL(MAX(STATUS), 'MISSING') INTO V_STATUS
      FROM USER_OBJECTS
     WHERE OBJECT_NAME = 'PRC_GET_TUITION_FEE_DETAIL'
       AND OBJECT_TYPE = 'PROCEDURE';
    DBMS_OUTPUT.PUT_LINE('PRC_GET_TUITION_FEE_DETAIL: ' || V_STATUS);
    IF V_STATUS <> 'VALID' THEN
        RAISE_APPLICATION_ERROR(-20001, 'V14_2: PRC_GET_TUITION_FEE_DETAIL khong hop le (' || V_STATUS || ').');
    END IF;
END;
/

PROMPT ============ V14_2 Kiem tra ============

SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, NULLABLE
  FROM USER_TAB_COLUMNS
 WHERE TABLE_NAME = 'FIN_PAYMENT_TRANSACTIONS'
   AND COLUMN_NAME IN ('RECEIPT_NO', 'TRANSACTION_TYPE', 'PAYER_NAME', 'VOIDED_AT', 'VOIDED_BY',
                       'VOID_REASON', 'REF_TRANSACTION_ID')
 ORDER BY COLUMN_NAME;

SELECT r.ROLE_CODE, p.ALLOWED_FUNCTIONS
  FROM SYS_ROLE_MENU_PERMISSIONS p
  JOIN SYS_ROLES r ON r.ID = p.ROLE_ID
  JOIN SYS_MENUS m ON m.ID = p.MENU_ID
 WHERE m.MENU_CODE = 'MENU_PAYMENT_HISTORY'
   AND r.ROLE_CODE IN ('ROLE_ADMIN', 'ROLE_ACCOUNTANT');

PROMPT ============ V14_2 DONE ============
EXIT
