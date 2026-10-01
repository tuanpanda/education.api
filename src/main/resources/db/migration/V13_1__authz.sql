-- =============================================================================
-- EDUCATION - MIGRATION V13.1: PHAN QUYEN (authorization hardening)
--
--   * IX_USER_ROLES_ROLE tren SYS_USER_ROLES (ROLE_ID):
--       - UserRoleRepository.lockByRoleCode khoa (SELECT ... FOR UPDATE) moi dong ROLE_ADMIN truoc khi kiem tra
--         "quan tri vien cuoi cung" -> can index de khong quet/khoa ca bang.
--       - FK_USER_ROLES_ROLE chua co index o cot con (PK la USER_ID, ROLE_ID).
--   * Khong doi du lieu. Giang vien <-> lop dung san EDU_CLASSES.TEACHER_ID / EDU_CLASS_SESSIONS.TEACHER_ID
--     (FK toi SYS_USERS.ID) va index IX_SESSIONS_CLASS_DATE (V6), khong can them cot.
--   * Chay lai nhieu lan an toan (bo qua ORA-00955 / ORA-01408 khi index da ton tai).
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @V13_1__authz.sql
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V13.1 Index SYS_USER_ROLES (ROLE_ID) ============

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX IX_USER_ROLES_ROLE ON SYS_USER_ROLES (ROLE_ID, USER_ID)';
    DBMS_OUTPUT.PUT_LINE('IX_USER_ROLES_ROLE: OK');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE IN (-955, -1408) THEN
            DBMS_OUTPUT.PUT_LINE('IX_USER_ROLES_ROLE: already exists');
        ELSE
            RAISE;
        END IF;
END;
/

PROMPT ============ V13.1 DONE ============
EXIT
