-- =============================================================================
-- EDUCATION - MIGRATION V8: MAU LICH HOC CUA LOP
--
-- Cot EDU_CLASSES.CALENDAR_COLOR (#RRGGBB) dung tren lich hoc thang/tuan.
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @V8__class_calendar_color.sql
-- =============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V8 Cot CALENDAR_COLOR ============

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE EDU_CLASSES ADD CALENDAR_COLOR VARCHAR2(7)';
    DBMS_OUTPUT.PUT_LINE('EDU_CLASSES.CALENDAR_COLOR: added');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE IN (-1430, -01430) THEN
            DBMS_OUTPUT.PUT_LINE('EDU_CLASSES.CALENDAR_COLOR: already exists');
        ELSE
            RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE q'[
        ALTER TABLE EDU_CLASSES ADD CONSTRAINT CK_CLASSES_CALENDAR_COLOR
            CHECK (CALENDAR_COLOR IS NULL OR REGEXP_LIKE(CALENDAR_COLOR, '^#[0-9A-Fa-f]{6}$'))
    ]';
    DBMS_OUTPUT.PUT_LINE('CK_CLASSES_CALENDAR_COLOR: OK');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE IN (-2260, -2275, -02260, -02275) THEN
            DBMS_OUTPUT.PUT_LINE('CK_CLASSES_CALENDAR_COLOR: already exists');
        ELSE
            RAISE;
        END IF;
END;
/

PROMPT ============ V8 HOAN TAT ============

EXIT;
