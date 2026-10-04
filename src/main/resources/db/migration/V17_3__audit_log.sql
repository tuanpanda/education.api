-- =============================================================================
-- EDUCATION - MIGRATION V17_3: NHAT KY HE THONG (AUDIT LOG)
--
-- Giai doan 0 - stream B (feat/portal-p0-hardening). CHI stream B sua file nay.
-- Stream A (feat/portal-p0-core) so huu V17_1 / V17_2 (USER_TYPE, EDU_USER_STUDENT_LINKS, ROLE_STUDENT).
-- V17_3 KHONG phu thuoc V17_1 / V17_2: chay truoc hay sau deu duoc.
--
-- Pham vi:
--   V17_3.0 Kiem tra truoc: can menu cha DIR_SYSTEM (V12) va vai tro ROLE_ADMIN. Chua doi gi.
--   V17_3.1 Bang SYS_AUDIT_LOGS + SEQ_SYS_AUDIT_LOGS + index:
--             ID, EVENT_TIME, USER_ID (nullable), USERNAME, USER_TYPE (nullable, chuoi tu do - KHONG CHECK /
--             KHONG FK de khong phu thuoc V17_1), ACTION, RESOURCE_TYPE, RESOURCE_ID, IP, USER_AGENT, RESULT, DETAIL.
--             Bang chi ghi them (append-only): ung dung khong UPDATE / DELETE; khong co IS_DELETED / CREATED_BY.
--             USER_ID khong co FK toi SYS_USERS: nhat ky phai giu nguyen ke ca khi tai khoan bi xoa, va ghi duoc
--             lan dang nhap sai voi ten dang nhap khong ton tai (USER_ID = NULL).
--             KHONG BAO GIO ghi mat khau, token, cookie vao DETAIL (AuditServiceImpl che cac khoa nhay cam).
--   V17_3.2 Menu MENU_AUDIT_LOG (/system/audit-logs) duoi DIR_SYSTEM (SORT_ORDER 6, sau MENU_STUDENT_ACCOUNT = 5
--           cua V17_2), chuc nang VIEW, cap cho ROLE_ADMIN.
--   V17_3.3 Kiem tra sau (bang du 12 cot, menu dung ten tieng Viet, ROLE_ADMIN co VIEW).
--
-- QUY UOC (kiem tra tu dong boi V17_3AuditLogScriptTest):
--   * WHENEVER SQLERROR EXIT ... ROLLBACK truoc lenh dau tien; script ket thuc bang COMMIT + EXIT.
--   * Chay lai nhieu lan an toan (idempotent): DDL boc trong khoi bo qua ORA-00955 / -01408 / -02260 / -02261 /
--     -02264 / -02275; seed dung MERGE (menu ON MENU_CODE, ID = SEQ_SYS_MENUS.NEXTVAL - KHONG dung ID co dinh).
--   * Luu y: DDL tu COMMIT trong Oracle; ROLLBACK cua WHENEVER SQLERROR chi hoan tac phan seed menu.
--   * File UTF-8 (khong BOM) co dau tieng Viet o ten menu: chay voi NLS_LANG=AMERICAN_AMERICA.AL32UTF8.
--     Muc V17_3.3 so ten menu voi UNISTR(...) va DUNG SCRIPT (ROLLBACK seed) neu sai bang ma.
--
-- Chay (Windows, PowerShell):
--   $env:NLS_LANG = "AMERICAN_AMERICA.AL32UTF8"
--   F:\Database\bin\sqlplus.exe EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V17_3__audit_log.sql
--
-- Thu tu deploy: chay V17_3 TRUOC hoac SAU khi deploy backend deu duoc. Backend chua co bang thi AuditService
-- chi ghi WARN (khong lam hong request); man hinh Nhat ky tra loi den khi chay V17_3.
--
-- ROLLBACK (go hoan toan V17_3; MAT toan bo nhat ky da ghi - can nhac export truoc):
--   DELETE FROM SYS_ROLE_MENU_PERMISSIONS
--    WHERE MENU_ID IN (SELECT ID FROM SYS_MENUS WHERE MENU_CODE = 'MENU_AUDIT_LOG');
--   DELETE FROM SYS_FUNCTIONS
--    WHERE MENU_ID IN (SELECT ID FROM SYS_MENUS WHERE MENU_CODE = 'MENU_AUDIT_LOG');
--   DELETE FROM SYS_MENUS WHERE MENU_CODE = 'MENU_AUDIT_LOG';
--   COMMIT;
--   DROP TABLE SYS_AUDIT_LOGS PURGE;
--   DROP SEQUENCE SEQ_SYS_AUDIT_LOGS;
--   (Backend moi van chay duoc sau rollback: ghi nhat ky that bai chi log WARN.)
--
-- DON DEP DINH KY (tuy chon, chinh sach luu giu - vi du giu 12 thang):
--   DELETE FROM SYS_AUDIT_LOGS WHERE EVENT_TIME < ADD_MONTHS(SYSTIMESTAMP, -12);
--   COMMIT;
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V17_3.0 Kiem tra truoc ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM SYS_MENUS WHERE MENU_CODE = 'DIR_SYSTEM' AND IS_DELETED = 0;
    IF V_COUNT <> 1 THEN
        RAISE_APPLICATION_ERROR(-20001, 'V17_3: khong tim thay menu cha DIR_SYSTEM - chay V12 truoc');
    END IF;
    SELECT COUNT(*) INTO V_COUNT FROM SYS_ROLES WHERE ROLE_CODE = 'ROLE_ADMIN';
    IF V_COUNT <> 1 THEN
        RAISE_APPLICATION_ERROR(-20002, 'V17_3: khong tim thay vai tro ROLE_ADMIN');
    END IF;
    DBMS_OUTPUT.PUT_LINE('  DIR_SYSTEM, ROLE_ADMIN                  OK');
