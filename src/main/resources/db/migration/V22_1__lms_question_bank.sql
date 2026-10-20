-- =============================================================================
-- EDUCATION - MIGRATION V22_1: GIAI DOAN 4 - NGAN HANG CAU HOI (LMS_QUESTIONS, LMS_QUESTION_OPTIONS)
--
-- Giai doan 4 (feat/GD4). Hop dong: docs/contracts/quiz-api.md (v1.1), dac ta docs/specs/GD4.md (v1.1).
--
-- Script nay lam:
--   V22_1.1 Kiem tra truoc: SYS_USERS, LMS_COURSES (V21_1).
--   V22_1.2 Sequence SEQ_LMS_QUESTIONS, SEQ_LMS_QUESTION_OPTIONS
--   V22_1.3 Bang LMS_QUESTIONS (QUESTION_TYPE SINGLE/MULTI/TRUE_FALSE/SHORT_TEXT/ESSAY, DIFFICULTY 1-3,
--           VISIBILITY PRIVATE/SHARED, ACCEPTED_ANSWERS CLOB JSON) + LMS_QUESTION_OPTIONS
--   V22_1.4 Index (SUBJECT_NAME, GRADE_LEVEL, TOPIC, DIFFICULTY), (OWNER_ID), (QUESTION_ID)
--   V22_1.5 Kiem tra sau
--
-- QUY UOC: idempotent (kiem tra USER_* truoc khi tao), file thuan ASCII, ten tieng Viet dung UNISTR,
--   ID tu SEQ_*, WHENEVER SQLERROR EXIT ... ROLLBACK, ket thuc COMMIT + EXIT.
--
-- THU TU CHAY: sau V21_4.
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe EDUCATION@//localhost:1521/ORCL @V22_1__<ten file>.sql
--
-- ROLLBACK:
--   DROP TABLE LMS_QUESTION_OPTIONS CASCADE CONSTRAINTS PURGE;
--   DROP TABLE LMS_QUESTIONS CASCADE CONSTRAINTS PURGE;
--   DROP SEQUENCE SEQ_LMS_QUESTION_OPTIONS;
--   DROP SEQUENCE SEQ_LMS_QUESTIONS;
--   COMMIT;
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V22_1.1 Kiem tra truoc ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME IN ('SYS_USERS', 'LMS_COURSES');
    IF V_COUNT <> 2 THEN
        RAISE_APPLICATION_ERROR(-20001, 'V22_1: thieu SYS_USERS / LMS_COURSES (chay V21_x truoc)');
    END IF;
    DBMS_OUTPUT.PUT_LINE('  pre-check OK');
END;
/

PROMPT ============ V22_1.2 Sequence ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_LMS_QUESTIONS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_LMS_QUESTIONS START WITH 1 INCREMENT BY 1 NOCYCLE';
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_QUESTIONS                       created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_QUESTIONS                       already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_LMS_QUESTION_OPTIONS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_LMS_QUESTION_OPTIONS START WITH 1 INCREMENT BY 1 NOCYCLE';
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_QUESTION_OPTIONS                created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_QUESTION_OPTIONS                already exists');
    END IF;
END;
/

PROMPT ============ V22_1.3 Bang ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME = 'LMS_QUESTIONS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE LMS_QUESTIONS (
                ID                 NUMBER                              NOT NULL,
                QUESTION_TYPE      VARCHAR2(20)                        NOT NULL,
                SUBJECT_NAME       VARCHAR2(100)                       NOT NULL,
                GRADE_LEVEL        VARCHAR2(20)                        NOT NULL,
                TOPIC              VARCHAR2(200)                       NOT NULL,
                DIFFICULTY         NUMBER(1)      DEFAULT 2            NOT NULL,
                CONTENT            CLOB                                NOT NULL,
                EXPLANATION        CLOB,
                ACCEPTED_ANSWERS   CLOB,
                OWNER_ID           NUMBER                              NOT NULL,
                VISIBILITY         VARCHAR2(10)   DEFAULT 'PRIVATE'    NOT NULL,
                COURSE_ID          NUMBER,
                VERSION            NUMBER(19)     DEFAULT 0            NOT NULL,
                IS_DELETED         NUMBER(1)      DEFAULT 0            NOT NULL,
                CREATED_AT         TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                UPDATED_AT         TIMESTAMP,
                CREATED_BY         VARCHAR2(50),
                UPDATED_BY         VARCHAR2(50),
                CONSTRAINT PK_LMS_QUESTIONS PRIMARY KEY (ID),
                CONSTRAINT FK_QST_OWNER  FOREIGN KEY (OWNER_ID)  REFERENCES SYS_USERS (ID),
                CONSTRAINT FK_QST_COURSE FOREIGN KEY (COURSE_ID) REFERENCES LMS_COURSES (ID),
                CONSTRAINT CK_QST_TYPE   CHECK (QUESTION_TYPE IN ('SINGLE', 'MULTI', 'TRUE_FALSE', 'SHORT_TEXT', 'ESSAY')),
                CONSTRAINT CK_QST_DIFF   CHECK (DIFFICULTY BETWEEN 1 AND 3),
                CONSTRAINT CK_QST_VIS    CHECK (VISIBILITY IN ('PRIVATE', 'SHARED')),
                CONSTRAINT CK_QST_DEL    CHECK (IS_DELETED IN (0, 1))
            )]';
        DBMS_OUTPUT.PUT_LINE('  LMS_QUESTIONS                           created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  LMS_QUESTIONS                           already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME = 'LMS_QUESTION_OPTIONS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE LMS_QUESTION_OPTIONS (
                ID                 NUMBER                              NOT NULL,
                QUESTION_ID        NUMBER                              NOT NULL,
                CONTENT            CLOB                                NOT NULL,
                IS_CORRECT         NUMBER(1)      DEFAULT 0            NOT NULL,
                SORT_ORDER         NUMBER(5)      DEFAULT 0            NOT NULL,
                CONSTRAINT PK_LMS_QUESTION_OPTIONS PRIMARY KEY (ID),
                CONSTRAINT FK_QOPT_QUESTION FOREIGN KEY (QUESTION_ID) REFERENCES LMS_QUESTIONS (ID),
                CONSTRAINT CK_QOPT_CORRECT  CHECK (IS_CORRECT IN (0, 1))
            )]';
        DBMS_OUTPUT.PUT_LINE('  LMS_QUESTION_OPTIONS                    created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  LMS_QUESTION_OPTIONS                    already exists');
    END IF;
