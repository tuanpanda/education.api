-- =============================================================================
-- MIGRATION V23_8__backfill_legacy_fee_period: THU HOC PHI THEO KHOANG NGAY
--
-- DML: dien PERIOD_FROM = ngay 1, PERIOD_TO = ngay cuoi thang FEE_MONTH/FEE_YEAR cho phieu thang cu chua co PERIOD_FROM.
-- Phieu cu van giu FEE_MONTH/FEE_YEAR (van hien "Thang MM/yyyy").
-- File KHONG ket thuc giao dich va KHONG co EXIT. Nguoi chay kiem tra theo docs/RUN-V23-fee-range.md roi tu quyet dinh.
--
-- Spec: docs/SPEC-fee-date-range.md muc 4. Huong dan chay: docs/RUN-V23-fee-range.md
-- Moi file 1 khoi lenh. Chay lai an toan (idempotent).
--
-- THU TU CHAY: sau V23_7.
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe /nolog
--   CONNECT <DB_USER>@//localhost:1521/<SERVICE>   (mat khau nhap tai prompt)
--   @V23_8__backfill_legacy_fee_period.sql
--
-- ROLLBACK:
--   Chua ghi han: go lenh huy giao dich (xem RUN guide).
--   Da ghi han: UPDATE FIN_TUITION_FEES SET PERIOD_FROM = NULL, PERIOD_TO = NULL WHERE FEE_MONTH IS NOT NULL
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED

UPDATE FIN_TUITION_FEES f
   SET f.PERIOD_FROM = TO_DATE(TO_CHAR(f.FEE_YEAR, 'FM0000') || LPAD(TO_CHAR(f.FEE_MONTH), 2, '0') || '01', 'YYYYMMDD'),
       f.PERIOD_TO   = LAST_DAY(TO_DATE(TO_CHAR(f.FEE_YEAR, 'FM0000') || LPAD(TO_CHAR(f.FEE_MONTH), 2, '0') || '01', 'YYYYMMDD'))
 WHERE f.FEE_MONTH IS NOT NULL
   AND f.FEE_YEAR IS NOT NULL
   AND f.PERIOD_FROM IS NULL;
