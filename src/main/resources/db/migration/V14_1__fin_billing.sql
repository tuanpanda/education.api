-- =============================================================================
-- EDUCATION - MIGRATION V14_1: TAI CHINH - TINH PHI, KHOAN HOC PHI, MIEN GIAM
--
-- Stream A (feat/fin-billing). CHI Stream A sua file nay; stream khac khong sua.
--
-- Pham vi (Stream A, du kien):
--   * EDU_CLASSES.FEE_TYPE (PER_SESSION | PER_MONTH | PER_COURSE, mac dinh PER_SESSION) + CHECK;
--     EDU_CLASSES.TUITION_AMOUNT la don gia theo FEE_TYPE.
--   * FIN_TUITION_FEES: CANCEL_REASON, DISCOUNT_NOTE (+ VERSION neu dung optimistic lock).
--   * Bang FIN_STUDENT_DISCOUNTS (+ SEQ_FIN_STUDENT_DISCOUNTS).
--   * PRC_GET_TUITION_SLIP_DATA: dem PRESENT + LATE (B1).
--   * Menu MENU_FEE_DISCOUNT (/finance/discounts) duoi DIR_FINANCE; chuc nang CANCEL cho MENU_TUITION_FEE.
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
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V14_1__fin_billing.sql
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V14_1.1 Cot / bang tinh phi ============

-- TODO(Stream A): ALTER TABLE EDU_CLASSES / FIN_TUITION_FEES, CREATE TABLE FIN_STUDENT_DISCOUNTS.

PROMPT ============ V14_1.2 Procedure phieu hoc phi ============

-- TODO(Stream A): CREATE OR REPLACE PROCEDURE PRC_GET_TUITION_SLIP_DATA (PRESENT + LATE).

PROMPT ============ V14_1.3 Menu MENU_FEE_DISCOUNT ============

-- TODO(Stream A): bo comment va dieu chinh.
-- MERGE INTO SYS_MENUS t
-- USING (
--     SELECT x.MENU_CODE, x.MENU_NAME, x.PATH, x.ICON, x.SORT_ORDER, p.ID PARENT_ID
--       FROM (
--             SELECT 'MENU_FEE_DISCOUNT' MENU_CODE, 'Miễn giảm học phí' MENU_NAME, '/finance/discounts' PATH,
--                    'percent' ICON, 4 SORT_ORDER FROM DUAL
--            ) x
--       JOIN SYS_MENUS p ON p.MENU_CODE = 'DIR_FINANCE'
-- ) s ON (t.MENU_CODE = s.MENU_CODE)
-- WHEN MATCHED THEN UPDATE SET t.PARENT_ID = s.PARENT_ID, t.MENU_NAME = s.MENU_NAME, t.PATH = s.PATH,
--                              t.ICON = s.ICON, t.SORT_ORDER = s.SORT_ORDER, t.MENU_TYPE = 'MENU',
--                              t.IS_HIDDEN = 0, t.IS_DELETED = 0, t.STATUS = 'ACTIVE',
--                              t.UPDATED_AT = SYSTIMESTAMP, t.UPDATED_BY = 'V14_1_MIGRATION'
-- WHEN NOT MATCHED THEN INSERT (ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, PATH, ICON, SORT_ORDER,
--                               IS_HIDDEN, STATUS, IS_DELETED, CREATED_BY)
--                       VALUES (SEQ_SYS_MENUS.NEXTVAL, s.PARENT_ID, s.MENU_CODE, s.MENU_NAME, 'MENU', s.PATH, s.ICON,
--                               s.SORT_ORDER, 0, 'ACTIVE', 0, 'V14_1_MIGRATION');

PROMPT ============ V14_1.4 Chuc nang ============