END;
/

COMMENT ON TABLE LMS_QUESTIONS IS 'Ngan hang cau hoi trac nghiem (GD4)';
COMMENT ON TABLE LMS_QUESTION_OPTIONS IS 'Lua chon cua cau hoi (GD4)';

PROMPT ============ V22_1.4 Index ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_INDEXES WHERE INDEX_NAME = 'IX_QST_FILTER';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_QST_FILTER ON LMS_QUESTIONS (SUBJECT_NAME, GRADE_LEVEL, TOPIC, DIFFICULTY)';
        DBMS_OUTPUT.PUT_LINE('  IX_QST_FILTER                           created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  IX_QST_FILTER                           already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_INDEXES WHERE INDEX_NAME = 'IX_QST_OWNER';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_QST_OWNER ON LMS_QUESTIONS (OWNER_ID)';
        DBMS_OUTPUT.PUT_LINE('  IX_QST_OWNER                            created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  IX_QST_OWNER                            already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_INDEXES WHERE INDEX_NAME = 'IX_QOPT_QUESTION';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_QOPT_QUESTION ON LMS_QUESTION_OPTIONS (QUESTION_ID)';
        DBMS_OUTPUT.PUT_LINE('  IX_QOPT_QUESTION                        created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  IX_QOPT_QUESTION                        already exists');
    END IF;
END;
/

PROMPT ============ V22_1.5 Kiem tra sau ============

DECLARE
    V_MISSING VARCHAR2(4000);
BEGIN
    FOR r IN (SELECT n.OBJECT_NAME, n.OBJECT_TYPE, NVL(o.STATUS, 'MISSING') STATUS
                FROM (SELECT 'LMS_QUESTIONS' OBJECT_NAME, 'TABLE' OBJECT_TYPE FROM DUAL UNION ALL
                      SELECT 'LMS_QUESTION_OPTIONS', 'TABLE' FROM DUAL UNION ALL
                      SELECT 'SEQ_LMS_QUESTIONS', 'SEQUENCE' FROM DUAL UNION ALL
                      SELECT 'SEQ_LMS_QUESTION_OPTIONS', 'SEQUENCE' FROM DUAL UNION ALL
                      SELECT 'IX_QST_FILTER', 'INDEX' FROM DUAL UNION ALL
                      SELECT 'IX_QST_OWNER', 'INDEX' FROM DUAL UNION ALL
                      SELECT 'IX_QOPT_QUESTION', 'INDEX' FROM DUAL) n
                LEFT JOIN USER_OBJECTS o ON o.OBJECT_NAME = n.OBJECT_NAME AND o.OBJECT_TYPE = n.OBJECT_TYPE) LOOP
        DBMS_OUTPUT.PUT_LINE('  ' || RPAD(r.OBJECT_NAME, 40) || r.STATUS);
        IF r.STATUS <> 'VALID' THEN
            V_MISSING := V_MISSING || ' ' || r.OBJECT_NAME;
        END IF;
    END LOOP;
    IF V_MISSING IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20009, 'V22_1.5: thieu/khong hop le:' || V_MISSING);
    END IF;
END;
/

COMMIT;

PROMPT ============ V22_1.5 DONE ============
EXIT
