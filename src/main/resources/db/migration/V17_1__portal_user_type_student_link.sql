-- =============================================================================
-- EDUCATION - MIGRATION V17_1: CONG HOC SINH - LOAI TAI KHOAN + LIEN KET TAI KHOAN / HOC SINH
--
-- Giai doan 0, Stream A (feat/portal-p0-core). CHI Stream A sua file nay.
-- (V17_2 = menu "Tai khoan hoc sinh" - Stream A; V17_3 = SYS_AUDIT_LOGS - Stream B.)
--
-- Script nay lam:
--   V17_1.1 SYS_USERS.USER_TYPE VARCHAR2(20) DEFAULT 'STAFF' NOT NULL + CK_USERS_USER_TYPE
--           (STAFF / STUDENT / PARENT). Dong hien co nhan 'STAFF' (Oracle dien DEFAULT khi ADD NOT NULL).
--   V17_1.2 Sequence SEQ_EDU_USER_STUDENT_LINKS (ID bang lien ket) va SEQ_STUDENT_USERNAME
--           (so thu tu ten dang nhap hoc sinh hs00001, hs00002... - NOCACHE de so lien tuc).
--   V17_1.3 Bang EDU_USER_STUDENT_LINKS: ID, USER_ID (FK SYS_USERS), STUDENT_ID (FK EDU_STUDENTS),
--           RELATION (SELF / PARENT), IS_PRIMARY, STATUS (ACTIVE / INACTIVE), IS_DELETED, cot audit;
--           UQ_USL_USER_STUDENT (USER_ID, STUDENT_ID).
--   V17_1.4 Index: UX_USL_SELF_ACTIVE - unique function-based index, moi hoc sinh TOI DA MOT lien ket SELF
--           dang hoat dong (RELATION = 'SELF' AND STATUS = 'ACTIVE' AND IS_DELETED = 0);
--           IX_USL_STUDENT (STUDENT_ID).
--   V17_1.5 Vai tro ROLE_STUDENT ("Hoc sinh", ACTIVE) - khong cap quyen menu nao.
--   V17_1.6 Kiem tra sau.
--
-- QUY UOC (kiem tra tu dong boi V17PortalScriptTest):
--   * WHENEVER SQLERROR EXIT ... ROLLBACK truoc lenh dau tien; ket thuc bang COMMIT + EXIT.
--   * Chay lai nhieu lan an toan: DDL kiem tra USER_TAB_COLUMNS / USER_CONSTRAINTS / USER_TABLES /
--     USER_INDEXES / USER_SEQUENCES truoc khi tao; seed dung MERGE ON (ROLE_CODE), ID = SEQ_SYS_ROLES.NEXTVAL.
--   * File thuan ASCII (chu co dau viet bang UNISTR) - khong phu thuoc NLS_LANG cua SQL*Plus.
--
-- THU TU CHAY: V17_1 -> V17_2 (V17_3 cua Stream B doc lap). V17_1 PHAI chay TRUOC khi deploy backend
-- feat/portal-p0-core (entity UserEntity map cot USER_TYPE; thieu cot thi dang nhap loi). Backend cu chay
-- binh thuong sau V17_1 (bo qua cot moi, dong moi nhan DEFAULT 'STAFF').
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V17_1__portal_user_type_student_link.sql
--
-- ROLLBACK (go bo hoan toan; MAT lien ket tai khoan hoc sinh. Chi lam SAU khi da quay lai backend cu):
--   -- 1) Vo hieu hoa tai khoan hoc sinh / phu huynh TRUOC: backend cu khong biet USER_TYPE nen se coi
--   --    cac tai khoan nay la nhan vien.
--   UPDATE SYS_USERS SET STATUS = 'LOCKED', IS_DELETED = 1, TOKEN_VERSION = TOKEN_VERSION + 1,
--          UPDATED_AT = SYSTIMESTAMP, UPDATED_BY = 'V17_1_ROLLBACK'
--    WHERE USER_TYPE <> 'STAFF';
--   DELETE FROM SYS_REFRESH_TOKENS WHERE USER_ID IN (SELECT ID FROM SYS_USERS WHERE USER_TYPE <> 'STAFF');
--   DELETE FROM SYS_USER_ROLES WHERE ROLE_ID IN (SELECT ID FROM SYS_ROLES WHERE ROLE_CODE = 'ROLE_STUDENT');
--   DELETE FROM SYS_ROLE_MENU_PERMISSIONS WHERE ROLE_ID IN (SELECT ID FROM SYS_ROLES WHERE ROLE_CODE = 'ROLE_STUDENT');
--   DELETE FROM SYS_ROLES WHERE ROLE_CODE = 'ROLE_STUDENT';
--   COMMIT;
--   -- 2) DDL (tu commit):
--   DROP TABLE EDU_USER_STUDENT_LINKS CASCADE CONSTRAINTS PURGE;
--   DROP SEQUENCE SEQ_EDU_USER_STUDENT_LINKS;
--   DROP SEQUENCE SEQ_STUDENT_USERNAME;
--   ALTER TABLE SYS_USERS DROP CONSTRAINT CK_USERS_USER_TYPE;
--   ALTER TABLE SYS_USERS DROP COLUMN USER_TYPE;
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V17_1.1 SYS_USERS.USER_TYPE ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO V_COUNT
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'SYS_USERS'
       AND COLUMN_NAME = 'USER_TYPE';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE q'[ALTER TABLE SYS_USERS ADD USER_TYPE VARCHAR2(20) DEFAULT 'STAFF' NOT NULL]';
        DBMS_OUTPUT.PUT_LINE('  SYS_USERS.USER_TYPE                     added');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  SYS_USERS.USER_TYPE                     already exists');
    END IF;

    SELECT COUNT(*)
      INTO V_COUNT
      FROM USER_CONSTRAINTS
     WHERE TABLE_NAME = 'SYS_USERS'
       AND CONSTRAINT_NAME = 'CK_USERS_USER_TYPE';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE q'[ALTER TABLE SYS_USERS ADD CONSTRAINT CK_USERS_USER_TYPE
                                 CHECK (USER_TYPE IN ('STAFF', 'STUDENT', 'PARENT'))]';
        DBMS_OUTPUT.PUT_LINE('  CK_USERS_USER_TYPE                      added');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  CK_USERS_USER_TYPE                      already exists');
    END IF;
