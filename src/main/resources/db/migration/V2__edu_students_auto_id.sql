-- =============================================================================
-- EDUCATION - MIGRATION V2: TU DONG SINH MA STUDENT_CODE BANG TRIGGER
--
-- Chuan: Oracle Database-First, di sau V1__education_full_schema.sql.
-- Flyway (neu bat): version 2. Chay thu cong bang SQL*Plus:
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @V2__edu_students_auto_id.sql
--
-- Bang: EDU_STUDENTS.STUDENT_CODE
-- Dinh dang: yyyyMMddHHmmss + XXXX (18 ky tu), XXXX reset 0001 moi ngay.
-- Script IDEMPOTENT: chay lai khong loi, khong xoa du lieu dang co.
-- =============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V2.1 Bang dem so thu tu theo ngay ============

DECLARE
    PROCEDURE DDL(P_SQL VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE P_SQL;
    EXCEPTION
        WHEN OTHERS THEN
            -- -955: name is already used by an existing object
            IF SQLCODE IN (-955) THEN
                NULL;
            ELSE
                RAISE;
            END IF;
    END DDL;
BEGIN
    DDL(q'[
        CREATE TABLE SYS_DAILY_ID_SEQ (
            TABLE_NAME VARCHAR2(30) NOT NULL,
            BIZ_DATE   CHAR(8)      NOT NULL,
            LAST_SEQ   NUMBER(4)    NOT NULL,
            UPDATED_AT TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
            CONSTRAINT PK_SYS_DAILY_ID_SEQ PRIMARY KEY (TABLE_NAME, BIZ_DATE),
            CONSTRAINT CK_DAILY_ID_SEQ_RANGE CHECK (LAST_SEQ BETWEEN 1 AND 9999),
            CONSTRAINT CK_DAILY_ID_SEQ_DATE  CHECK (REGEXP_LIKE(BIZ_DATE, '^[0-9]{8}$'))
        )
    ]');
    DBMS_OUTPUT.PUT_LINE('SYS_DAILY_ID_SEQ: OK');
END;
/

PROMPT ============ V2.2 Trigger BEFORE INSERT EDU_STUDENTS ============

CREATE OR REPLACE TRIGGER TRG_EDU_STUDENTS_BI_CODE
BEFORE INSERT ON EDU_STUDENTS
FOR EACH ROW
DECLARE
    C_TABLE   CONSTANT VARCHAR2(30) := 'EDU_STUDENTS';
    C_MAX_SEQ CONSTANT NUMBER(4)    := 9999;

    V_TS   TIMESTAMP;
    V_DAY  CHAR(8);
    V_SEQ  NUMBER(4);
    V_CODE VARCHAR2(18);
BEGIN
    V_TS  := SYSTIMESTAMP;
    V_DAY := TO_CHAR(V_TS, 'YYYYMMDD');

    BEGIN
        INSERT INTO SYS_DAILY_ID_SEQ (TABLE_NAME, BIZ_DATE, LAST_SEQ, UPDATED_AT)
        VALUES (C_TABLE, V_DAY, 1, V_TS);
        V_SEQ := 1;
    EXCEPTION
        WHEN DUP_VAL_ON_INDEX THEN
            SELECT LAST_SEQ
              INTO V_SEQ
              FROM SYS_DAILY_ID_SEQ
             WHERE TABLE_NAME = C_TABLE
               AND BIZ_DATE   = V_DAY
               FOR UPDATE;

            V_SEQ := V_SEQ + 1;

            IF V_SEQ > C_MAX_SEQ THEN
                RAISE_APPLICATION_ERROR(
                    -20001,
                    'Da vuot qua ' || C_MAX_SEQ || ' ma ID trong ngay '
                    || V_DAY || ' cho bang EDU_STUDENTS.');
            END IF;

            UPDATE SYS_DAILY_ID_SEQ
               SET LAST_SEQ   = V_SEQ,
                   UPDATED_AT = V_TS
             WHERE TABLE_NAME = C_TABLE
               AND BIZ_DATE   = V_DAY;
    END;

    V_CODE := TO_CHAR(V_TS, 'YYYYMMDDHH24MISS') || LPAD(TO_CHAR(V_SEQ), 4, '0');
    :NEW.STUDENT_CODE := V_CODE;
END;
/

PROMPT Trigger TRG_EDU_STUDENTS_BI_CODE: OK

COLUMN STATUS FORMAT A10
COLUMN TRIGGER_NAME FORMAT A32
SELECT TRIGGER_NAME, STATUS, TRIGGER_TYPE, TRIGGERING_EVENT
  FROM USER_TRIGGERS
 WHERE TRIGGER_NAME = 'TRG_EDU_STUDENTS_BI_CODE';

PROMPT ============ V2 HOAN TAT ============
PROMPT Ban ghi EDU_STUDENTS cu giu nguyen STUDENT_CODE.
PROMPT INSERT moi nhan STUDENT_CODE = yyyyMMddHHmmssXXXX (18 ky tu).
/