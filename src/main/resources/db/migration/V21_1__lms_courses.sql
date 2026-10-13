-- =============================================================================
-- EDUCATION - MIGRATION V21_1: GIAI DOAN 3 - HOC LIEU: KHOA HOC MAU (LMS_COURSES)
--
-- Giai doan 3 (feat/portal-p3). Hop dong: docs/portal-p3-api.md.
--
-- Script nay lam:
--   V21_1.1 Kiem tra truoc: EDU_CLASSES ton tai, da chay V20_x (LMS_ASSIGNMENTS).
--   V21_1.2 Sequence SEQ_LMS_COURSES
--   V21_1.3 Bang LMS_COURSES: COURSE_CODE, COURSE_NAME, SUBJECT_NAME, GRADE_LEVEL, DESCRIPTION CLOB (HTML sanitize),
--           STATUS ACTIVE/ARCHIVED, VERSION (optimistic lock), IS_DELETED, audit.
--   V21_1.4 EDU_CLASSES.COURSE_ID NUMBER NULL + FK_CLASSES_COURSE (lop dung chung khoa mau - lien ket song).
--   V21_1.5 Index: UX_COURSES_CODE (ma khoa duy nhat trong cac dong chua xoa, khong phan biet hoa thuong),
--           IX_CLASSES_COURSE (EDU_CLASSES.COURSE_ID).
--   V21_1.6 Kiem tra sau
--
-- QUY UOC:
--   * WHENEVER SQLERROR EXIT ... ROLLBACK; ket thuc bang COMMIT + EXIT. (DDL Oracle tu commit.)
--   * Chay lai an toan: kiem USER_TABLES / USER_SEQUENCES / USER_INDEXES / USER_TAB_COLUMNS truoc khi tao.
--   * File thuan ASCII.
--
-- THU TU CHAY: sau V20_4. V21_1 -> V21_2 -> V21_3 -> V21_4.
--
--   (Windows CMD; nhap mat khau khi sqlplus hoi - KHONG ghi mat khau vao file)
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe EDUCATION@//localhost:1521/ORCL @V21_1__lms_courses.sql
--
-- ROLLBACK (MAT du lieu khoa hoc; chay rollback V21_4, V21_3, V21_2 truoc):
--   ALTER TABLE EDU_CLASSES DROP CONSTRAINT FK_CLASSES_COURSE;
--   DROP INDEX IX_CLASSES_COURSE;
--   ALTER TABLE EDU_CLASSES DROP COLUMN COURSE_ID;
--   DROP TABLE LMS_COURSES CASCADE CONSTRAINTS PURGE;
--   DROP SEQUENCE SEQ_LMS_COURSES;
--   COMMIT;
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V21_1.1 Kiem tra truoc ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME IN ('EDU_CLASSES', 'LMS_ASSIGNMENTS');
    IF V_COUNT <> 2 THEN
        RAISE_APPLICATION_ERROR(-20001, 'V21_1: thieu EDU_CLASSES hoac chua chay V20_1 (LMS_ASSIGNMENTS)');
    END IF;
    SELECT COUNT(*) INTO V_COUNT FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'EDU_GRADES' AND COLUMN_NAME = 'ASSIGNMENT_ID';
    IF V_COUNT <> 1 THEN
        RAISE_APPLICATION_ERROR(-20002, 'V21_1: chua chay V20_4 (EDU_GRADES.ASSIGNMENT_ID)');
    END IF;
    DBMS_OUTPUT.PUT_LINE('  EDU_CLASSES, V20                        OK');
END;
/

PROMPT ============ V21_1.2 Sequence ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_LMS_COURSES';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_LMS_COURSES START WITH 1 INCREMENT BY 1 NOCYCLE';
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_COURSES                         created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_COURSES                         already exists');
    END IF;
END;
/

PROMPT ============ V21_1.3 Bang LMS_COURSES ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME = 'LMS_COURSES';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE LMS_COURSES (
                ID                 NUMBER                              NOT NULL,
                COURSE_CODE        VARCHAR2(50)                        NOT NULL,
                COURSE_NAME        VARCHAR2(200 CHAR)                  NOT NULL,
                SUBJECT_NAME       VARCHAR2(150 CHAR),
                GRADE_LEVEL        NUMBER(2),
                DESCRIPTION        CLOB,
                STATUS             VARCHAR2(20)   DEFAULT 'ACTIVE'     NOT NULL,
                VERSION            NUMBER(10)     DEFAULT 0            NOT NULL,
                IS_DELETED         NUMBER(1)      DEFAULT 0            NOT NULL,
                CREATED_AT         TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                UPDATED_AT         TIMESTAMP,
                CREATED_BY         VARCHAR2(50),
                UPDATED_BY         VARCHAR2(50),
                CONSTRAINT PK_LMS_COURSES PRIMARY KEY (ID),
                CONSTRAINT CK_COURSE_STATUS  CHECK (STATUS IN ('ACTIVE', 'ARCHIVED')),
                CONSTRAINT CK_COURSE_DELETED CHECK (IS_DELETED IN (0, 1)),
                CONSTRAINT CK_COURSE_GRADE   CHECK (GRADE_LEVEL IS NULL OR GRADE_LEVEL BETWEEN 1 AND 12)
            )]';
        DBMS_OUTPUT.PUT_LINE('  LMS_COURSES                             created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  LMS_COURSES                             already exists');
    END IF;
