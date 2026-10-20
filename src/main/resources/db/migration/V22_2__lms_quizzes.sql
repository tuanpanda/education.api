-- =============================================================================
-- EDUCATION - MIGRATION V22_2: GIAI DOAN 4 - DE KIEM TRA (LMS_QUIZZES, LMS_QUIZ_QUESTIONS, LMS_QUIZ_RULES)
--
-- Giai doan 4 (feat/GD4). Hop dong: docs/contracts/quiz-api.md (v1.1), dac ta docs/specs/GD4.md (v1.1).
--
-- Script nay lam:
--   V22_2.1 Kiem tra truoc: LMS_QUESTIONS (V22_1), EDU_CLASSES, LMS_LESSONS (V21_2).
--   V22_2.2 Sequence SEQ_LMS_QUIZZES, SEQ_LMS_QUIZ_QUESTIONS, SEQ_LMS_QUIZ_RULES
--   V22_2.3 Bang LMS_QUIZZES (CK CLOSE_AT > OPEN_AT, SYNC_TO_GRADEBOOK mac dinh 0, CLOSED_AT = luc dong de),
--           LMS_QUIZ_QUESTIONS (UNIQUE QUIZ_ID, QUESTION_ID; SNAPSHOT chup khi publish),
--           LMS_QUIZ_RULES (CANDIDATE_SNAPSHOT CLOB + CK IS JSON: mang anh chup cau ung vien, khoa khi publish)
--   V22_2.4 Index (CLASS_ID, STATUS, OPEN_AT)
--   V22_2.5 Kiem tra sau
--
-- QUY UOC: idempotent (kiem tra USER_* truoc khi tao), file thuan ASCII, ten tieng Viet dung UNISTR,
--   ID tu SEQ_*, WHENEVER SQLERROR EXIT ... ROLLBACK, ket thuc COMMIT + EXIT.
--
-- THU TU CHAY: sau V22_1.
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe EDUCATION@//localhost:1521/ORCL @V22_2__<ten file>.sql
--
-- ROLLBACK:
--   DROP TABLE LMS_QUIZ_RULES CASCADE CONSTRAINTS PURGE;
--   DROP TABLE LMS_QUIZ_QUESTIONS CASCADE CONSTRAINTS PURGE;
--   DROP TABLE LMS_QUIZZES CASCADE CONSTRAINTS PURGE;
--   DROP SEQUENCE SEQ_LMS_QUIZ_RULES;
--   DROP SEQUENCE SEQ_LMS_QUIZ_QUESTIONS;
--   DROP SEQUENCE SEQ_LMS_QUIZZES;
--   COMMIT;
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V22_2.1 Kiem tra truoc ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME IN ('LMS_QUESTIONS', 'EDU_CLASSES', 'LMS_LESSONS');
    IF V_COUNT <> 3 THEN
        RAISE_APPLICATION_ERROR(-20001, 'V22_2: chua chay V22_1 / thieu EDU_CLASSES, LMS_LESSONS');
    END IF;
    DBMS_OUTPUT.PUT_LINE('  pre-check OK');
END;
/

PROMPT ============ V22_2.2 Sequence ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_LMS_QUIZZES';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_LMS_QUIZZES START WITH 1 INCREMENT BY 1 NOCYCLE';
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_QUIZZES                         created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_QUIZZES                         already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_LMS_QUIZ_QUESTIONS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_LMS_QUIZ_QUESTIONS START WITH 1 INCREMENT BY 1 NOCYCLE';
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_QUIZ_QUESTIONS                  created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_QUIZ_QUESTIONS                  already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_LMS_QUIZ_RULES';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_LMS_QUIZ_RULES START WITH 1 INCREMENT BY 1 NOCYCLE';
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_QUIZ_RULES                      created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_QUIZ_RULES                      already exists');
    END IF;
END;
/

