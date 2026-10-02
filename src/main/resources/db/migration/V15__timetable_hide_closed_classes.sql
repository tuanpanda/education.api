-- =============================================================================
-- EDUCATION - MIGRATION V15: AN LOP DA DONG / DA HUY KHOI THOI KHOA BIEU
--
-- Script nay lam:
--   V15.1 PRC_GET_TIMETABLE_BY_RANGE: bo moi buoi hoc cua lop co EDU_CLASSES.STATUS IN ('CLOSED', 'CANCELLED')
--         (CK_CLASSES_STATUS: PLANNED/OPEN/ONGOING/CLOSED/CANCELLED), ke ca buoi da qua / da COMPLETED.
--         Phan con lai giu nguyen ban V6: tham so, cot tra ve, loc khoang ngay / lop / giang vien /
--         hoc sinh, sap xep va ma loi. Cung quy tac voi DomainConstants.isHiddenFromTimetable
--         (TimetableServiceImpl loc lai lan nua khi DB chua chay V15).
--   V15.2 Kiem tra PRC_GET_TIMETABLE_BY_RANGE VALID sau khi bien dich.
--
-- Khong doi bang / du lieu (EDU_CLASS_SESSIONS giu nguyen, mo lai lop thi buoi hoc hien lai).
-- Chay lai nhieu lan an toan (CREATE OR REPLACE). Thu tu chay: sau V14_3.
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V15__timetable_hide_closed_classes.sql
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V15.1 PRC_GET_TIMETABLE_BY_RANGE ============

CREATE OR REPLACE PROCEDURE PRC_GET_TIMETABLE_BY_RANGE(
    P_FROM_DATE   IN  DATE,
    P_TO_DATE     IN  DATE,
    P_CLASS_ID    IN  NUMBER,
    P_TEACHER_ID  IN  NUMBER,
    P_STUDENT_ID  IN  NUMBER,
    O_CURSOR      OUT SYS_REFCURSOR,
    O_ERR_CODE    OUT VARCHAR2,
    O_ERR_MSG     OUT VARCHAR2
) AS
    V_FROM DATE;
    V_TO   DATE;
    -- American 'D': 1=Sunday ... 7=Saturday. Quy uoc he thong: 2=Thu Hai ... 8=Chu Nhat.
BEGIN
    IF P_FROM_DATE IS NULL OR P_TO_DATE IS NULL THEN
        O_ERR_CODE := 'INVALID_DATE_RANGE';
        O_ERR_MSG  := 'Tu ngay va den ngay khong duoc de trong.';
        RETURN;
    END IF;

    V_FROM := TRUNC(P_FROM_DATE);
    V_TO   := TRUNC(P_TO_DATE);
    IF V_TO < V_FROM THEN
        O_ERR_CODE := 'INVALID_DATE_RANGE';
        O_ERR_MSG  := 'Den ngay khong duoc nho hon tu ngay.';
        RETURN;
    END IF;

    OPEN O_CURSOR FOR
        SELECT s.ID,
               s.CLASS_ID,
               c.CLASS_CODE,
               c.CLASS_NAME,
               s.SCHEDULE_ID,
               s.SESSION_DATE,
               CASE TO_NUMBER(TO_CHAR(s.SESSION_DATE, 'D', 'NLS_DATE_LANGUAGE=AMERICAN'))
                   WHEN 1 THEN 8
                   ELSE TO_NUMBER(TO_CHAR(s.SESSION_DATE, 'D', 'NLS_DATE_LANGUAGE=AMERICAN'))
               END AS DAY_OF_WEEK,
               s.START_TIME,
               s.END_TIME,
               s.ROOM_NAME,
               s.TEACHER_ID,
               u.FULL_NAME AS TEACHER_NAME,
               s.TOPIC,
               s.STATUS,
               s.NOTE
          FROM EDU_CLASS_SESSIONS s
          JOIN EDU_CLASSES c ON c.ID = s.CLASS_ID
          LEFT JOIN SYS_USERS u ON u.ID = s.TEACHER_ID
         WHERE s.IS_DELETED = 0
           -- V15: lop da dong / da huy khong hien tren TKB (an moi buoi, ke ca buoi da qua).
           AND c.STATUS NOT IN ('CLOSED', 'CANCELLED')
           AND s.SESSION_DATE BETWEEN V_FROM AND V_TO
           AND (P_CLASS_ID IS NULL OR s.CLASS_ID = P_CLASS_ID)
           AND (P_TEACHER_ID IS NULL OR s.TEACHER_ID = P_TEACHER_ID)
           AND (P_STUDENT_ID IS NULL OR EXISTS (
                    SELECT 1
                      FROM EDU_CLASS_STUDENTS cs
                     WHERE cs.CLASS_ID = s.CLASS_ID
                       AND cs.STUDENT_ID = P_STUDENT_ID
                       AND cs.IS_DELETED = 0
                       AND cs.STATUS = 'ENROLLED'))
         ORDER BY s.SESSION_DATE, s.START_TIME, s.CLASS_ID;

    O_ERR_CODE := '0';
    O_ERR_MSG  := 'SUCCESS';
EXCEPTION
    WHEN OTHERS THEN
        IF O_CURSOR IS NOT NULL AND O_CURSOR%ISOPEN THEN CLOSE O_CURSOR; END IF;
        O_ERR_CODE := TO_CHAR(SQLCODE);
        O_ERR_MSG  := SUBSTR(SQLERRM, 1, 255);
END PRC_GET_TIMETABLE_BY_RANGE;
/
SHOW ERRORS PROCEDURE PRC_GET_TIMETABLE_BY_RANGE

PROMPT ============ V15.2 Kiem tra bien dich ============

-- CREATE OR REPLACE loi bien dich chi bao Warning (khong kich hoat WHENEVER SQLERROR): kiem tra VALID tuong minh.
DECLARE
    V_STATUS VARCHAR2(20);
BEGIN
    SELECT NVL(MAX(STATUS), 'MISSING')
      INTO V_STATUS
      FROM USER_OBJECTS
     WHERE OBJECT_NAME = 'PRC_GET_TIMETABLE_BY_RANGE'
       AND OBJECT_TYPE = 'PROCEDURE';
    DBMS_OUTPUT.PUT_LINE('  ' || RPAD('PRC_GET_TIMETABLE_BY_RANGE', 32) || V_STATUS);
    IF V_STATUS <> 'VALID' THEN
        RAISE_APPLICATION_ERROR(-20001, 'V15: PRC_GET_TIMETABLE_BY_RANGE khong hop le: ' || V_STATUS);
    END IF;
END;
/

COMMIT;

PROMPT ============ V15 DONE ============
EXIT