END;
/

COMMENT ON TABLE LMS_COURSES IS 'Khoa hoc mau (GD3): bo chuong/bai dung chung cho nhieu lop cung chuong trinh';
COMMENT ON COLUMN LMS_COURSES.DESCRIPTION IS 'HTML CKEditor da sanitize o backend';
COMMENT ON COLUMN LMS_COURSES.VERSION IS 'Optimistic lock (JPA @Version)';

PROMPT ============ V21_1.4 EDU_CLASSES.COURSE_ID ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'EDU_CLASSES' AND COLUMN_NAME = 'COURSE_ID';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE EDU_CLASSES ADD (COURSE_ID NUMBER)';
        DBMS_OUTPUT.PUT_LINE('  EDU_CLASSES.COURSE_ID                   added');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  EDU_CLASSES.COURSE_ID                   already exists');
    END IF;
    SELECT COUNT(*) INTO V_COUNT FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_CLASSES_COURSE';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE EDU_CLASSES ADD CONSTRAINT FK_CLASSES_COURSE FOREIGN KEY (COURSE_ID) REFERENCES LMS_COURSES (ID)';
        DBMS_OUTPUT.PUT_LINE('  FK_CLASSES_COURSE                       created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  FK_CLASSES_COURSE                       already exists');
    END IF;
END;
/

COMMENT ON COLUMN EDU_CLASSES.COURSE_ID IS 'Khoa hoc mau gan voi lop (GD3, V21_1); NULL = lop chi co bai hoc rieng';

PROMPT ============ V21_1.5 Index ============

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
    ENSURE_INDEX('UX_COURSES_CODE',
        'CREATE UNIQUE INDEX UX_COURSES_CODE ON LMS_COURSES (CASE WHEN IS_DELETED = 0 THEN UPPER(COURSE_CODE) END)');
    ENSURE_INDEX('IX_CLASSES_COURSE',
        'CREATE INDEX IX_CLASSES_COURSE ON EDU_CLASSES (COURSE_ID)');
END;
/

PROMPT ============ V21_1.6 Kiem tra sau ============

DECLARE
    V_MISSING VARCHAR2(4000);
    V_COUNT   NUMBER;
BEGIN
    FOR r IN (SELECT n.OBJECT_NAME, n.OBJECT_TYPE, NVL(o.STATUS, 'MISSING') STATUS
                FROM (SELECT 'LMS_COURSES' OBJECT_NAME, 'TABLE' OBJECT_TYPE FROM DUAL UNION ALL
                      SELECT 'SEQ_LMS_COURSES', 'SEQUENCE' FROM DUAL UNION ALL
                      SELECT 'UX_COURSES_CODE', 'INDEX' FROM DUAL UNION ALL
                      SELECT 'IX_CLASSES_COURSE', 'INDEX' FROM DUAL) n
                LEFT JOIN USER_OBJECTS o ON o.OBJECT_NAME = n.OBJECT_NAME AND o.OBJECT_TYPE = n.OBJECT_TYPE) LOOP
        DBMS_OUTPUT.PUT_LINE('  ' || RPAD(r.OBJECT_NAME, 40) || r.STATUS);
        IF r.STATUS <> 'VALID' THEN
            V_MISSING := V_MISSING || ' ' || r.OBJECT_NAME;
        END IF;
    END LOOP;
    SELECT COUNT(*) INTO V_COUNT FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'EDU_CLASSES' AND COLUMN_NAME = 'COURSE_ID' AND NULLABLE = 'Y';
    IF V_COUNT <> 1 THEN
        V_MISSING := V_MISSING || ' EDU_CLASSES.COURSE_ID';
    END IF;
    SELECT COUNT(*) INTO V_COUNT FROM USER_CONSTRAINTS
     WHERE STATUS = 'ENABLED'
       AND CONSTRAINT_NAME IN ('CK_COURSE_STATUS', 'CK_COURSE_DELETED', 'FK_CLASSES_COURSE');
    IF V_COUNT <> 3 THEN
        V_MISSING := V_MISSING || ' CONSTRAINTS(' || V_COUNT || '/3)';
    END IF;
    IF V_MISSING IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20009, 'V21_1: thieu/khong hop le:' || V_MISSING);
    END IF;
END;
/

COMMIT;

PROMPT ============ V21_1 DONE ============
EXIT
