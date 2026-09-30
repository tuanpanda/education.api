-- =============================================================================
-- EDUCATION - MIGRATION V12: QUAN TRI HE THONG & BAO MAT (JWT + BCrypt)
--
--   * SYS_USERS: MUST_CHANGE_PASSWORD, TOKEN_VERSION (thu hoi JWT), LAST_LOGIN_AT, PASSWORD_CHANGED_AT.
--   * Mat khau chuyen sang BCrypt:
--       - admin / Admin@123            (bat buoc doi mat khau o lan dang nhap dau)
--       - tai khoan demo con mat khau SHA-256 'Education@123' -> BCrypt 'Education@123' (bat buoc doi)
--     Chi ghi de khi PASSWORD_HASH chua phai BCrypt ('$2...') hoac con la hash "mau" cua schema_init.sql cu
--     ('$2a$10$7EqJ...Kq2G' - dung dinh dang BCrypt nhung KHONG khop mat khau nao), chay lai nhieu lan an toan.
--   * Menu 'Quan tri he thong': Nguoi dung (/admin/users), Vai tro & phan quyen (/admin/roles), Menu (/admin/menus).
--   * ROLE_ADMIN: toan quyen tren moi menu / chuc nang.
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @V12__system_admin_security.sql
-- =============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED
-- Dung ngay o loi dau tien (khong chay tiep cac buoc sau tren du lieu dang do), rollback DML chua commit.
-- Luu y: DDL (ALTER TABLE ben duoi) tu commit, khong rollback duoc; cac khoi DDL da idempotent nen chay lai an toan.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
WHENEVER OSERROR EXIT FAILURE ROLLBACK

PROMPT ============ V12.1 Cot bao mat tren SYS_USERS ============