END;
/

PROMPT ============ V17_3.1 Bang SYS_AUDIT_LOGS ============

DECLARE
    PROCEDURE DDL(p_name IN VARCHAR2, p_sql IN VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE p_sql;
        DBMS_OUTPUT.PUT_LINE('  ' || p_name || ': created');
    EXCEPTION
        WHEN OTHERS THEN
            -- -955: ten da dung, -1408: cot da co index, -2260/-2261: khoa chinh / unique da co,
            -- -2264/-2275: rang buoc da ton tai
            IF SQLCODE IN (-955, -1408, -2260, -2261, -2264, -2275) THEN
                DBMS_OUTPUT.PUT_LINE('  ' || p_name || ': already exists');
            ELSE
                RAISE;
            END IF;
    END;
BEGIN
    DDL('SEQ_SYS_AUDIT_LOGS',
        'CREATE SEQUENCE SEQ_SYS_AUDIT_LOGS START WITH 1 INCREMENT BY 1 CACHE 20 NOCYCLE');
    DDL('SYS_AUDIT_LOGS', q'[CREATE TABLE SYS_AUDIT_LOGS (
        ID             NUMBER(19)      NOT NULL,
        EVENT_TIME     TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
        USER_ID        NUMBER(19),
        USERNAME       VARCHAR2(100),
        USER_TYPE      VARCHAR2(20),
        ACTION         VARCHAR2(50)    NOT NULL,
        RESOURCE_TYPE  VARCHAR2(50),
        RESOURCE_ID    VARCHAR2(100),
        IP             VARCHAR2(64),
        USER_AGENT     VARCHAR2(500),
        RESULT         VARCHAR2(20)    DEFAULT 'SUCCESS' NOT NULL,
        DETAIL         VARCHAR2(4000),
        CONSTRAINT PK_SYS_AUDIT_LOGS PRIMARY KEY (ID),
        CONSTRAINT CK_AUDIT_LOGS_RESULT CHECK (RESULT IN ('SUCCESS', 'FAILURE', 'DENIED'))
    )]');
    DDL('IDX_AUDIT_LOGS_TIME',
        'CREATE INDEX IDX_AUDIT_LOGS_TIME ON SYS_AUDIT_LOGS (EVENT_TIME)');
    DDL('IDX_AUDIT_LOGS_USER',
        'CREATE INDEX IDX_AUDIT_LOGS_USER ON SYS_AUDIT_LOGS (USER_ID, EVENT_TIME)');
    DDL('IDX_AUDIT_LOGS_ACTION',
        'CREATE INDEX IDX_AUDIT_LOGS_ACTION ON SYS_AUDIT_LOGS (ACTION, EVENT_TIME)');
    DDL('IDX_AUDIT_LOGS_RESOURCE',
        'CREATE INDEX IDX_AUDIT_LOGS_RESOURCE ON SYS_AUDIT_LOGS (RESOURCE_TYPE, RESOURCE_ID)');
END;
/

