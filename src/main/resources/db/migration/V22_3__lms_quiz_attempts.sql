-- =============================================================================
-- EDUCATION - MIGRATION V22_3: GIAI DOAN 4 - LUOT LAM BAI (LMS_QUIZ_ATTEMPTS, LMS_QUIZ_ANSWERS, LMS_QUIZ_ATTEMPT_EVENTS)
--
-- Giai doan 4 (feat/GD4). Hop dong: docs/contracts/quiz-api.md (v1.1), dac ta docs/specs/GD4.md (v1.1).
--
-- Script nay lam:
--   V22_3.1 Kiem tra truoc: LMS_QUIZZES (V22_2), EDU_STUDENTS.
--   V22_3.2 Sequence SEQ_LMS_QUIZ_ATTEMPTS, SEQ_LMS_QUIZ_ANSWERS, SEQ_LMS_QUIZ_ATTEMPT_EVENTS
--   V22_3.3 Bang LMS_QUIZ_ATTEMPTS (UNIQUE QUIZ_ID, STUDENT_ID, ATTEMPT_NO), LMS_QUIZ_ANSWERS
--           (UQ_QANS_ATTEMPT_QUESTION), LMS_QUIZ_ATTEMPT_EVENTS
--   V22_3.4 Index: unique index ham UX_QATT_ONE_IN_PROGRESS (toi da 1 IN_PROGRESS / HS / de),
--           IX_QATT_STATUS_DEADLINE (STATUS, DEADLINE_AT) cho job tu nop, IX_QEVT_ATTEMPT
--   V22_3.5 Kiem tra sau
--
-- QUY UOC: idempotent (kiem tra USER_* truoc khi tao), file thuan ASCII, ten tieng Viet dung UNISTR,
--   ID tu SEQ_*, WHENEVER SQLERROR EXIT ... ROLLBACK, ket thuc COMMIT + EXIT.
--
-- THU TU CHAY: sau V22_2.
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe EDUCATION@//localhost:1521/ORCL @V22_3__<ten file>.sql
--
-- ROLLBACK:
--   DROP TABLE LMS_QUIZ_ATTEMPT_EVENTS CASCADE CONSTRAINTS PURGE;
--   DROP TABLE LMS_QUIZ_ANSWERS CASCADE CONSTRAINTS PURGE;
--   DROP TABLE LMS_QUIZ_ATTEMPTS CASCADE CONSTRAINTS PURGE;
--   DROP SEQUENCE SEQ_LMS_QUIZ_ATTEMPT_EVENTS;
--   DROP SEQUENCE SEQ_LMS_QUIZ_ANSWERS;
--   DROP SEQUENCE SEQ_LMS_QUIZ_ATTEMPTS;
--   COMMIT;
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V22_3.1 Kiem tra truoc ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME IN ('LMS_QUIZZES', 'EDU_STUDENTS');
    IF V_COUNT <> 2 THEN
        RAISE_APPLICATION_ERROR(-20001, 'V22_3: chua chay V22_2 / thieu EDU_STUDENTS');
    END IF;
    DBMS_OUTPUT.PUT_LINE('  pre-check OK');
END;
/

PROMPT ============ V22_3.2 Sequence ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_LMS_QUIZ_ATTEMPTS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_LMS_QUIZ_ATTEMPTS START WITH 1 INCREMENT BY 1 NOCYCLE';
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_QUIZ_ATTEMPTS                   created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_QUIZ_ATTEMPTS                   already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_LMS_QUIZ_ANSWERS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_LMS_QUIZ_ANSWERS START WITH 1 INCREMENT BY 1 NOCYCLE';
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_QUIZ_ANSWERS                    created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_QUIZ_ANSWERS                    already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_LMS_QUIZ_ATTEMPT_EVENTS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_LMS_QUIZ_ATTEMPT_EVENTS START WITH 1 INCREMENT BY 1 NOCYCLE';
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_QUIZ_ATTEMPT_EVENTS             created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  SEQ_LMS_QUIZ_ATTEMPT_EVENTS             already exists');
    END IF;
END;
/