END;
/

COMMENT ON COLUMN SYS_USERS.USER_TYPE IS 'Loai tai khoan: STAFF (nhan vien, mac dinh) / STUDENT (hoc sinh - chi /api/v1/portal/**) / PARENT (phu huynh)';

PROMPT ============ V17_1.2 Sequence ============

DECLARE
    PROCEDURE ENSURE_SEQUENCE(P_NAME VARCHAR2, P_DDL VARCHAR2) IS
        V_COUNT NUMBER;
    BEGIN
        SELECT COUNT(*) INTO V_COUNT FROM USER_SEQUENCES WHERE SEQUENCE_NAME = P_NAME;
        IF V_COUNT = 0 THEN
            EXECUTE IMMEDIATE P_DDL;
            DBMS_OUTPUT.PUT_LINE('  ' || RPAD(P_NAME, 40) || 'created');
        ELSE
            DBMS_OUTPUT.PUT_LINE('  ' || RPAD(P_NAME, 40) || 'already exists');
        END IF;
    END;
BEGIN
    ENSURE_SEQUENCE('SEQ_EDU_USER_STUDENT_LINKS',
        'CREATE SEQUENCE SEQ_EDU_USER_STUDENT_LINKS START WITH 1 INCREMENT BY 1 NOCYCLE');
    ENSURE_SEQUENCE('SEQ_STUDENT_USERNAME',
        'CREATE SEQUENCE SEQ_STUDENT_USERNAME START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE');
END;
/