COMMENT ON TABLE SYS_AUDIT_LOGS IS 'Nhat ky he thong (append-only): dang nhap, mat khau, khoa tai khoan, phan quyen, thanh toan, khoan phi';
COMMENT ON COLUMN SYS_AUDIT_LOGS.EVENT_TIME IS 'Thoi diem su kien (gio Viet Nam, nhu cac cot TIMESTAMP khac)';
COMMENT ON COLUMN SYS_AUDIT_LOGS.USER_ID IS 'SYS_USERS.ID nguoi thao tac (NULL: chua dang nhap / ten dang nhap khong ton tai); khong FK';
COMMENT ON COLUMN SYS_AUDIT_LOGS.USERNAME IS 'Ten dang nhap nguoi thao tac (hoac ten da nhap khi dang nhap that bai)';
COMMENT ON COLUMN SYS_AUDIT_LOGS.USER_TYPE IS 'Loai tai khoan (STAFF/STUDENT/PARENT...) tai thoi diem ghi; chuoi tu do, co the NULL';
COMMENT ON COLUMN SYS_AUDIT_LOGS.ACTION IS 'Ma hanh dong, vi du LOGIN_SUCCESS, LOGIN_FAILED, PASSWORD_RESET, PAYMENT_VOIDED';
COMMENT ON COLUMN SYS_AUDIT_LOGS.RESOURCE_TYPE IS 'Loai doi tuong bi tac dong, vi du USER, ROLE, PAYMENT_TRANSACTION, TUITION_FEE';
COMMENT ON COLUMN SYS_AUDIT_LOGS.RESOURCE_ID IS 'Khoa cua doi tuong bi tac dong (chuoi)';
COMMENT ON COLUMN SYS_AUDIT_LOGS.IP IS 'IP client (sau reverse proxy: lay tu X-Forwarded-For do Tomcat xu ly)';
COMMENT ON COLUMN SYS_AUDIT_LOGS.RESULT IS 'SUCCESS / FAILURE / DENIED';
COMMENT ON COLUMN SYS_AUDIT_LOGS.DETAIL IS 'Chi tiet JSON rut gon (<= 4000 byte); KHONG chua mat khau / token / cookie';

PROMPT ============ V17_3.2 Menu Nhat ky he thong ============

MERGE INTO SYS_MENUS t
USING (
    SELECT x.MENU_CODE, x.MENU_NAME, x.PATH, x.ICON, x.SORT_ORDER, p.ID PARENT_ID
      FROM (
            SELECT 'MENU_AUDIT_LOG' MENU_CODE, 'Nhật ký hệ thống' MENU_NAME, '/system/audit-logs' PATH,
                   'shield' ICON, 6 SORT_ORDER FROM DUAL
           ) x
      JOIN SYS_MENUS p ON p.MENU_CODE = 'DIR_SYSTEM'
) s ON (t.MENU_CODE = s.MENU_CODE)
WHEN MATCHED THEN UPDATE SET t.PARENT_ID = s.PARENT_ID, t.MENU_NAME = s.MENU_NAME, t.PATH = s.PATH,
                             t.ICON = s.ICON, t.SORT_ORDER = s.SORT_ORDER, t.MENU_TYPE = 'MENU',
                             t.IS_HIDDEN = 0, t.IS_DELETED = 0, t.STATUS = 'ACTIVE',
                             t.UPDATED_AT = SYSTIMESTAMP, t.UPDATED_BY = 'V17_3_MIGRATION'
WHEN NOT MATCHED THEN INSERT (ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, PATH, ICON, SORT_ORDER,
                              IS_HIDDEN, STATUS, IS_DELETED, CREATED_BY)
                      VALUES (SEQ_SYS_MENUS.NEXTVAL, s.PARENT_ID, s.MENU_CODE, s.MENU_NAME, 'MENU', s.PATH, s.ICON,
                              s.SORT_ORDER, 0, 'ACTIVE', 0, 'V17_3_MIGRATION');

