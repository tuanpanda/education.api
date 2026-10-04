-- =============================================================================
-- EDUCATION - MIGRATION V17_2: MENU QUAN TRI "TAI KHOAN HOC SINH" (MENU_STUDENT_ACCOUNT)
--
-- Giai doan 0, Stream A (feat/portal-p0-core). CHI Stream A sua file nay.
--
-- Script nay lam:
--   V17_2.1 Kiem tra truoc: co thu muc DIR_SYSTEM ("Quan tri he thong") va vai tro ROLE_ADMIN.
--   V17_2.2 Menu MENU_STUDENT_ACCOUNT ('Tài khoản học sinh', /system/student-accounts) duoi DIR_SYSTEM.
--   V17_2.3 Chuc nang: VIEW / CREATE / RESET_PASSWORD / LOCK
--           (backend: Permissions.STUDENT_ACCOUNT_*; LOCK dung cho ca khoa va mo khoa).
--   V17_2.4 Phan quyen: ROLE_ADMIN toan quyen MENU_STUDENT_ACCOUNT (vai tro khac do quan tri vien tu cap).
--   V17_2.5 Kiem tra sau (ke ca ten menu khong bi loi font do NLS_LANG).
--
-- QUY UOC (kiem tra tu dong boi V17PortalScriptTest):
--   * WHENEVER SQLERROR EXIT ... ROLLBACK truoc lenh dau tien; ket thuc bang COMMIT + EXIT.
--   * Chay lai nhieu lan an toan: MERGE ON (MENU_CODE) / (MENU_ID, FUNCTION_CODE) / (ROLE_ID, MENU_ID);
--     ID lay tu SEQ_SYS_MENUS / SEQ_SYS_FUNCTIONS / SEQ_SYS_ROLE_MENU_PERM - KHONG dung ID co dinh.
--   * Chi DML, khong DDL.
--   * File UTF-8 co dau tieng Viet: chay voi NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--     (PowerShell: $env:NLS_LANG = 'AMERICAN_AMERICA.AL32UTF8'). Sai NLS_LANG -> V17_2.5 bao loi va ROLLBACK.
--
-- THU TU CHAY: sau V17_1. Chay truoc hay sau deploy backend deu duoc (backend cu khong dung menu nay;
-- truoc khi co V17_2 chi ROLE_ADMIN goi duoc /api/v1/student-accounts/**).
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V17_2__student_account_menu.sql
--
-- ROLLBACK (xoa mem menu, giong MenuAdminServiceImpl.delete):
--   DELETE FROM SYS_ROLE_MENU_PERMISSIONS
--    WHERE MENU_ID IN (SELECT ID FROM SYS_MENUS WHERE MENU_CODE = 'MENU_STUDENT_ACCOUNT');
--   UPDATE SYS_FUNCTIONS SET IS_DELETED = 1, UPDATED_AT = SYSTIMESTAMP
--    WHERE MENU_ID IN (SELECT ID FROM SYS_MENUS WHERE MENU_CODE = 'MENU_STUDENT_ACCOUNT');
--   UPDATE SYS_MENUS SET IS_DELETED = 1, IS_HIDDEN = 1, UPDATED_AT = SYSTIMESTAMP, UPDATED_BY = 'V17_2_ROLLBACK'
--    WHERE MENU_CODE = 'MENU_STUDENT_ACCOUNT';
--   COMMIT;
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V17_2.1 Kiem tra truoc ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM SYS_MENUS WHERE MENU_CODE = 'DIR_SYSTEM' AND IS_DELETED = 0;
    IF V_COUNT <> 1 THEN
        RAISE_APPLICATION_ERROR(-20001, 'V17_2: khong tim thay thu muc DIR_SYSTEM (chay V12 truoc)');
    END IF;
    SELECT COUNT(*) INTO V_COUNT FROM SYS_ROLES WHERE ROLE_CODE = 'ROLE_ADMIN' AND IS_DELETED = 0;
    IF V_COUNT <> 1 THEN
        RAISE_APPLICATION_ERROR(-20002, 'V17_2: khong tim thay vai tro ROLE_ADMIN');
    END IF;
    DBMS_OUTPUT.PUT_LINE('  DIR_SYSTEM, ROLE_ADMIN                  OK');
END;
/

PROMPT ============ V17_2.2 Menu MENU_STUDENT_ACCOUNT ============

MERGE INTO SYS_MENUS t
USING (
    SELECT x.MENU_CODE, x.MENU_NAME, x.PATH, x.ICON, x.SORT_ORDER, p.ID PARENT_ID
      FROM (
            SELECT 'MENU_STUDENT_ACCOUNT' MENU_CODE, 'Tài khoản học sinh' MENU_NAME,
                   '/system/student-accounts' PATH, 'user-graduate' ICON, 5 SORT_ORDER FROM DUAL
           ) x
      JOIN SYS_MENUS p ON p.MENU_CODE = 'DIR_SYSTEM' AND p.IS_DELETED = 0
) s ON (t.MENU_CODE = s.MENU_CODE)
WHEN MATCHED THEN UPDATE SET t.PARENT_ID = s.PARENT_ID, t.MENU_NAME = s.MENU_NAME, t.PATH = s.PATH,
                             t.ICON = s.ICON, t.SORT_ORDER = s.SORT_ORDER, t.MENU_TYPE = 'MENU',
                             t.IS_HIDDEN = 0, t.IS_DELETED = 0, t.STATUS = 'ACTIVE',
                             t.UPDATED_AT = SYSTIMESTAMP, t.UPDATED_BY = 'V17_2_MIGRATION'
WHEN NOT MATCHED THEN INSERT (ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, PATH, ICON, SORT_ORDER,
                              IS_HIDDEN, STATUS, IS_DELETED, CREATED_BY)
                      VALUES (SEQ_SYS_MENUS.NEXTVAL, s.PARENT_ID, s.MENU_CODE, s.MENU_NAME, 'MENU', s.PATH, s.ICON,
                              s.SORT_ORDER, 0, 'ACTIVE', 0, 'V17_2_MIGRATION');

PROMPT ============ V17_2.3 Chuc nang ============

MERGE INTO SYS_FUNCTIONS t
USING (
    SELECT m.ID AS MENU_ID, x.FUNCTION_CODE, x.FUNCTION_NAME
      FROM SYS_MENUS m
      JOIN (
            SELECT 'VIEW' FUNCTION_CODE, 'Xem danh sách' FUNCTION_NAME FROM DUAL UNION ALL
            SELECT 'CREATE', 'Tạo tài khoản' FROM DUAL UNION ALL
            SELECT 'RESET_PASSWORD', 'Đặt lại mật khẩu' FROM DUAL UNION ALL
            SELECT 'LOCK', 'Khóa / mở khóa' FROM DUAL
           ) x ON 1 = 1
     WHERE m.MENU_CODE = 'MENU_STUDENT_ACCOUNT'
       AND m.IS_DELETED = 0
) s ON (t.MENU_ID = s.MENU_ID AND t.FUNCTION_CODE = s.FUNCTION_CODE)
WHEN MATCHED THEN UPDATE SET t.FUNCTION_NAME = s.FUNCTION_NAME, t.IS_DELETED = 0
WHEN NOT MATCHED THEN INSERT (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
                      VALUES (SEQ_SYS_FUNCTIONS.NEXTVAL, s.MENU_ID, s.FUNCTION_CODE, s.FUNCTION_NAME, 0);

PROMPT ============ V17_2.4 Phan quyen ROLE_ADMIN ============

MERGE INTO SYS_ROLE_MENU_PERMISSIONS t
USING (
    SELECT r.ID AS ROLE_ID,
           f.MENU_ID,
           LISTAGG(f.FUNCTION_CODE, ',') WITHIN GROUP (ORDER BY f.ID) AS ALLOWED_FUNCTIONS
      FROM SYS_FUNCTIONS f
      JOIN SYS_MENUS m ON m.ID = f.MENU_ID AND m.IS_DELETED = 0 AND m.MENU_CODE = 'MENU_STUDENT_ACCOUNT'
      CROSS JOIN SYS_ROLES r
     WHERE r.ROLE_CODE = 'ROLE_ADMIN'
       AND f.IS_DELETED = 0
     GROUP BY r.ID, f.MENU_ID
) s ON (t.ROLE_ID = s.ROLE_ID AND t.MENU_ID = s.MENU_ID)
WHEN MATCHED THEN UPDATE SET t.ALLOWED_FUNCTIONS = s.ALLOWED_FUNCTIONS, t.UPDATED_AT = SYSTIMESTAMP,
                             t.UPDATED_BY = 'V17_2_MIGRATION'
WHEN NOT MATCHED THEN INSERT (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
                      VALUES (SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, s.ROLE_ID, s.MENU_ID, s.ALLOWED_FUNCTIONS, 'V17_2_MIGRATION');

PROMPT ============ V17_2.5 Kiem tra sau ============

DECLARE
    V_NAME  SYS_MENUS.MENU_NAME%TYPE;
    V_FUNCS NUMBER;
    V_PERMS NUMBER;
BEGIN
    SELECT MENU_NAME INTO V_NAME
      FROM SYS_MENUS
     WHERE MENU_CODE = 'MENU_STUDENT_ACCOUNT' AND IS_DELETED = 0;
    -- Ten menu doi chieu voi ban UNISTR (khong phu thuoc ma hoa file / NLS_LANG).
    IF V_NAME IS NULL OR V_NAME <> UNISTR('T\00E0i kho\1EA3n h\1ECDc sinh') THEN
        RAISE_APPLICATION_ERROR(-20003,
            'V17_2: ten menu sai ma hoa - chay lai voi NLS_LANG=AMERICAN_AMERICA.AL32UTF8');
    END IF;

    SELECT COUNT(*) INTO V_FUNCS
      FROM SYS_FUNCTIONS f
      JOIN SYS_MENUS m ON m.ID = f.MENU_ID AND m.MENU_CODE = 'MENU_STUDENT_ACCOUNT'
     WHERE f.IS_DELETED = 0
       AND f.FUNCTION_CODE IN ('VIEW', 'CREATE', 'RESET_PASSWORD', 'LOCK');
    IF V_FUNCS <> 4 THEN
        RAISE_APPLICATION_ERROR(-20004, 'V17_2: MENU_STUDENT_ACCOUNT co ' || V_FUNCS || '/4 chuc nang');
    END IF;

    SELECT COUNT(*) INTO V_PERMS
      FROM SYS_ROLE_MENU_PERMISSIONS p
      JOIN SYS_MENUS m ON m.ID = p.MENU_ID AND m.MENU_CODE = 'MENU_STUDENT_ACCOUNT'
      JOIN SYS_ROLES r ON r.ID = p.ROLE_ID AND r.ROLE_CODE = 'ROLE_ADMIN'
     WHERE INSTR(',' || p.ALLOWED_FUNCTIONS || ',', ',VIEW,') > 0
       AND INSTR(',' || p.ALLOWED_FUNCTIONS || ',', ',CREATE,') > 0
       AND INSTR(',' || p.ALLOWED_FUNCTIONS || ',', ',RESET_PASSWORD,') > 0
       AND INSTR(',' || p.ALLOWED_FUNCTIONS || ',', ',LOCK,') > 0;
    IF V_PERMS <> 1 THEN
        RAISE_APPLICATION_ERROR(-20005, 'V17_2: ROLE_ADMIN chua du quyen MENU_STUDENT_ACCOUNT');
    END IF;
    DBMS_OUTPUT.PUT_LINE('  MENU_STUDENT_ACCOUNT                    OK');
END;
/

COMMIT;

PROMPT ============ V17_2 DONE ============
EXIT
