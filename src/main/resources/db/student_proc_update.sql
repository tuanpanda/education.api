-- =============================================================================
-- EDUCATION - MODULE QUAN LY HOC SINH (MENU_STUDENT_LIST)
-- Standalone Procedure: PRC_SEARCH_STUDENTS_PAGING
--
-- Tim kiem hoc sinh theo tu khoa + trang thai, co phan trang bang OFFSET/FETCH NEXT.
-- Yeu cau: Oracle 12c tro len (da kiem chung tren Oracle 21c Enterprise 21.3.0.0.0).
--
-- Cach chay:  sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @student_proc_update.sql
-- =============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON

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
    -- Gioi han an toan de client khong the yeu cau mot trang qua lon
    C_DEFAULT_PAGE_SIZE CONSTANT NUMBER := 20;
    C_MAX_PAGE_SIZE     CONSTANT NUMBER := 200;

    V_KEYWORD   VARCHAR2(4000);
    V_STATUS    VARCHAR2(20);
    V_PAGE_NO   NUMBER;
    V_PAGE_SIZE NUMBER;
    V_OFFSET    NUMBER;
BEGIN
    -- ---------------------------------------------------------------------
    -- 1. Chuan hoa tham so dau vao
    -- ---------------------------------------------------------------------
    V_KEYWORD := LOWER(TRIM(P_KEYWORD));

    -- Escape ky tu wildcard cua LIKE ('%', '_') va chinh ky tu escape ('\'),
    -- de tu khoa nhu '100%' hoac 'A_B' duoc tim dung nghia van ban.
    IF V_KEYWORD IS NOT NULL THEN
        V_KEYWORD := REPLACE(V_KEYWORD, '\', '\\');
        V_KEYWORD := REPLACE(V_KEYWORD, '%', '\%');
        V_KEYWORD := REPLACE(V_KEYWORD, '_', '\_');
    END IF;

    V_STATUS := UPPER(TRIM(P_STATUS));

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

    -- ---------------------------------------------------------------------
    -- 2. Dem tong so dong khop dieu kien (phuc vu tinh totalPages)
    -- ---------------------------------------------------------------------
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

    -- ---------------------------------------------------------------------
    -- 3. Lay du lieu cua trang hien tai
    -- ---------------------------------------------------------------------
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
        -- Dong cursor neu da mo do dang, tranh ro ri cursor phia server
        IF O_DATA_CURSOR IS NOT NULL AND O_DATA_CURSOR%ISOPEN THEN
            CLOSE O_DATA_CURSOR;
        END IF;
        O_TOTAL_ROWS := 0;
        O_ERR_CODE   := TO_CHAR(SQLCODE);
        O_ERR_MSG    := SUBSTR(SQLERRM, 1, 255);
END PRC_SEARCH_STUDENTS_PAGING;
/

-- =============================================================================
-- Index ho tro tim kiem/sap xep tren cot IS_DELETED + STATUS
-- =============================================================================
DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT
      FROM USER_INDEXES
     WHERE INDEX_NAME = 'IDX_STUDENTS_DELETED_STATUS';

    IF V_COUNT = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_STUDENTS_DELETED_STATUS '
                       || 'ON EDU_STUDENTS (IS_DELETED, STATUS)';
        DBMS_OUTPUT.PUT_LINE('Da tao index IDX_STUDENTS_DELETED_STATUS');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Index IDX_STUDENTS_DELETED_STATUS da ton tai, bo qua');
    END IF;
END;
/

-- =============================================================================
-- Kiem tra ket qua bien dich
-- =============================================================================
DECLARE
    V_STATUS USER_OBJECTS.STATUS%TYPE;
BEGIN
    SELECT STATUS INTO V_STATUS
      FROM USER_OBJECTS
     WHERE OBJECT_TYPE = 'PROCEDURE'
       AND OBJECT_NAME = 'PRC_SEARCH_STUDENTS_PAGING';

    DBMS_OUTPUT.PUT_LINE('PRC_SEARCH_STUDENTS_PAGING = ' || V_STATUS);

    IF V_STATUS <> 'VALID' THEN
        RAISE_APPLICATION_ERROR(-20001, 'Procedure PRC_SEARCH_STUDENTS_PAGING bien dich loi.');
    END IF;
END;
/

COMMIT;