MERGE INTO SYS_FUNCTIONS t
USING (
    SELECT m.ID AS MENU_ID, 'VIEW' AS FUNCTION_CODE, 'Xem danh sách' AS FUNCTION_NAME
      FROM SYS_MENUS m
     WHERE m.MENU_CODE = 'MENU_AUDIT_LOG'
       AND m.IS_DELETED = 0
) s ON (t.MENU_ID = s.MENU_ID AND t.FUNCTION_CODE = s.FUNCTION_CODE)
WHEN MATCHED THEN UPDATE SET t.FUNCTION_NAME = s.FUNCTION_NAME, t.IS_DELETED = 0
WHEN NOT MATCHED THEN INSERT (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
                      VALUES (SEQ_SYS_FUNCTIONS.NEXTVAL, s.MENU_ID, s.FUNCTION_CODE, s.FUNCTION_NAME, 0);

-- ROLE_ADMIN: them VIEW neu chua co (khong ghi de quyen admin da chinh tay tren menu nay).
MERGE INTO SYS_ROLE_MENU_PERMISSIONS t
USING (
    SELECT r.ID AS ROLE_ID, m.ID AS MENU_ID, 'VIEW' AS FUNCTION_CODE
      FROM SYS_ROLES r
      JOIN SYS_MENUS m ON m.MENU_CODE = 'MENU_AUDIT_LOG' AND m.IS_DELETED = 0
     WHERE r.ROLE_CODE = 'ROLE_ADMIN'
) s ON (t.ROLE_ID = s.ROLE_ID AND t.MENU_ID = s.MENU_ID)
WHEN MATCHED THEN UPDATE SET t.ALLOWED_FUNCTIONS = CASE WHEN t.ALLOWED_FUNCTIONS IS NULL
                                                        THEN s.FUNCTION_CODE
                                                        ELSE t.ALLOWED_FUNCTIONS || ',' || s.FUNCTION_CODE END,
                             t.UPDATED_AT = SYSTIMESTAMP, t.UPDATED_BY = 'V17_3_MIGRATION'
                      WHERE INSTR(',' || REPLACE(t.ALLOWED_FUNCTIONS, ' ', '') || ',', ',' || s.FUNCTION_CODE || ',') = 0
WHEN NOT MATCHED THEN INSERT (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
                      VALUES (SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, s.ROLE_ID, s.MENU_ID, s.FUNCTION_CODE, 'V17_3_MIGRATION');

PROMPT ============ V17_3.3 Kiem tra sau ============

DECLARE
    V_COLUMNS NUMBER;
    V_NAME    SYS_MENUS.MENU_NAME%TYPE;
    V_GRANTED NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO V_COLUMNS
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'SYS_AUDIT_LOGS'
       AND COLUMN_NAME IN ('ID', 'EVENT_TIME', 'USER_ID', 'USERNAME', 'USER_TYPE', 'ACTION', 'RESOURCE_TYPE',
                           'RESOURCE_ID', 'IP', 'USER_AGENT', 'RESULT', 'DETAIL');
    IF V_COLUMNS <> 12 THEN
        RAISE_APPLICATION_ERROR(-20003, 'V17_3: SYS_AUDIT_LOGS chi co ' || V_COLUMNS || '/12 cot');
    END IF;

    SELECT MENU_NAME INTO V_NAME FROM SYS_MENUS WHERE MENU_CODE = 'MENU_AUDIT_LOG';
    -- 'Nhat ky he thong' co dau, viet bang UNISTR (khong phu thuoc bang ma cua file / NLS_LANG).
    IF V_NAME IS NULL OR V_NAME <> UNISTR('Nh\1EADt k\00FD h\1EC7 th\1ED1ng') THEN
        RAISE_APPLICATION_ERROR(-20004,
            'V17_3: ten menu MENU_AUDIT_LOG sai bang ma - dat NLS_LANG=AMERICAN_AMERICA.AL32UTF8 roi chay lai');
    END IF;

    SELECT COUNT(*)
      INTO V_GRANTED
      FROM SYS_ROLE_MENU_PERMISSIONS p
      JOIN SYS_ROLES r ON r.ID = p.ROLE_ID AND r.ROLE_CODE = 'ROLE_ADMIN'
      JOIN SYS_MENUS m ON m.ID = p.MENU_ID AND m.MENU_CODE = 'MENU_AUDIT_LOG'
     WHERE INSTR(',' || REPLACE(p.ALLOWED_FUNCTIONS, ' ', '') || ',', ',VIEW,') > 0;
    IF V_GRANTED <> 1 THEN
        RAISE_APPLICATION_ERROR(-20005, 'V17_3: ROLE_ADMIN chua co MENU_AUDIT_LOG:VIEW');
    END IF;
    DBMS_OUTPUT.PUT_LINE('  SYS_AUDIT_LOGS (12 cot), MENU_AUDIT_LOG, ROLE_ADMIN:VIEW   OK');
END;
/

COMMIT;

PROMPT ============ V17_3 DONE ============
EXIT