PROMPT ============ V17_1.3 Bang EDU_USER_STUDENT_LINKS ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME = 'EDU_USER_STUDENT_LINKS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE EDU_USER_STUDENT_LINKS (
                ID          NUMBER                              NOT NULL,
                USER_ID     NUMBER                              NOT NULL,
                STUDENT_ID  NUMBER                              NOT NULL,
                RELATION    VARCHAR2(20)                        NOT NULL,
                IS_PRIMARY  NUMBER(1)    DEFAULT 0              NOT NULL,
                STATUS      VARCHAR2(20) DEFAULT 'ACTIVE'       NOT NULL,
                IS_DELETED  NUMBER(1)    DEFAULT 0              NOT NULL,
                CREATED_AT  TIMESTAMP    DEFAULT SYSTIMESTAMP   NOT NULL,
                UPDATED_AT  TIMESTAMP,
                CREATED_BY  VARCHAR2(50),
                UPDATED_BY  VARCHAR2(50),
                CONSTRAINT PK_EDU_USER_STUDENT_LINKS PRIMARY KEY (ID),
                CONSTRAINT FK_USL_USER    FOREIGN KEY (USER_ID)    REFERENCES SYS_USERS (ID),
                CONSTRAINT FK_USL_STUDENT FOREIGN KEY (STUDENT_ID) REFERENCES EDU_STUDENTS (ID),
                CONSTRAINT UQ_USL_USER_STUDENT UNIQUE (USER_ID, STUDENT_ID),
                CONSTRAINT CK_USL_RELATION CHECK (RELATION IN ('SELF', 'PARENT')),
                CONSTRAINT CK_USL_PRIMARY  CHECK (IS_PRIMARY IN (0, 1)),
                CONSTRAINT CK_USL_STATUS   CHECK (STATUS IN ('ACTIVE', 'INACTIVE')),
                CONSTRAINT CK_USL_DELETED  CHECK (IS_DELETED IN (0, 1))
            )]';
        DBMS_OUTPUT.PUT_LINE('  EDU_USER_STUDENT_LINKS                  created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  EDU_USER_STUDENT_LINKS                  already exists');
    END IF;
END;
/

COMMENT ON TABLE EDU_USER_STUDENT_LINKS IS 'Lien ket tai khoan SYS_USERS - hoc sinh EDU_STUDENTS (SELF = tai khoan cua chinh hoc sinh, PARENT = phu huynh). Toi da 1 SELF dang hoat dong / hoc sinh (UX_USL_SELF_ACTIVE)';
COMMENT ON COLUMN EDU_USER_STUDENT_LINKS.RELATION IS 'SELF / PARENT';
COMMENT ON COLUMN EDU_USER_STUDENT_LINKS.IS_PRIMARY IS '1 = lien ket chinh (hoc sinh mac dinh cua phu huynh nhieu con)';
COMMENT ON COLUMN EDU_USER_STUDENT_LINKS.STATUS IS 'ACTIVE / INACTIVE';

PROMPT ============ V17_1.4 Index ============

DECLARE
    PROCEDURE ENSURE_INDEX(P_NAME VARCHAR2, P_DDL VARCHAR2) IS
        V_COUNT NUMBER;
    BEGIN
        SELECT COUNT(*) INTO V_COUNT FROM USER_INDEXES WHERE INDEX_NAME = P_NAME;
        IF V_COUNT = 0 THEN
            EXECUTE IMMEDIATE P_DDL;
            DBMS_OUTPUT.PUT_LINE('  ' || RPAD(P_NAME, 40) || 'created');
        ELSE
            DBMS_OUTPUT.PUT_LINE('  ' || RPAD(P_NAME, 40) || 'already exists');
        END IF;
    END;
BEGIN
    -- Moi hoc sinh toi da MOT tai khoan SELF dang hoat dong (dong khac cho NULL - khong vao index).
    ENSURE_INDEX('UX_USL_SELF_ACTIVE',
        q'[CREATE UNIQUE INDEX UX_USL_SELF_ACTIVE ON EDU_USER_STUDENT_LINKS (
               CASE WHEN RELATION = 'SELF' AND STATUS = 'ACTIVE' AND IS_DELETED = 0 THEN STUDENT_ID END)]');
    ENSURE_INDEX('IX_USL_STUDENT',
        'CREATE INDEX IX_USL_STUDENT ON EDU_USER_STUDENT_LINKS (STUDENT_ID)');
