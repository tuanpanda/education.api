-- =============================================================================
-- EDUCATION - MIGRATION V14_3: TAI CHINH - BAO CAO, DASHBOARD, XUAT EXCEL
--
-- Stream C (feat/fin-reports). CHI Stream C sua file nay; stream khac khong sua.
--
-- Pham vi (Stream C, du kien) - CHI procedure / menu, KHONG doi bang:
--   * Sua PRC_RPT_DASHBOARD_METRICS: OVERDUE_FEES tinh ca STATUS = 'OVERDUE'; TOTAL_RECEIVABLE ro nghia (B7).
--   * Procedure moi: PRC_RPT_FINANCE_SUMMARY, PRC_RPT_DEBT_AGING, PRC_RPT_CLASS_COLLECTION,
--     PRC_RPT_STUDENT_LEDGER (chi doc FIN_TUITION_FEES / FIN_PAYMENT_TRANSACTIONS / EDU_*).
--   * Menu MENU_FINANCE_DASHBOARD (/finance/dashboard) va MENU_FINANCE_REPORT (/finance/reports) duoi DIR_FINANCE.
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
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V14_3__fin_reports.sql
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V14_3.1 Procedure bao cao ============

-- TODO(Stream C): CREATE OR REPLACE PROCEDURE PRC_RPT_DASHBOARD_METRICS / PRC_RPT_FINANCE_* ...

PROMPT ============ V14_3.2 Menu bao cao tai chinh ============

-- TODO(Stream C): bo comment va dieu chinh.
-- MERGE INTO SYS_MENUS t
-- USING (
--     SELECT x.MENU_CODE, x.MENU_NAME, x.PATH, x.ICON, x.SORT_ORDER, p.ID PARENT_ID
--       FROM (
--             SELECT 'MENU_FINANCE_DASHBOARD' MENU_CODE, 'Tổng quan tài chính' MENU_NAME, '/finance/dashboard' PATH,
--                    'gauge' ICON, 0 SORT_ORDER FROM DUAL UNION ALL
--             SELECT 'MENU_FINANCE_REPORT', 'Báo cáo tài chính', '/finance/reports', 'chart-line', 5 FROM DUAL
--            ) x
--       JOIN SYS_MENUS p ON p.MENU_CODE = 'DIR_FINANCE'
-- ) s ON (t.MENU_CODE = s.MENU_CODE)
-- WHEN MATCHED THEN UPDATE SET t.PARENT_ID = s.PARENT_ID, t.MENU_NAME = s.MENU_NAME, t.PATH = s.PATH,
--                              t.ICON = s.ICON, t.SORT_ORDER = s.SORT_ORDER, t.MENU_TYPE = 'MENU',
--                              t.IS_HIDDEN = 0, t.IS_DELETED = 0, t.STATUS = 'ACTIVE',
--                              t.UPDATED_AT = SYSTIMESTAMP, t.UPDATED_BY = 'V14_3_MIGRATION'
-- WHEN NOT MATCHED THEN INSERT (ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, PATH, ICON, SORT_ORDER,
--                               IS_HIDDEN, STATUS, IS_DELETED, CREATED_BY)
--                       VALUES (SEQ_SYS_MENUS.NEXTVAL, s.PARENT_ID, s.MENU_CODE, s.MENU_NAME, 'MENU', s.PATH, s.ICON,
--                               s.SORT_ORDER, 0, 'ACTIVE', 0, 'V14_3_MIGRATION');

PROMPT ============ V14_3.3 Chuc nang ============

