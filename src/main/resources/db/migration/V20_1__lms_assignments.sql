-- =============================================================================
-- EDUCATION - MIGRATION V20_1: GIAI DOAN 2 - BAI TAP VE NHA (LMS_ASSIGNMENTS)
--
-- Giai doan 2 (feat/homework). Hop dong: docs/homework-api.md.
--
-- Script nay lam:
--   V20_1.1 Kiem tra truoc: EDU_CLASSES, EDU_CLASS_SESSIONS ton tai.
--   V20_1.2 Sequence SEQ_LMS_ASSIGNMENTS
--   V20_1.3 Bang LMS_ASSIGNMENTS: CLASS_ID (FK), SESSION_ID (FK EDU_CLASS_SESSIONS, nullable),
--           TITLE VARCHAR2(200 CHAR), DESCRIPTION CLOB (HTML sanitize o backend), SUBMISSION_TYPE TEXT/FILE/BOTH,
--           MAX_SCORE NUMBER(5,2) default 10, DUE_AT, ALLOW_LATE + LATE_UNTIL, MAX_FILES, ALLOW_RESUBMIT,
--           SYNC_TO_GRADEBOOK + GRADE_WEIGHT (V20_4), STATUS DRAFT/PUBLISHED/CLOSED, PUBLISHED_AT, CLOSED_AT,
--           VERSION (optimistic lock), audit, IS_DELETED.
--           Gio luu: TIMESTAMP khong mui gio = gio server Asia/Ho_Chi_Minh (giong EDU_ANNOUNCEMENTS).
--   V20_1.4 Index (CLASS_ID, STATUS, DUE_AT), (SESSION_ID)
--   V20_1.5 Kiem tra sau
--
-- QUY UOC:
--   * WHENEVER SQLERROR EXIT ... ROLLBACK; ket thuc bang COMMIT + EXIT. (DDL Oracle tu commit.)
--   * Chay lai an toan: kiem USER_TABLES / USER_SEQUENCES / USER_INDEXES truoc khi tao.
--   * File thuan ASCII.
--
-- THU TU CHAY: sau V19_4. V20_1 -> V20_2 -> V20_3 -> V20_4.
--
--   (Windows CMD/PowerShell; nhap mat khau khi sqlplus hoi - KHONG ghi mat khau vao file)
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe EDUCATION@//localhost:1521/ORCL @V20_1__lms_assignments.sql
--
-- ROLLBACK (MAT du lieu bai tap; chay rollback V20_4, V20_2 truoc):
--   DROP TABLE LMS_ASSIGNMENTS CASCADE CONSTRAINTS PURGE;
--   DROP SEQUENCE SEQ_LMS_ASSIGNMENTS;
--   COMMIT;
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V20_1.1 Kiem tra truoc ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME IN ('EDU_CLASSES', 'EDU_CLASS_SESSIONS');
    IF V_COUNT <> 2 THEN
        RAISE_APPLICATION_ERROR(-20001, 'V20_1: thieu EDU_CLASSES / EDU_CLASS_SESSIONS');
    END IF;
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME = 'SYS_FILE_MODULE_SETTINGS';
    IF V_COUNT <> 1 THEN
        RAISE_APPLICATION_ERROR(-20002, 'V20_1: chua chay V19_2 (SYS_FILE_MODULE_SETTINGS)');
    END IF;
    DBMS_OUTPUT.PUT_LINE('  EDU_CLASSES, EDU_CLASS_SESSIONS, V19   OK');
END;
/

PROMPT ============ V20_1.2 Sequence ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_LMS_ASSIGNMENTS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_LMS_ASSIGNMENTS START WITH 1 INCREMENT BY 1 NOCYCLE';
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_ASSIGNMENTS                     created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_ASSIGNMENTS                     already exists');
    END IF;
END;
/

PROMPT ============ V20_1.3 Bang LMS_ASSIGNMENTS ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME = 'LMS_ASSIGNMENTS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE LMS_ASSIGNMENTS (
                ID                 NUMBER                              NOT NULL,
                CLASS_ID           NUMBER                              NOT NULL,
                SESSION_ID         NUMBER,
                TITLE              VARCHAR2(200 CHAR)                  NOT NULL,
                DESCRIPTION        CLOB,
                SUBMISSION_TYPE    VARCHAR2(10)   DEFAULT 'BOTH'       NOT NULL,
                MAX_SCORE          NUMBER(5,2)    DEFAULT 10           NOT NULL,
                DUE_AT             TIMESTAMP                           NOT NULL,
                ALLOW_LATE         NUMBER(1)      DEFAULT 0            NOT NULL,
                LATE_UNTIL         TIMESTAMP,
                MAX_FILES          NUMBER(2)      DEFAULT 5            NOT NULL,
                ALLOW_RESUBMIT     NUMBER(1)      DEFAULT 1            NOT NULL,
                SYNC_TO_GRADEBOOK  NUMBER(1)      DEFAULT 0            NOT NULL,
                GRADE_WEIGHT       NUMBER(5,2)    DEFAULT 1            NOT NULL,
                STATUS             VARCHAR2(20)   DEFAULT 'DRAFT'      NOT NULL,
                PUBLISHED_AT       TIMESTAMP,
                CLOSED_AT          TIMESTAMP,
                VERSION            NUMBER(10)     DEFAULT 0            NOT NULL,
                IS_DELETED         NUMBER(1)      DEFAULT 0            NOT NULL,
                CREATED_AT         TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                UPDATED_AT         TIMESTAMP,
                CREATED_BY         VARCHAR2(50),
                UPDATED_BY         VARCHAR2(50),
                CONSTRAINT PK_LMS_ASSIGNMENTS PRIMARY KEY (ID),
                CONSTRAINT FK_ASG_CLASS   FOREIGN KEY (CLASS_ID)   REFERENCES EDU_CLASSES (ID),
                CONSTRAINT FK_ASG_SESSION FOREIGN KEY (SESSION_ID) REFERENCES EDU_CLASS_SESSIONS (ID),
                CONSTRAINT CK_ASG_SUB_TYPE CHECK (SUBMISSION_TYPE IN ('TEXT', 'FILE', 'BOTH')),
                CONSTRAINT CK_ASG_STATUS   CHECK (STATUS IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
                CONSTRAINT CK_ASG_MAX_SCORE CHECK (MAX_SCORE > 0 AND MAX_SCORE <= 100),
                CONSTRAINT CK_ASG_MAX_FILES CHECK (MAX_FILES BETWEEN 0 AND 10),
                CONSTRAINT CK_ASG_WEIGHT   CHECK (GRADE_WEIGHT > 0),
                CONSTRAINT CK_ASG_FLAGS    CHECK (ALLOW_LATE IN (0, 1) AND ALLOW_RESUBMIT IN (0, 1)
                                                 AND SYNC_TO_GRADEBOOK IN (0, 1) AND IS_DELETED IN (0, 1)),
                CONSTRAINT CK_ASG_LATE     CHECK (LATE_UNTIL IS NULL OR (ALLOW_LATE = 1 AND LATE_UNTIL > DUE_AT))
            )]';
        DBMS_OUTPUT.PUT_LINE('  LMS_ASSIGNMENTS                         created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  LMS_ASSIGNMENTS                         already exists');
    END IF;
