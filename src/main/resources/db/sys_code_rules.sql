-- =============================================================================
-- EDUCATION - QUY LUAT SINH MA (standalone, dong bo V3)
--
-- Trigger ID chi cap SEQ_EDU_STUDENTS.
-- STUDENT_CODE sinh theo bang SYS_CODE_RULES (RULE_CODE = STUDENT).
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @sys_code_rules.sql
-- =============================================================================

@@migration/V3__sys_code_rules.sql