-- MERGE INTO SYS_FUNCTIONS t
-- USING (
--     SELECT m.ID AS MENU_ID, x.FUNCTION_CODE, x.FUNCTION_NAME
--       FROM SYS_MENUS m
--       JOIN (
--             SELECT 'MENU_FEE_DISCOUNT' MENU_CODE, 'VIEW' FUNCTION_CODE, 'Xem danh sách' FUNCTION_NAME FROM DUAL UNION ALL
--             SELECT 'MENU_FEE_DISCOUNT', 'CREATE', 'Thêm mới' FROM DUAL UNION ALL
--             SELECT 'MENU_FEE_DISCOUNT', 'UPDATE', 'Cập nhật' FROM DUAL UNION ALL
--             SELECT 'MENU_FEE_DISCOUNT', 'DELETE', 'Xóa' FROM DUAL UNION ALL
--             SELECT 'MENU_TUITION_FEE', 'CANCEL', 'Hủy khoản phí' FROM DUAL
--            ) x ON x.MENU_CODE = m.MENU_CODE
--      WHERE m.IS_DELETED = 0
-- ) s ON (t.MENU_ID = s.MENU_ID AND t.FUNCTION_CODE = s.FUNCTION_CODE)
-- WHEN MATCHED THEN UPDATE SET t.FUNCTION_NAME = s.FUNCTION_NAME, t.IS_DELETED = 0
-- WHEN NOT MATCHED THEN INSERT (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
--                       VALUES (SEQ_SYS_FUNCTIONS.NEXTVAL, s.MENU_ID, s.FUNCTION_CODE, s.FUNCTION_NAME, 0);

PROMPT ============ V14_1.5 Phan quyen ============

-- ROLE_ADMIN: toan quyen tren cac menu cua script nay (sinh tu SYS_FUNCTIONS, khong liet ke tay).
-- MERGE INTO SYS_ROLE_MENU_PERMISSIONS t
-- USING (
--     SELECT r.ID AS ROLE_ID,
--            f.MENU_ID,
--            LISTAGG(f.FUNCTION_CODE, ',') WITHIN GROUP (ORDER BY f.ID) AS ALLOWED_FUNCTIONS
--       FROM SYS_FUNCTIONS f
--       JOIN SYS_MENUS m ON m.ID = f.MENU_ID AND m.IS_DELETED = 0
--                       AND m.MENU_CODE IN ('MENU_FEE_DISCOUNT', 'MENU_TUITION_FEE')
--       CROSS JOIN SYS_ROLES r
--      WHERE r.ROLE_CODE = 'ROLE_ADMIN'
--        AND f.IS_DELETED = 0
--      GROUP BY r.ID, f.MENU_ID
-- ) s ON (t.ROLE_ID = s.ROLE_ID AND t.MENU_ID = s.MENU_ID)
-- WHEN MATCHED THEN UPDATE SET t.ALLOWED_FUNCTIONS = s.ALLOWED_FUNCTIONS, t.UPDATED_AT = SYSTIMESTAMP,
--                              t.UPDATED_BY = 'V14_1_MIGRATION'
-- WHEN NOT MATCHED THEN INSERT (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
--                       VALUES (SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, s.ROLE_ID, s.MENU_ID, s.ALLOWED_FUNCTIONS, 'V14_1_MIGRATION');
--
-- ROLE_ACCOUNTANT: liet ke tuong minh (ALLOWED_FUNCTIONS ghi de toan bo gia tri cu cua menu do).
-- MERGE INTO SYS_ROLE_MENU_PERMISSIONS t
-- USING (
--     SELECT r.ID AS ROLE_ID, m.ID AS MENU_ID, x.ALLOWED_FUNCTIONS
--       FROM (
--             SELECT 'ROLE_ACCOUNTANT' ROLE_CODE, 'MENU_FEE_DISCOUNT' MENU_CODE, 'VIEW,CREATE,UPDATE,DELETE' ALLOWED_FUNCTIONS FROM DUAL UNION ALL
--             SELECT 'ROLE_ACCOUNTANT', 'MENU_TUITION_FEE', 'VIEW,CREATE,UPDATE,DELETE,EXPORT,CANCEL' FROM DUAL
--            ) x
--       JOIN SYS_ROLES r ON r.ROLE_CODE = x.ROLE_CODE
--       JOIN SYS_MENUS m ON m.MENU_CODE = x.MENU_CODE AND m.IS_DELETED = 0
-- ) s ON (t.ROLE_ID = s.ROLE_ID AND t.MENU_ID = s.MENU_ID)
-- WHEN MATCHED THEN UPDATE SET t.ALLOWED_FUNCTIONS = s.ALLOWED_FUNCTIONS, t.UPDATED_AT = SYSTIMESTAMP,
--                              t.UPDATED_BY = 'V14_1_MIGRATION'
-- WHEN NOT MATCHED THEN INSERT (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
--                       VALUES (SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, s.ROLE_ID, s.MENU_ID, s.ALLOWED_FUNCTIONS, 'V14_1_MIGRATION');

COMMIT;

PROMPT ============ V14_1 DONE ============
EXIT