END;
/

COMMENT ON TABLE LMS_ASSIGNMENTS IS 'Bai tap ve nha theo lop (GD2)';
COMMENT ON COLUMN LMS_ASSIGNMENTS.DESCRIPTION IS 'HTML CKEditor da sanitize o backend';
COMMENT ON COLUMN LMS_ASSIGNMENTS.DUE_AT IS 'Han nop, gio server Asia/Ho_Chi_Minh';
COMMENT ON COLUMN LMS_ASSIGNMENTS.LATE_UNTIL IS 'Han cuoi nop muon (ALLOW_LATE=1); NULL = nop muon khong gioi han den khi CLOSED';
COMMENT ON COLUMN LMS_ASSIGNMENTS.MAX_FILES IS 'So file toi da / bai nop (<= MAX_FILES_PER_REF cua module SUBMISSION)';
COMMENT ON COLUMN LMS_ASSIGNMENTS.SYNC_TO_GRADEBOOK IS '1 = cham diem thi day vao EDU_GRADES (V20_4)';
COMMENT ON COLUMN LMS_ASSIGNMENTS.VERSION IS 'Optimistic lock (JPA @Version)';

PROMPT ============ V20_1.4 Index ============

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
    ENSURE_INDEX('IX_ASG_CLASS_STATUS_DUE',
        'CREATE INDEX IX_ASG_CLASS_STATUS_DUE ON LMS_ASSIGNMENTS (CLASS_ID, STATUS, DUE_AT)');
    ENSURE_INDEX('IX_ASG_SESSION',
        'CREATE INDEX IX_ASG_SESSION ON LMS_ASSIGNMENTS (SESSION_ID)');
END;
/

PROMPT ============ V20_1.5 Kiem tra sau ============

DECLARE
    V_MISSING VARCHAR2(4000);
    V_COUNT   NUMBER;
BEGIN
    FOR r IN (SELECT n.OBJECT_NAME, n.OBJECT_TYPE, NVL(o.STATUS, 'MISSING') STATUS
                FROM (SELECT 'LMS_ASSIGNMENTS' OBJECT_NAME, 'TABLE' OBJECT_TYPE FROM DUAL UNION ALL
                      SELECT 'SEQ_LMS_ASSIGNMENTS', 'SEQUENCE' FROM DUAL UNION ALL
                      SELECT 'IX_ASG_CLASS_STATUS_DUE', 'INDEX' FROM DUAL UNION ALL
                      SELECT 'IX_ASG_SESSION', 'INDEX' FROM DUAL) n
                LEFT JOIN USER_OBJECTS o ON o.OBJECT_NAME = n.OBJECT_NAME AND o.OBJECT_TYPE = n.OBJECT_TYPE) LOOP
        DBMS_OUTPUT.PUT_LINE('  ' || RPAD(r.OBJECT_NAME, 40) || r.STATUS);
        IF r.STATUS <> 'VALID' THEN
            V_MISSING := V_MISSING || ' ' || r.OBJECT_NAME;
        END IF;
    END LOOP;
    SELECT COUNT(*) INTO V_COUNT FROM USER_CONSTRAINTS
     WHERE TABLE_NAME = 'LMS_ASSIGNMENTS' AND STATUS = 'ENABLED'
       AND CONSTRAINT_NAME IN ('CK_ASG_SUB_TYPE', 'CK_ASG_STATUS', 'CK_ASG_LATE', 'FK_ASG_CLASS', 'FK_ASG_SESSION');
    IF V_COUNT <> 5 THEN
        V_MISSING := V_MISSING || ' LMS_ASSIGNMENTS_CONSTRAINTS';
    END IF;
    IF V_MISSING IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20009, 'V20_1: thieu/khong hop le:' || V_MISSING);
    END IF;
END;
/

COMMIT;

PROMPT ============ V20_1 DONE ============
EXIT