END;
/

PROMPT ============ V17_1.5 Vai tro ROLE_STUDENT ============

MERGE INTO SYS_ROLES t
USING (SELECT 'ROLE_STUDENT' ROLE_CODE FROM DUAL) s
   ON (t.ROLE_CODE = s.ROLE_CODE)
WHEN NOT MATCHED THEN INSERT (ID, ROLE_CODE, ROLE_NAME, DESCRIPTION, STATUS, IS_DELETED, CREATED_BY)
                      VALUES (SEQ_SYS_ROLES.NEXTVAL, s.ROLE_CODE, UNISTR('H\1ECDc sinh'),
                              UNISTR('T\00E0i kho\1EA3n h\1ECDc sinh (c\1ED5ng /portal) - kh\00F4ng c\00F3 quy\1EC1n menu qu\1EA3n tr\1ECB'),
                              'ACTIVE', 0, 'V17_1_MIGRATION');

PROMPT ============ V17_1.6 Kiem tra sau ============

DECLARE
    V_MISSING VARCHAR2(4000);
    V_COUNT   NUMBER;
BEGIN
    FOR r IN (SELECT n.OBJECT_NAME, n.OBJECT_TYPE, NVL(o.STATUS, 'MISSING') STATUS
                FROM (SELECT 'EDU_USER_STUDENT_LINKS' OBJECT_NAME, 'TABLE' OBJECT_TYPE FROM DUAL UNION ALL
                      SELECT 'SEQ_EDU_USER_STUDENT_LINKS', 'SEQUENCE' FROM DUAL UNION ALL
                      SELECT 'SEQ_STUDENT_USERNAME', 'SEQUENCE' FROM DUAL UNION ALL
                      SELECT 'UX_USL_SELF_ACTIVE', 'INDEX' FROM DUAL UNION ALL
                      SELECT 'IX_USL_STUDENT', 'INDEX' FROM DUAL) n
                LEFT JOIN USER_OBJECTS o ON o.OBJECT_NAME = n.OBJECT_NAME AND o.OBJECT_TYPE = n.OBJECT_TYPE) LOOP
        DBMS_OUTPUT.PUT_LINE('  ' || RPAD(r.OBJECT_NAME, 40) || r.STATUS);
        IF r.STATUS <> 'VALID' THEN
            V_MISSING := V_MISSING || ' ' || r.OBJECT_NAME;
        END IF;
    END LOOP;

    SELECT COUNT(*) INTO V_COUNT
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'SYS_USERS' AND COLUMN_NAME = 'USER_TYPE' AND NULLABLE = 'N';
    IF V_COUNT = 0 THEN
        V_MISSING := V_MISSING || ' SYS_USERS.USER_TYPE';
    END IF;

    SELECT COUNT(*) INTO V_COUNT
      FROM USER_CONSTRAINTS
     WHERE TABLE_NAME = 'SYS_USERS' AND CONSTRAINT_NAME = 'CK_USERS_USER_TYPE' AND STATUS = 'ENABLED';
    IF V_COUNT = 0 THEN
        V_MISSING := V_MISSING || ' CK_USERS_USER_TYPE';
    END IF;

    SELECT COUNT(*) INTO V_COUNT
      FROM SYS_ROLES
     WHERE ROLE_CODE = 'ROLE_STUDENT' AND IS_DELETED = 0 AND STATUS = 'ACTIVE';
    IF V_COUNT = 0 THEN
        V_MISSING := V_MISSING || ' ROLE_STUDENT';
    END IF;

    IF V_MISSING IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20001, 'V17_1: thieu / khong hop le:' || V_MISSING);
    END IF;
    DBMS_OUTPUT.PUT_LINE('  V17_1                                   OK');
END;
/

COMMIT;

PROMPT ============ V17_1 DONE ============
EXIT