-- MERGE INTO SYS_FUNCTIONS t
-- USING (
--     SELECT m.ID AS MENU_ID, x.FUNCTION_CODE, x.FUNCTION_NAME
--       FROM SYS_MENUS m
--       JOIN (
--             SELECT 'MENU_FINANCE_DASHBOARD' MENU_CODE, 'VIEW' FUNCTION_CODE, 'Xem danh sách' FUNCTION_NAME FROM DUAL UNION ALL
--             SELECT 'MENU_FINANCE_REPORT', 'VIEW', 'Xem danh sách' FROM DUAL UNION ALL
--             SELECT 'MENU_FINANCE_REPORT', 'EXPORT', 'Xuất dữ liệu' FROM DUAL
--            ) x ON x.MENU_CODE = m.MENU_CODE
--      WHERE m.IS_DELETED = 0
-- ) s ON (t.MENU_ID = s.MENU_ID AND t.FUNCTION_CODE = s.FUNCTION_CODE)
-- WHEN MATCHED THEN UPDATE SET t.FUNCTION_NAME = s.FUNCTION_NAME, t.IS_DELETED = 0
-- WHEN NOT MATCHED THEN INSERT (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
--                       VALUES (SEQ_SYS_FUNCTIONS.NEXTVAL, s.MENU_ID, s.FUNCTION_CODE, s.FUNCTION_NAME, 0);

PROMPT ============ V14_3.4 Phan quyen ============

-- ROLE_ADMIN: toan quyen tren cac menu cua script nay (sinh tu SYS_FUNCTIONS, khong liet ke tay).
-- MERGE INTO SYS_ROLE_MENU_PERMISSIONS t
-- USING (
--     SELECT r.ID AS ROLE_ID,
--            f.MENU_ID,
--            LISTAGG(f.FUNCTION_CODE, ',') WITHIN GROUP (ORDER BY f.ID) AS ALLOWED_FUNCTIONS
--       FROM SYS_FUNCTIONS f
--       JOIN SYS_MENUS m ON m.ID = f.MENU_ID AND m.IS_DELETED = 0
--                       AND m.MENU_CODE IN ('MENU_FINANCE_DASHBOARD', 'MENU_FINANCE_REPORT')
--       CROSS JOIN SYS_ROLES r
--      WHERE r.ROLE_CODE = 'ROLE_ADMIN'
--        AND f.IS_DELETED = 0
--      GROUP BY r.ID, f.MENU_ID
-- ) s ON (t.ROLE_ID = s.ROLE_ID AND t.MENU_ID = s.MENU_ID)
-- WHEN MATCHED THEN UPDATE SET t.ALLOWED_FUNCTIONS = s.ALLOWED_FUNCTIONS, t.UPDATED_AT = SYSTIMESTAMP,
--                              t.UPDATED_BY = 'V14_3_MIGRATION'
-- WHEN NOT MATCHED THEN INSERT (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
--                       VALUES (SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, s.ROLE_ID, s.MENU_ID, s.ALLOWED_FUNCTIONS, 'V14_3_MIGRATION');
--
-- ROLE_ACCOUNTANT: liet ke tuong minh (ALLOWED_FUNCTIONS ghi de toan bo gia tri cu cua menu do).
-- MERGE INTO SYS_ROLE_MENU_PERMISSIONS t
-- USING (
--     SELECT r.ID AS ROLE_ID, m.ID AS MENU_ID, x.ALLOWED_FUNCTIONS
--       FROM (
--             SELECT 'ROLE_ACCOUNTANT' ROLE_CODE, 'MENU_FINANCE_DASHBOARD' MENU_CODE, 'VIEW' ALLOWED_FUNCTIONS FROM DUAL UNION ALL
--             SELECT 'ROLE_ACCOUNTANT', 'MENU_FINANCE_REPORT', 'VIEW,EXPORT' FROM DUAL
--            ) x
--       JOIN SYS_ROLES r ON r.ROLE_CODE = x.ROLE_CODE
--       JOIN SYS_MENUS m ON m.MENU_CODE = x.MENU_CODE AND m.IS_DELETED = 0
-- ) s ON (t.ROLE_ID = s.ROLE_ID AND t.MENU_ID = s.MENU_ID)
-- WHEN MATCHED THEN UPDATE SET t.ALLOWED_FUNCTIONS = s.ALLOWED_FUNCTIONS, t.UPDATED_AT = SYSTIMESTAMP,
--                              t.UPDATED_BY = 'V14_3_MIGRATION'
-- WHEN NOT MATCHED THEN INSERT (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
--                       VALUES (SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, s.ROLE_ID, s.MENU_ID, s.ALLOWED_FUNCTIONS, 'V14_3_MIGRATION');

COMMIT;

PROMPT ============ V14_3 DONE ============
EXIT
