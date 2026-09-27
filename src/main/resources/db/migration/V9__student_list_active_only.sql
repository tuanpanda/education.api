-- =============================================================================
-- EDUCATION - MIGRATION V9: CHI LIET KE HOC SINH ACTIVE
--
-- PRC_SEARCH_STUDENTS va PRC_SEARCH_STUDENTS_PAGING luon loc STATUS = 'ACTIVE'.
-- P_STATUS van nhan de tuong thich API, khong dung de mo rong danh sach.
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @V9__student_list_active_only.sql
-- =============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V9.1 PRC_SEARCH_STUDENTS ============

CREATE OR REPLACE PROCEDURE PRC_SEARCH_STUDENTS(
    P_KEYWORD    IN  VARCHAR2,
    O_CURSOR     OUT SYS_REFCURSOR,
    O_ERR_CODE   OUT VARCHAR2,
    O_ERR_MSG    OUT VARCHAR2
) AS
    V_KEYWORD VARCHAR2(4000);
BEGIN
    V_KEYWORD := LOWER(TRIM(P_KEYWORD));

    OPEN O_CURSOR FOR
        SELECT ID,
               STUDENT_CODE AS studentCode,
               FULL_NAME    AS fullName,
               EMAIL,
               STATUS,
               DATE_OF_BIRTH AS dateOfBirth,
               PARENT_NAME   AS parentName,
               PHONE,
               ADDRESS,
               NOTE
          FROM EDU_STUDENTS
         WHERE IS_DELETED = 0
           AND STATUS = 'ACTIVE'
           AND (V_KEYWORD IS NULL
                OR LOWER(STUDENT_CODE) LIKE '%' || V_KEYWORD || '%'
                OR LOWER(FULL_NAME)    LIKE '%' || V_KEYWORD || '%'
                OR LOWER(EMAIL)        LIKE '%' || V_KEYWORD || '%'
                OR LOWER(PARENT_NAME)  LIKE '%' || V_KEYWORD || '%'
                OR LOWER(PHONE)        LIKE '%' || V_KEYWORD || '%'
                OR LOWER(ADDRESS)      LIKE '%' || V_KEYWORD || '%');

    O_ERR_CODE := '0';
    O_ERR_MSG  := 'SUCCESS';
EXCEPTION
    WHEN OTHERS THEN
        IF O_CURSOR IS NOT NULL AND O_CURSOR%ISOPEN THEN
            CLOSE O_CURSOR;
        END IF;
        O_ERR_CODE := TO_CHAR(SQLCODE);
        O_ERR_MSG  := SUBSTR(SQLERRM, 1, 255);
END PRC_SEARCH_STUDENTS;
/

PROMPT ============ V9.2 PRC_SEARCH_STUDENTS_PAGING ============

CREATE OR REPLACE PROCEDURE PRC_SEARCH_STUDENTS_PAGING(
    P_KEYWORD     IN  VARCHAR2,
    P_STATUS      IN  VARCHAR2,
    P_PAGE_NO     IN  NUMBER,
    P_PAGE_SIZE   IN  NUMBER,
    O_DATA_CURSOR OUT SYS_REFCURSOR,
    O_TOTAL_ROWS  OUT NUMBER,
    O_ERR_CODE    OUT VARCHAR2,
    O_ERR_MSG     OUT VARCHAR2
) AS
    C_DEFAULT_PAGE_SIZE CONSTANT NUMBER := 20;
    C_MAX_PAGE_SIZE     CONSTANT NUMBER := 200;

    V_KEYWORD   VARCHAR2(4000);
    V_PAGE_NO   NUMBER;
    V_PAGE_SIZE NUMBER;
    V_OFFSET    NUMBER;