DECLARE
    PROCEDURE ADD_COLUMN(p_column IN VARCHAR2, p_ddl IN VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE SYS_USERS ADD (' || p_ddl || ')';
        DBMS_OUTPUT.PUT_LINE('SYS_USERS.' || p_column || ': added');
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE = -1430 THEN
                DBMS_OUTPUT.PUT_LINE('SYS_USERS.' || p_column || ': already exists');
            ELSE
                RAISE;
            END IF;
    END;
BEGIN
    ADD_COLUMN('MUST_CHANGE_PASSWORD', 'MUST_CHANGE_PASSWORD NUMBER(1) DEFAULT 0 NOT NULL');
    ADD_COLUMN('TOKEN_VERSION',        'TOKEN_VERSION NUMBER(10) DEFAULT 0 NOT NULL');
    ADD_COLUMN('LAST_LOGIN_AT',        'LAST_LOGIN_AT TIMESTAMP(6)');
    ADD_COLUMN('PASSWORD_CHANGED_AT',  'PASSWORD_CHANGED_AT TIMESTAMP(6)');
END;
/

DECLARE
    PROCEDURE ADD_CHECK(p_name IN VARCHAR2, p_condition IN VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE SYS_USERS ADD CONSTRAINT ' || p_name
                          || ' CHECK (' || p_condition || ') ENABLE NOVALIDATE';
        DBMS_OUTPUT.PUT_LINE(p_name || ': created');
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE IN (-2264, -2275) THEN
                DBMS_OUTPUT.PUT_LINE(p_name || ': already exists');
            ELSE
                RAISE;
            END IF;
    END;
BEGIN
    ADD_CHECK('CK_USERS_STATUS', q'[STATUS IN ('ACTIVE', 'INACTIVE', 'LOCKED')]');
    ADD_CHECK('CK_USERS_MUST_CHANGE', 'MUST_CHANGE_PASSWORD IN (0, 1)');
END;
/

COMMENT ON COLUMN SYS_USERS.MUST_CHANGE_PASSWORD IS '1 = bat buoc doi mat khau truoc khi dung he thong';
COMMENT ON COLUMN SYS_USERS.TOKEN_VERSION IS 'Tang khi dang xuat / doi mat khau / khoa -> vo hieu hoa moi JWT da cap';
COMMENT ON COLUMN SYS_USERS.STATUS IS 'ACTIVE | INACTIVE | LOCKED';

PROMPT ============ V12.2 Vai tro ROLE_ADMIN + tai khoan admin ============

MERGE INTO SYS_ROLES t
USING (SELECT 'ROLE_ADMIN' ROLE_CODE, 'Quản trị viên hệ thống' ROLE_NAME FROM DUAL) s
   ON (t.ROLE_CODE = s.ROLE_CODE)
WHEN MATCHED THEN UPDATE SET t.STATUS = 'ACTIVE', t.IS_DELETED = 0
WHEN NOT MATCHED THEN INSERT (ID, ROLE_CODE, ROLE_NAME, DESCRIPTION, STATUS, IS_DELETED, CREATED_BY)
                      VALUES (SEQ_SYS_ROLES.NEXTVAL, s.ROLE_CODE, s.ROLE_NAME,
                              'Toàn quyền trên mọi menu và chức năng', 'ACTIVE', 0, 'V12_MIGRATION');

-- BCrypt (cost 10) cua 'Admin@123'.
MERGE INTO SYS_USERS t
USING (SELECT 'admin' USERNAME FROM DUAL) s
   ON (t.USERNAME = s.USERNAME)
WHEN MATCHED THEN UPDATE SET
        t.PASSWORD_HASH = '$2a$10$aHlyZN6UgaGm.aPpX.jBjuKV3p0hBxTtfrUifsl0gyNMnzzT81OCy',
        t.MUST_CHANGE_PASSWORD = 1,
        t.TOKEN_VERSION = t.TOKEN_VERSION + 1,
        t.STATUS = 'ACTIVE',
        t.IS_DELETED = 0,
        t.UPDATED_AT = SYSTIMESTAMP,
        t.UPDATED_BY = 'V12_MIGRATION'
     WHERE t.PASSWORD_HASH NOT LIKE '$2%'
        OR t.PASSWORD_HASH = '$2a$10$7EqJtq98hPqEX7fNZaFWoO96u8xLw9Jm9j.qR8xT6rW1dG7z5Kq2G'
WHEN NOT MATCHED THEN INSERT (ID, USERNAME, PASSWORD_HASH, FULL_NAME, EMAIL, STATUS, IS_DELETED,
                              MUST_CHANGE_PASSWORD, TOKEN_VERSION, CREATED_BY)
                      VALUES (SEQ_SYS_USERS.NEXTVAL, s.USERNAME,
                              '$2a$10$aHlyZN6UgaGm.aPpX.jBjuKV3p0hBxTtfrUifsl0gyNMnzzT81OCy',
                              'Quản trị hệ thống', NULL, 'ACTIVE', 0, 1, 0, 'V12_MIGRATION');

MERGE INTO SYS_USER_ROLES t
USING (
    SELECT u.ID USER_ID, r.ID ROLE_ID
      FROM SYS_USERS u
      JOIN SYS_ROLES r ON r.ROLE_CODE = 'ROLE_ADMIN'
     WHERE u.USERNAME = 'admin'
) s ON (t.USER_ID = s.USER_ID AND t.ROLE_ID = s.ROLE_ID)
WHEN NOT MATCHED THEN INSERT (USER_ID, ROLE_ID, ASSIGNED_BY) VALUES (s.USER_ID, s.ROLE_ID, 'V12_MIGRATION');

-- Tai khoan demo (V1) con mat khau SHA-256 cua 'Education@123' (hoac hash "mau" khong dung duoc cua
-- schema_init.sql cu) -> BCrypt 'Education@123', buoc doi.
UPDATE SYS_USERS
   SET PASSWORD_HASH = '$2a$10$NDLPXq0uJZykYoFJWdOZIuYlv64aHu.YXRqP9n8yWAd7./30VwigW',
       MUST_CHANGE_PASSWORD = 1,
       TOKEN_VERSION = TOKEN_VERSION + 1,
       UPDATED_AT = SYSTIMESTAMP,
       UPDATED_BY = 'V12_MIGRATION'
 WHERE USERNAME <> 'admin'
   AND (PASSWORD_HASH = LOWER(RAWTOHEX(STANDARD_HASH('Education@123', 'SHA256')))
        OR PASSWORD_HASH = '$2a$10$7EqJtq98hPqEX7fNZaFWoO96u8xLw9Jm9j.qR8xT6rW1dG7z5Kq2G');

PROMPT ============ V12.3 Menu Quan tri he thong ============

UPDATE SYS_MENUS
   SET MENU_NAME = 'Quản trị hệ thống',
       ICON = NVL(ICON, 'gear'),
       MENU_TYPE = 'DIR',
       STATUS = 'ACTIVE',
       IS_DELETED = 0,
       UPDATED_AT = SYSTIMESTAMP,
       UPDATED_BY = 'V12_MIGRATION'
 WHERE MENU_CODE = 'DIR_SYSTEM';

MERGE INTO SYS_MENUS t
USING (SELECT 'DIR_SYSTEM' MENU_CODE FROM DUAL) s
   ON (t.MENU_CODE = s.MENU_CODE)
WHEN NOT MATCHED THEN INSERT (ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, PATH, ICON, SORT_ORDER,
                              IS_HIDDEN, STATUS, IS_DELETED, CREATED_BY)
                      VALUES (SEQ_SYS_MENUS.NEXTVAL, NULL, s.MENU_CODE, 'Quản trị hệ thống', 'DIR', NULL, 'gear', 90,
                              0, 'ACTIVE', 0, 'V12_MIGRATION');

MERGE INTO SYS_MENUS t
USING (
    SELECT x.MENU_CODE, x.MENU_NAME, x.PATH, x.ICON, x.SORT_ORDER, p.ID PARENT_ID
      FROM (
            SELECT 'MENU_USER_LIST' MENU_CODE, 'Người dùng' MENU_NAME, '/admin/users' PATH,
                   'user-gear' ICON, 1 SORT_ORDER FROM DUAL UNION ALL
            SELECT 'MENU_ROLE_LIST', 'Vai trò & phân quyền', '/admin/roles', 'user-shield', 2 FROM DUAL UNION ALL
            SELECT 'MENU_MENU_CONFIG', 'Menu', '/admin/menus', 'bars', 3 FROM DUAL
           ) x
      JOIN SYS_MENUS p ON p.MENU_CODE = 'DIR_SYSTEM'
) s ON (t.MENU_CODE = s.MENU_CODE)
WHEN MATCHED THEN UPDATE SET t.PARENT_ID = s.PARENT_ID, t.MENU_NAME = s.MENU_NAME, t.PATH = s.PATH,
                             t.ICON = s.ICON, t.SORT_ORDER = s.SORT_ORDER, t.MENU_TYPE = 'MENU',
                             t.IS_HIDDEN = 0, t.IS_DELETED = 0, t.STATUS = 'ACTIVE',
                             t.UPDATED_AT = SYSTIMESTAMP, t.UPDATED_BY = 'V12_MIGRATION'
WHEN NOT MATCHED THEN INSERT (ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, PATH, ICON, SORT_ORDER,
                              IS_HIDDEN, STATUS, IS_DELETED, CREATED_BY)
                      VALUES (SEQ_SYS_MENUS.NEXTVAL, s.PARENT_ID, s.MENU_CODE, s.MENU_NAME, 'MENU', s.PATH, s.ICON,
                              s.SORT_ORDER, 0, 'ACTIVE', 0, 'V12_MIGRATION');

UPDATE SYS_MENUS
   SET SORT_ORDER = 4, UPDATED_AT = SYSTIMESTAMP, UPDATED_BY = 'V12_MIGRATION'
 WHERE MENU_CODE = 'MENU_BANK_ACCOUNT';

-- Man hinh "Phan quyen nguoi dung" cu (/system/permissions) da gop vao "Vai tro & phan quyen".
UPDATE SYS_MENUS
   SET IS_DELETED = 1, STATUS = 'INACTIVE', UPDATED_AT = SYSTIMESTAMP, UPDATED_BY = 'V12_MIGRATION'
 WHERE MENU_CODE = 'MENU_USER_PERM';

DELETE FROM SYS_ROLE_MENU_PERMISSIONS
 WHERE MENU_ID IN (SELECT ID FROM SYS_MENUS WHERE MENU_CODE = 'MENU_USER_PERM');

PROMPT ============ V12.4 Chuc nang cua menu quan tri ============

MERGE INTO SYS_FUNCTIONS t
USING (
    SELECT m.ID AS MENU_ID, x.FUNCTION_CODE, x.FUNCTION_NAME
      FROM SYS_MENUS m
      JOIN (
            SELECT 'VIEW' FUNCTION_CODE, 'Xem danh sách' FUNCTION_NAME FROM DUAL UNION ALL
            SELECT 'CREATE', 'Thêm mới' FROM DUAL UNION ALL
            SELECT 'UPDATE', 'Cập nhật' FROM DUAL UNION ALL
            SELECT 'DELETE', 'Xóa' FROM DUAL
           ) x ON 1 = 1
     WHERE m.MENU_CODE IN ('MENU_USER_LIST', 'MENU_ROLE_LIST', 'MENU_MENU_CONFIG')
       AND m.IS_DELETED = 0
    UNION ALL
    SELECT m.ID, 'VIEW', 'Xem danh sách'
      FROM SYS_MENUS m
     WHERE m.MENU_CODE = 'DIR_SYSTEM'
) s ON (t.MENU_ID = s.MENU_ID AND t.FUNCTION_CODE = s.FUNCTION_CODE)
WHEN MATCHED THEN UPDATE SET t.IS_DELETED = 0
WHEN NOT MATCHED THEN INSERT (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
                      VALUES (SEQ_SYS_FUNCTIONS.NEXTVAL, s.MENU_ID, s.FUNCTION_CODE, s.FUNCTION_NAME, 0);

PROMPT ============ V12.5 ROLE_ADMIN toan quyen ============

MERGE INTO SYS_ROLE_MENU_PERMISSIONS t
USING (
    SELECT r.ID AS ROLE_ID,
           f.MENU_ID,
           LISTAGG(f.FUNCTION_CODE, ',') WITHIN GROUP (ORDER BY f.ID) AS ALLOWED_FUNCTIONS
      FROM SYS_FUNCTIONS f
      JOIN SYS_MENUS m ON m.ID = f.MENU_ID AND m.IS_DELETED = 0
      CROSS JOIN SYS_ROLES r
     WHERE r.ROLE_CODE = 'ROLE_ADMIN'
       AND f.IS_DELETED = 0
     GROUP BY r.ID, f.MENU_ID
) s ON (t.ROLE_ID = s.ROLE_ID AND t.MENU_ID = s.MENU_ID)
WHEN MATCHED THEN UPDATE SET t.ALLOWED_FUNCTIONS = s.ALLOWED_FUNCTIONS, t.UPDATED_AT = SYSTIMESTAMP,
                             t.UPDATED_BY = 'V12_MIGRATION'
WHEN NOT MATCHED THEN INSERT (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
                      VALUES (SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, s.ROLE_ID, s.MENU_ID, s.ALLOWED_FUNCTIONS, 'V12_MIGRATION');

COMMIT;

PROMPT ============ V12 DONE ============
EXIT
