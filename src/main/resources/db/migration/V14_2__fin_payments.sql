-- =============================================================================
-- EDUCATION - MIGRATION V14_2: TAI CHINH - GIAO DICH, PHIEU THU, HUY / HOAN TIEN
--
-- Stream B (feat/fin-payments). CHI Stream B sua file nay; stream khac khong sua.
--
-- Pham vi (Stream B, du kien):
--   * FIN_PAYMENT_TRANSACTIONS: RECEIPT_NO (unique), TRANSACTION_TYPE (PAYMENT | REFUND, mac dinh PAYMENT),
--     PAYER_NAME, VOID_REASON, VOIDED_AT, VOIDED_BY, REF_TRANSACTION_ID (FK tu tham chieu).
--   * SYS_CODE_RULES 'RECEIPT' (PT{YYYY}{MM}{SEQ}, reset theo thang) - mau V10.2.
--   * Chuc nang VOID, REFUND cho MENU_PAYMENT_HISTORY (khong tao menu moi).
--
-- QUY UOC CHUNG CHO V14_x (kiem tra tu dong boi V14ScriptConventionTest):
--   * WHENEVER SQLERROR EXIT ... ROLLBACK truoc lenh dau tien; script ket thuc bang COMMIT + EXIT.
--   * Chay lai nhieu lan an toan (idempotent): DDL bat loi "da ton tai" (ORA-00955 / -01430 / -02260 /
--     -02275 / -01408), seed dung MERGE.
--   * Menu: MERGE ON (MENU_CODE) va ID = SEQ_SYS_MENUS.NEXTVAL - KHONG dung ID co dinh.
--     Chuc nang: SEQ_SYS_FUNCTIONS.NEXTVAL; phan quyen: SEQ_SYS_ROLE_MENU_PERM.NEXTVAL.
--   * ROLE_ADMIN duoc cap moi chuc nang cua menu do script nay tao; vai tro khac liet ke tuong minh.
--   * File UTF-8 co dau tieng Viet: chay voi NLS_LANG=AMERICAN_AMERICA.AL32UTF8.
--   * Thu tu chay: V14_1 -> V14_2 -> V14_3 (sau V13_3). Moi script doc lap, khong phu thuoc doi tuong
--     cua script V14 khac.
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V14_2__fin_payments.sql
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V14_2.1 Cot giao dich / phieu thu ============

-- TODO(Stream B): ALTER TABLE FIN_PAYMENT_TRANSACTIONS ..., unique index RECEIPT_NO.

PROMPT ============ V14_2.2 Quy luat ma phieu thu ============

-- TODO(Stream B): MERGE INTO SYS_CODE_RULES (RULE_CODE = 'RECEIPT') theo mau V10__tuition_slip.sql (V10.2).

PROMPT ============ V14_2.3 Chuc nang VOID / REFUND ============

-- MERGE INTO SYS_FUNCTIONS t
-- USING (
--     SELECT m.ID AS MENU_ID, x.FUNCTION_CODE, x.FUNCTION_NAME
--       FROM SYS_MENUS m
--       JOIN (
--             SELECT 'MENU_PAYMENT_HISTORY' MENU_CODE, 'VOID' FUNCTION_CODE, 'Hủy giao dịch' FUNCTION_NAME FROM DUAL UNION ALL
--             SELECT 'MENU_PAYMENT_HISTORY', 'REFUND', 'Hoàn tiền' FROM DUAL
--            ) x ON x.MENU_CODE = m.MENU_CODE
--      WHERE m.IS_DELETED = 0
-- ) s ON (t.MENU_ID = s.MENU_ID AND t.FUNCTION_CODE = s.FUNCTION_CODE)
-- WHEN MATCHED THEN UPDATE SET t.FUNCTION_NAME = s.FUNCTION_NAME, t.IS_DELETED = 0
-- WHEN NOT MATCHED THEN INSERT (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
--                       VALUES (SEQ_SYS_FUNCTIONS.NEXTVAL, s.MENU_ID, s.FUNCTION_CODE, s.FUNCTION_NAME, 0);

PROMPT ============ V14_2.4 Phan quyen ============

-- ROLE_ADMIN: toan quyen tren cac menu cua script nay (sinh tu SYS_FUNCTIONS, khong liet ke tay).
-- MERGE INTO SYS_ROLE_MENU_PERMISSIONS t
-- USING (
--     SELECT r.ID AS ROLE_ID,
--            f.MENU_ID,
--            LISTAGG(f.FUNCTION_CODE, ',') WITHIN GROUP (ORDER BY f.ID) AS ALLOWED_FUNCTIONS
--       FROM SYS_FUNCTIONS f
--       JOIN SYS_MENUS m ON m.ID = f.MENU_ID AND m.IS_DELETED = 0
--                       AND m.MENU_CODE IN ('MENU_PAYMENT_HISTORY')
--       CROSS JOIN SYS_ROLES r
--      WHERE r.ROLE_CODE = 'ROLE_ADMIN'
--        AND f.IS_DELETED = 0
--      GROUP BY r.ID, f.MENU_ID
-- ) s ON (t.ROLE_ID = s.ROLE_ID AND t.MENU_ID = s.MENU_ID)
-- WHEN MATCHED THEN UPDATE SET t.ALLOWED_FUNCTIONS = s.ALLOWED_FUNCTIONS, t.UPDATED_AT = SYSTIMESTAMP,
--                              t.UPDATED_BY = 'V14_2_MIGRATION'
-- WHEN NOT MATCHED THEN INSERT (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
--                       VALUES (SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, s.ROLE_ID, s.MENU_ID, s.ALLOWED_FUNCTIONS, 'V14_2_MIGRATION');
--
-- ROLE_ACCOUNTANT: liet ke tuong minh (ALLOWED_FUNCTIONS ghi de toan bo gia tri cu cua menu do).
-- MERGE INTO SYS_ROLE_MENU_PERMISSIONS t
-- USING (
--     SELECT r.ID AS ROLE_ID, m.ID AS MENU_ID, x.ALLOWED_FUNCTIONS
--       FROM (
--             SELECT 'ROLE_ACCOUNTANT' ROLE_CODE, 'MENU_PAYMENT_HISTORY' MENU_CODE, 'VIEW,CREATE,APPROVE,EXPORT,VOID' ALLOWED_FUNCTIONS FROM DUAL
--            ) x
--       JOIN SYS_ROLES r ON r.ROLE_CODE = x.ROLE_CODE
--       JOIN SYS_MENUS m ON m.MENU_CODE = x.MENU_CODE AND m.IS_DELETED = 0
-- ) s ON (t.ROLE_ID = s.ROLE_ID AND t.MENU_ID = s.MENU_ID)
-- WHEN MATCHED THEN UPDATE SET t.ALLOWED_FUNCTIONS = s.ALLOWED_FUNCTIONS, t.UPDATED_AT = SYSTIMESTAMP,
--                              t.UPDATED_BY = 'V14_2_MIGRATION'
-- WHEN NOT MATCHED THEN INSERT (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
--                       VALUES (SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, s.ROLE_ID, s.MENU_ID, s.ALLOWED_FUNCTIONS, 'V14_2_MIGRATION');

COMMIT;

PROMPT ============ V14_2 DONE ============
EXIT