PROMPT ============ V22_2.3 Bang ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME = 'LMS_QUIZZES';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE LMS_QUIZZES (
                ID                  NUMBER                              NOT NULL,
                CLASS_ID            NUMBER                              NOT NULL,
                LESSON_ID           NUMBER,
                TITLE               VARCHAR2(200)                       NOT NULL,
                DESCRIPTION         CLOB,
                OPEN_AT             TIMESTAMP                           NOT NULL,
                CLOSE_AT            TIMESTAMP                           NOT NULL,
                DURATION_MIN        NUMBER(5),
                MAX_ATTEMPTS        NUMBER(3)      DEFAULT 1            NOT NULL,
                SHUFFLE_QUESTIONS   NUMBER(1)      DEFAULT 0            NOT NULL,
                SHUFFLE_OPTIONS     NUMBER(1)      DEFAULT 0            NOT NULL,
                RESULT_MODE         VARCHAR2(20)   DEFAULT 'AFTER_CLOSE' NOT NULL,
                RESULTS_RELEASED_AT TIMESTAMP,
                SCORE_POLICY        VARCHAR2(10)   DEFAULT 'HIGHEST'    NOT NULL,
                PASS_SCORE          NUMBER(7,2),
                MAX_SCORE           NUMBER(7,2)    DEFAULT 0            NOT NULL,
                SYNC_TO_GRADEBOOK   NUMBER(1)      DEFAULT 0            NOT NULL,
                STATUS              VARCHAR2(20)   DEFAULT 'DRAFT'      NOT NULL,
                PUBLISHED_AT        TIMESTAMP,
                CLOSED_AT           TIMESTAMP,
                VERSION             NUMBER(19)     DEFAULT 0            NOT NULL,
                IS_DELETED         NUMBER(1)      DEFAULT 0            NOT NULL,
                CREATED_AT         TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                UPDATED_AT         TIMESTAMP,
                CREATED_BY         VARCHAR2(50),
                UPDATED_BY         VARCHAR2(50),
                CONSTRAINT PK_LMS_QUIZZES PRIMARY KEY (ID),
                CONSTRAINT FK_QUIZ_CLASS  FOREIGN KEY (CLASS_ID)  REFERENCES EDU_CLASSES (ID),
                CONSTRAINT FK_QUIZ_LESSON FOREIGN KEY (LESSON_ID) REFERENCES LMS_LESSONS (ID),
                CONSTRAINT CK_QUIZ_TIME   CHECK (CLOSE_AT > OPEN_AT),
                CONSTRAINT CK_QUIZ_DUR    CHECK (DURATION_MIN IS NULL OR DURATION_MIN BETWEEN 1 AND 300),
                CONSTRAINT CK_QUIZ_ATT    CHECK (MAX_ATTEMPTS BETWEEN 1 AND 10),
                CONSTRAINT CK_QUIZ_MODE   CHECK (RESULT_MODE IN ('IMMEDIATE', 'AFTER_CLOSE', 'MANUAL')),
                CONSTRAINT CK_QUIZ_POLICY CHECK (SCORE_POLICY IN ('HIGHEST', 'LAST', 'AVERAGE')),
                CONSTRAINT CK_QUIZ_STATUS CHECK (STATUS IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
                CONSTRAINT CK_QUIZ_FLAGS  CHECK (SHUFFLE_QUESTIONS IN (0, 1) AND SHUFFLE_OPTIONS IN (0, 1)
                                                 AND SYNC_TO_GRADEBOOK IN (0, 1) AND IS_DELETED IN (0, 1))
            )]';
        DBMS_OUTPUT.PUT_LINE('  LMS_QUIZZES                             created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  LMS_QUIZZES                             already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME = 'LMS_QUIZ_QUESTIONS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE LMS_QUIZ_QUESTIONS (
                ID                 NUMBER                              NOT NULL,
                QUIZ_ID            NUMBER                              NOT NULL,
                QUESTION_ID        NUMBER                              NOT NULL,
                POINTS             NUMBER(7,2)                         NOT NULL,
                SORT_ORDER         NUMBER(5)      DEFAULT 0            NOT NULL,
                SNAPSHOT           CLOB,
                CONSTRAINT PK_LMS_QUIZ_QUESTIONS PRIMARY KEY (ID),
                CONSTRAINT FK_QQ_QUIZ     FOREIGN KEY (QUIZ_ID)     REFERENCES LMS_QUIZZES (ID),
                CONSTRAINT FK_QQ_QUESTION FOREIGN KEY (QUESTION_ID) REFERENCES LMS_QUESTIONS (ID),
                CONSTRAINT UQ_QQ_QUIZ_QUESTION UNIQUE (QUIZ_ID, QUESTION_ID),
                CONSTRAINT CK_QQ_POINTS   CHECK (POINTS > 0)
            )]';
        DBMS_OUTPUT.PUT_LINE('  LMS_QUIZ_QUESTIONS                      created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  LMS_QUIZ_QUESTIONS                      already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME = 'LMS_QUIZ_RULES';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE LMS_QUIZ_RULES (
                ID                 NUMBER                              NOT NULL,
                QUIZ_ID            NUMBER                              NOT NULL,
                TOPIC              VARCHAR2(200)                       NOT NULL,
                DIFFICULTY         NUMBER(1)                           NOT NULL,
                QUESTION_COUNT     NUMBER(5)                           NOT NULL,
                POINTS_EACH        NUMBER(7,2)                         NOT NULL,
                SORT_ORDER         NUMBER(5)      DEFAULT 0            NOT NULL,
                CANDIDATE_SNAPSHOT CLOB,
                CONSTRAINT PK_LMS_QUIZ_RULES PRIMARY KEY (ID),
                CONSTRAINT FK_QR_QUIZ   FOREIGN KEY (QUIZ_ID) REFERENCES LMS_QUIZZES (ID),
                CONSTRAINT CK_QR_DIFF   CHECK (DIFFICULTY BETWEEN 1 AND 3),
                CONSTRAINT CK_QR_COUNT  CHECK (QUESTION_COUNT >= 1),
                CONSTRAINT CK_QR_POINTS CHECK (POINTS_EACH > 0),
                CONSTRAINT CK_QR_CAND_JSON CHECK (CANDIDATE_SNAPSHOT IS JSON)
            )]';
        DBMS_OUTPUT.PUT_LINE('  LMS_QUIZ_RULES                          created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  LMS_QUIZ_RULES                          already exists');
    END IF;