PROMPT ============ V22_3.3 Bang ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME = 'LMS_QUIZ_ATTEMPTS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE LMS_QUIZ_ATTEMPTS (
                ID                 NUMBER                              NOT NULL,
                QUIZ_ID            NUMBER                              NOT NULL,
                STUDENT_ID         NUMBER                              NOT NULL,
                ATTEMPT_NO         NUMBER(3)                           NOT NULL,
                STATUS             VARCHAR2(20)   DEFAULT 'IN_PROGRESS' NOT NULL,
                STARTED_AT         TIMESTAMP                           NOT NULL,
                DEADLINE_AT        TIMESTAMP                           NOT NULL,
                SUBMITTED_AT       TIMESTAMP,
                SCORE              NUMBER(7,2),
                MAX_SCORE          NUMBER(7,2),
                QUESTION_ORDER     CLOB,
                IP_ADDRESS         VARCHAR2(64),
                USER_AGENT         VARCHAR2(500),
                FOCUS_LOST_COUNT   NUMBER(10)     DEFAULT 0            NOT NULL,
                FLAGS              VARCHAR2(200),
                VERSION            NUMBER(19)     DEFAULT 0            NOT NULL,
                CREATED_AT         TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                UPDATED_AT         TIMESTAMP,
                CREATED_BY         VARCHAR2(50),
                UPDATED_BY         VARCHAR2(50),
                CONSTRAINT PK_LMS_QUIZ_ATTEMPTS PRIMARY KEY (ID),
                CONSTRAINT FK_QATT_QUIZ    FOREIGN KEY (QUIZ_ID)    REFERENCES LMS_QUIZZES (ID),
                CONSTRAINT FK_QATT_STUDENT FOREIGN KEY (STUDENT_ID) REFERENCES EDU_STUDENTS (ID),
                CONSTRAINT UQ_QATT_QUIZ_STUDENT_NO UNIQUE (QUIZ_ID, STUDENT_ID, ATTEMPT_NO),
                CONSTRAINT CK_QATT_STATUS  CHECK (STATUS IN ('IN_PROGRESS', 'SUBMITTED', 'AUTO_SUBMITTED', 'GRADED'))
            )]';
        DBMS_OUTPUT.PUT_LINE('  LMS_QUIZ_ATTEMPTS                       created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  LMS_QUIZ_ATTEMPTS                       already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME = 'LMS_QUIZ_ANSWERS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE LMS_QUIZ_ANSWERS (
                ID                  NUMBER                              NOT NULL,
                ATTEMPT_ID          NUMBER                              NOT NULL,
                QUESTION_ID         NUMBER                              NOT NULL,
                SORT_ORDER          NUMBER(5)      DEFAULT 0            NOT NULL,
                POINTS              NUMBER(7,2)                         NOT NULL,
                SELECTED_OPTION_IDS VARCHAR2(400),
                TEXT_ANSWER         CLOB,
                IS_CORRECT          NUMBER(1),
                POINTS_AWARDED      NUMBER(7,2),
                GRADED_BY           VARCHAR2(50),
                GRADED_AT           TIMESTAMP,
                SAVED_AT            TIMESTAMP,
                SNAPSHOT            CLOB                                NOT NULL,
                GRADER_COMMENT      VARCHAR2(2000),
                OVERRIDE_REASON     VARCHAR2(500),
                VERSION             NUMBER(19)     DEFAULT 0            NOT NULL,
                CONSTRAINT PK_LMS_QUIZ_ANSWERS PRIMARY KEY (ID),
                CONSTRAINT FK_QANS_ATTEMPT FOREIGN KEY (ATTEMPT_ID) REFERENCES LMS_QUIZ_ATTEMPTS (ID),
                CONSTRAINT UQ_QANS_ATTEMPT_QUESTION UNIQUE (ATTEMPT_ID, QUESTION_ID),
                CONSTRAINT CK_QANS_CORRECT CHECK (IS_CORRECT IS NULL OR IS_CORRECT IN (0, 1))
            )]';
        DBMS_OUTPUT.PUT_LINE('  LMS_QUIZ_ANSWERS                        created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  LMS_QUIZ_ANSWERS                        already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_TABLES WHERE TABLE_NAME = 'LMS_QUIZ_ATTEMPT_EVENTS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE q'[
            CREATE TABLE LMS_QUIZ_ATTEMPT_EVENTS (
                ID                 NUMBER                              NOT NULL,
                ATTEMPT_ID         NUMBER                              NOT NULL,
                EVENT_TYPE         VARCHAR2(20)                        NOT NULL,
                OCCURRED_AT        TIMESTAMP                           NOT NULL,
                IP_ADDRESS         VARCHAR2(64),
                USER_AGENT         VARCHAR2(500),
                CONSTRAINT PK_LMS_QUIZ_ATTEMPT_EVENTS PRIMARY KEY (ID),
                CONSTRAINT FK_QEVT_ATTEMPT FOREIGN KEY (ATTEMPT_ID) REFERENCES LMS_QUIZ_ATTEMPTS (ID)
            )]';
        DBMS_OUTPUT.PUT_LINE('  LMS_QUIZ_ATTEMPT_EVENTS                 created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  LMS_QUIZ_ATTEMPT_EVENTS                 already exists');
    END IF;
