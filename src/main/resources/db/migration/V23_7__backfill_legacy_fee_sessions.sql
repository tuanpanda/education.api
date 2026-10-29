-- =============================================================================
-- MIGRATION V23_7__backfill_legacy_fee_sessions: THU HOC PHI THEO KHOANG NGAY
--
-- DML: tao dong FIN_TUITION_FEE_SESSIONS cho phieu thang cu (IS_DELETED=0, STATUS<>'CANCELLED', co FEE_MONTH/FEE_YEAR)
-- tu diem danh PRESENT/LATE cua hoc sinh/lop/thang do. Bo qua buoi da co dong (chay lai an toan).
-- File KHONG ket thuc giao dich va KHONG co EXIT (EXIT cua sqlplus mac dinh se ghi han du lieu).
-- Nguoi chay kiem tra theo docs/RUN-V23-fee-range.md roi tu quyet dinh ghi han hay huy.
--
-- Spec: docs/SPEC-fee-date-range.md muc 4. Huong dan chay: docs/RUN-V23-fee-range.md
-- Moi file 1 khoi lenh. Chay lai an toan (idempotent).
--
-- THU TU CHAY: sau V23_1..V23_6.
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe /nolog
--   CONNECT <DB_USER>@//localhost:1521/<SERVICE>   (mat khau nhap tai prompt)
--   @V23_7__backfill_legacy_fee_sessions.sql
--
-- ROLLBACK:
--   Chua ghi han: go lenh huy giao dich (xem RUN guide).
--   Da ghi han: DELETE FROM FIN_TUITION_FEE_SESSIONS WHERE CREATED_BY = 'V23_7_BACKFILL'
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED

INSERT INTO FIN_TUITION_FEE_SESSIONS (ID, FEE_ID, ATTENDANCE_ID, SESSION_DATE, CREATED_AT, CREATED_BY)
SELECT SEQ_FIN_TUITION_FEE_SESSIONS.NEXTVAL,
       x.FEE_ID,
       x.ATTENDANCE_ID,
       x.SESSION_DATE,
       SYSTIMESTAMP,
       'V23_7_BACKFILL'
  FROM (
        SELECT f.ID                    AS FEE_ID,
               a.ID                    AS ATTENDANCE_ID,
               TRUNC(a.ATTENDANCE_DATE) AS SESSION_DATE
          FROM FIN_TUITION_FEES f
          JOIN EDU_ATTENDANCE a
            ON a.STUDENT_ID = f.STUDENT_ID
           AND a.CLASS_ID = f.CLASS_ID
         WHERE f.IS_DELETED = 0
           AND f.STATUS <> 'CANCELLED'
           AND f.FEE_MONTH IS NOT NULL
           AND f.FEE_YEAR IS NOT NULL
           AND a.IS_DELETED = 0
           AND a.STATUS IN ('PRESENT', 'LATE')
           AND a.ATTENDANCE_DATE >= TO_DATE(TO_CHAR(f.FEE_YEAR, 'FM0000') || LPAD(TO_CHAR(f.FEE_MONTH), 2, '0') || '01', 'YYYYMMDD')
           AND a.ATTENDANCE_DATE < ADD_MONTHS(TO_DATE(TO_CHAR(f.FEE_YEAR, 'FM0000') || LPAD(TO_CHAR(f.FEE_MONTH), 2, '0') || '01', 'YYYYMMDD'), 1)
           AND NOT EXISTS (
                   SELECT 1
                     FROM FIN_TUITION_FEE_SESSIONS s
                    WHERE s.ATTENDANCE_ID = a.ID
               )
       ) x;
