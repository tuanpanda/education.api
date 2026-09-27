-- =============================================================================
-- EDUCATION - Trigger ID only (standalone)
--
-- TRG_EDU_STUDENTS_BI_ID cap ID tu SEQ_EDU_STUDENTS khi NULL.
-- STUDENT_CODE KHONG sinh o day; dung bang SYS_CODE_RULES + FN_NEXT_BIZ_CODE
-- (xem db/migration/V3__sys_code_rules.sql).
-- =============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT Dang chuyen huong sang V3 (SYS_CODE_RULES). Chay file migration V3:

@@migration/V3__sys_code_rules.sql