END;
/

COMMENT ON TABLE LMS_QUIZ_ATTEMPTS IS 'Luot lam bai kiem tra (GD4)';

PROMPT ============ V22_3.4 Index ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_INDEXES WHERE INDEX_NAME = 'UX_QATT_ONE_IN_PROGRESS';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UX_QATT_ONE_IN_PROGRESS ON LMS_QUIZ_ATTEMPTS (CASE WHEN STATUS = ''IN_PROGRESS'' THEN QUIZ_ID END, CASE WHEN STATUS = ''IN_PROGRESS'' THEN STUDENT_ID END)';
        DBMS_OUTPUT.PUT_LINE('  UX_QATT_ONE_IN_PROGRESS                 created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  UX_QATT_ONE_IN_PROGRESS                 already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_INDEXES WHERE INDEX_NAME = 'IX_QATT_STATUS_DEADLINE';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_QATT_STATUS_DEADLINE ON LMS_QUIZ_ATTEMPTS (STATUS, DEADLINE_AT)';
        DBMS_OUTPUT.PUT_LINE('  IX_QATT_STATUS_DEADLINE                 created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  IX_QATT_STATUS_DEADLINE                 already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_INDEXES WHERE INDEX_NAME = 'IX_QATT_STUDENT';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_QATT_STUDENT ON LMS_QUIZ_ATTEMPTS (STUDENT_ID, QUIZ_ID)';
        DBMS_OUTPUT.PUT_LINE('  IX_QATT_STUDENT                         created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  IX_QATT_STUDENT                         already exists');
    END IF;
END;
/

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT FROM USER_INDEXES WHERE INDEX_NAME = 'IX_QEVT_ATTEMPT';
    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_QEVT_ATTEMPT ON LMS_QUIZ_ATTEMPT_EVENTS (ATTEMPT_ID)';
        DBMS_OUTPUT.PUT_LINE('  IX_QEVT_ATTEMPT                         created');
    ELSE
        DBMS_OUTPUT.PUT_LINE('  IX_QEVT_ATTEMPT                         already exists');
    END IF;
END;
/

PROMPT ============ V22_3.5 Kiem tra sau ============

DECLARE
    V_MISSING VARCHAR2(4000);
BEGIN
    FOR r IN (SELECT n.OBJECT_NAME, n.OBJECT_TYPE, NVL(o.STATUS, 'MISSING') STATUS
                FROM (SELECT 'LMS_QUIZ_ATTEMPTS' OBJECT_NAME, 'TABLE' OBJECT_TYPE FROM DUAL UNION ALL
                      SELECT 'LMS_QUIZ_ANSWERS', 'TABLE' FROM DUAL UNION ALL
                      SELECT 'LMS_QUIZ_ATTEMPT_EVENTS', 'TABLE' FROM DUAL UNION ALL
                      SELECT 'SEQ_LMS_QUIZ_ATTEMPTS', 'SEQUENCE' FROM DUAL UNION ALL
                      SELECT 'SEQ_LMS_QUIZ_ANSWERS', 'SEQUENCE' FROM DUAL UNION ALL
                      SELECT 'SEQ_LMS_QUIZ_ATTEMPT_EVENTS', 'SEQUENCE' FROM DUAL UNION ALL
                      SELECT 'UX_QATT_ONE_IN_PROGRESS', 'INDEX' FROM DUAL UNION ALL
                      SELECT 'IX_QATT_STATUS_DEADLINE', 'INDEX' FROM DUAL UNION ALL
                      SELECT 'IX_QATT_STUDENT', 'INDEX' FROM DUAL UNION ALL
                      SELECT 'IX_QEVT_ATTEMPT', 'INDEX' FROM DUAL) n
                LEFT JOIN USER_OBJECTS o ON o.OBJECT_NAME = n.OBJECT_NAME AND o.OBJECT_TYPE = n.OBJECT_TYPE) LOOP
        DBMS_OUTPUT.PUT_LINE('  ' || RPAD(r.OBJECT_NAME, 40) || r.STATUS);
        IF r.STATUS <> 'VALID' THEN
            V_MISSING := V_MISSING || ' ' || r.OBJECT_NAME;
        END IF;
    END LOOP;
    IF V_MISSING IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20009, 'V22_3.5: thieu/khong hop le:' || V_MISSING);
    END IF;
END;
/

COMMIT;

PROMPT ============ V22_3.5 DONE ============
EXIT