BEGIN
    V_KEYWORD := LOWER(TRIM(P_KEYWORD));
    IF V_KEYWORD IS NOT NULL THEN
        V_KEYWORD := REPLACE(V_KEYWORD, '\', '\\');
        V_KEYWORD := REPLACE(V_KEYWORD, '%', '\%');
        V_KEYWORD := REPLACE(V_KEYWORD, '_', '\_');
    END IF;

    V_PAGE_NO := NVL(P_PAGE_NO, 1);
    IF V_PAGE_NO < 1 THEN
        V_PAGE_NO := 1;
    END IF;

    V_PAGE_SIZE := NVL(P_PAGE_SIZE, C_DEFAULT_PAGE_SIZE);
    IF V_PAGE_SIZE < 1 THEN
        V_PAGE_SIZE := C_DEFAULT_PAGE_SIZE;
    ELSIF V_PAGE_SIZE > C_MAX_PAGE_SIZE THEN
        V_PAGE_SIZE := C_MAX_PAGE_SIZE;
    END IF;

    V_OFFSET := (V_PAGE_NO - 1) * V_PAGE_SIZE;

    SELECT COUNT(*)
      INTO O_TOTAL_ROWS
      FROM EDU_STUDENTS
     WHERE IS_DELETED = 0
       AND STATUS = 'ACTIVE'
       AND (V_KEYWORD IS NULL
            OR LOWER(STUDENT_CODE) LIKE '%' || V_KEYWORD || '%' ESCAPE '\'
            OR LOWER(FULL_NAME)    LIKE '%' || V_KEYWORD || '%' ESCAPE '\'
            OR LOWER(EMAIL)        LIKE '%' || V_KEYWORD || '%' ESCAPE '\'
            OR LOWER(PARENT_NAME)  LIKE '%' || V_KEYWORD || '%' ESCAPE '\'
            OR LOWER(PHONE)        LIKE '%' || V_KEYWORD || '%' ESCAPE '\'
            OR LOWER(ADDRESS)      LIKE '%' || V_KEYWORD || '%' ESCAPE '\');

    OPEN O_DATA_CURSOR FOR
        SELECT ID,
               STUDENT_CODE,
               FULL_NAME,
               EMAIL,
               STATUS,
               DATE_OF_BIRTH,
               PARENT_NAME,
               PHONE,
               ADDRESS,
               NOTE,
               CREATED_AT,
               UPDATED_AT
          FROM EDU_STUDENTS
         WHERE IS_DELETED = 0
           AND STATUS = 'ACTIVE'
           AND (V_KEYWORD IS NULL
                OR LOWER(STUDENT_CODE) LIKE '%' || V_KEYWORD || '%' ESCAPE '\'
                OR LOWER(FULL_NAME)    LIKE '%' || V_KEYWORD || '%' ESCAPE '\'
                OR LOWER(EMAIL)        LIKE '%' || V_KEYWORD || '%' ESCAPE '\'
                OR LOWER(PARENT_NAME)  LIKE '%' || V_KEYWORD || '%' ESCAPE '\'
                OR LOWER(PHONE)        LIKE '%' || V_KEYWORD || '%' ESCAPE '\'
                OR LOWER(ADDRESS)      LIKE '%' || V_KEYWORD || '%' ESCAPE '\')
         ORDER BY ID DESC
        OFFSET V_OFFSET ROWS FETCH NEXT V_PAGE_SIZE ROWS ONLY;

    O_ERR_CODE := '0';
    O_ERR_MSG  := 'SUCCESS';
EXCEPTION
    WHEN OTHERS THEN
        IF O_DATA_CURSOR IS NOT NULL AND O_DATA_CURSOR%ISOPEN THEN
            CLOSE O_DATA_CURSOR;
        END IF;
        O_TOTAL_ROWS := 0;
        O_ERR_CODE   := TO_CHAR(SQLCODE);
        O_ERR_MSG    := SUBSTR(SQLERRM, 1, 255);
END PRC_SEARCH_STUDENTS_PAGING;
/

PROMPT ============ V9.3 Kiem tra ============

DECLARE
    V_STATUS USER_OBJECTS.STATUS%TYPE;
BEGIN
    SELECT STATUS INTO V_STATUS FROM USER_OBJECTS
     WHERE OBJECT_TYPE = 'PROCEDURE' AND OBJECT_NAME = 'PRC_SEARCH_STUDENTS';
    DBMS_OUTPUT.PUT_LINE('PRC_SEARCH_STUDENTS = ' || V_STATUS);
    IF V_STATUS <> 'VALID' THEN
        RAISE_APPLICATION_ERROR(-20001, 'Procedure PRC_SEARCH_STUDENTS bien dich loi.');
    END IF;

    SELECT STATUS INTO V_STATUS FROM USER_OBJECTS
     WHERE OBJECT_TYPE = 'PROCEDURE' AND OBJECT_NAME = 'PRC_SEARCH_STUDENTS_PAGING';
    DBMS_OUTPUT.PUT_LINE('PRC_SEARCH_STUDENTS_PAGING = ' || V_STATUS);
    IF V_STATUS <> 'VALID' THEN
        RAISE_APPLICATION_ERROR(-20001, 'Procedure PRC_SEARCH_STUDENTS_PAGING bien dich loi.');
    END IF;
END;
/

PROMPT V9: chi liet ke hoc sinh STATUS = ACTIVE. P_STATUS giu tuong thich.
/