END;
/

COMMENT ON TABLE LMS_QUIZZES IS 'De kiem tra online (GD4)';
COMMENT ON COLUMN LMS_QUIZZES.CLOSED_AT IS 'Luc dong de (dong som); NULL = chua dong';

PROMPT ============ V22_2.4 Index ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_INDEXES WHERE INDEX_NAME = 'IX_QUIZ_CLASS_STATUS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_QUIZ_CLASS_STATUS ON LMS_QUIZZES (CLASS_ID, STATUS, OPEN_AT)';
        DBMS_OUTPUT.PUT_LINE('  IX_QUIZ_CLASS_STATUS                    created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  IX_QUIZ_CLASS_STATUS                    already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_INDEXES WHERE INDEX_NAME = 'IX_QR_QUIZ';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_QR_QUIZ ON LMS_QUIZ_RULES (QUIZ_ID)';
        DBMS_OUTPUT.PUT_LINE('  IX_QR_QUIZ                              created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  IX_QR_QUIZ                              already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_INDEXES WHERE INDEX_NAME = 'IX_QQ_QUESTION';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_QQ_QUESTION ON LMS_QUIZ_QUESTIONS (QUESTION_ID)';
        DBMS_OUTPUT.PUT_LINE('  IX_QQ_QUESTION                          created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  IX_QQ_QUESTION                          already exists');
    END IF;
END;
/

PROMPT ============ V22_2.5 Kiem tra sau ============

DECLARE
    V_MISSING VARCHAR2(4000);
    V_COUNT   NUMBER;
BEGIN
    FOR r IN (SELECT n.OBJECT_NAME, n.OBJECT_TYPE, NVL(o.STATUS, 'MISSING') STATUS
                FROM (SELECT 'LMS_QUIZZES' OBJECT_NAME, 'TABLE' OBJECT_TYPE FROM DUAL UNION ALL
                      SELECT 'LMS_QUIZ_QUESTIONS', 'TABLE' FROM DUAL UNION ALL
                      SELECT 'LMS_QUIZ_RULES', 'TABLE' FROM DUAL UNION ALL
                      SELECT 'SEQ_LMS_QUIZZES', 'SEQUENCE' FROM DUAL UNION ALL
                      SELECT 'SEQ_LMS_QUIZ_QUESTIONS', 'SEQUENCE' FROM DUAL UNION ALL
                      SELECT 'SEQ_LMS_QUIZ_RULES', 'SEQUENCE' FROM DUAL UNION ALL
                      SELECT 'IX_QUIZ_CLASS_STATUS', 'INDEX' FROM DUAL UNION ALL
                      SELECT 'IX_QR_QUIZ', 'INDEX' FROM DUAL UNION ALL
                      SELECT 'IX_QQ_QUESTION', 'INDEX' FROM DUAL) n
                LEFT JOIN USER_OBJECTS o ON o.OBJECT_NAME = n.OBJECT_NAME AND o.OBJECT_TYPE = n.OBJECT_TYPE) LOOP
        DBMS_OUTPUT.PUT_LINE('  ' || RPAD(r.OBJECT_NAME, 40) || r.STATUS);
        IF r.STATUS <> 'VALID' THEN
            V_MISSING := V_MISSING || ' ' || r.OBJECT_NAME;
        END IF;
    END LOOP;
    IF V_MISSING IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20009, 'V22_2.5: thieu/khong hop le:' || V_MISSING);
    END IF;
    SELECT COUNT(*) INTO V_COUNT FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'LMS_QUIZ_RULES' AND COLUMN_NAME = 'CANDIDATE_SNAPSHOT' AND DATA_TYPE = 'CLOB';
    IF V_COUNT = 0 THEN
        RAISE_APPLICATION_ERROR(-20010, 'V22_2.5: LMS_QUIZ_RULES.CANDIDATE_SNAPSHOT khong phai CLOB');
    END IF;
    SELECT COUNT(*) INTO V_COUNT FROM USER_CONSTRAINTS
     WHERE TABLE_NAME = 'LMS_QUIZ_RULES' AND CONSTRAINT_NAME = 'CK_QR_CAND_JSON' AND CONSTRAINT_TYPE = 'C';
    IF V_COUNT = 0 THEN
        RAISE_APPLICATION_ERROR(-20011, 'V22_2.5: thieu CK_QR_CAND_JSON (IS JSON)');
    END IF;
    DBMS_OUTPUT.PUT_LINE('  CANDIDATE_SNAPSHOT CLOB + CK_QR_CAND_JSON  OK');
END;
/

COMMIT;

PROMPT ============ V22_2.5 DONE ============
EXIT
