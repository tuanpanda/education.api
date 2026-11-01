# RUN V23: thu học phí theo khoảng ngày (V23_1 → V23_9)

Spec: `docs/SPEC-fee-date-range.md` v1.1 · Contract: fee-range-api v1.3.
Chạy tay bằng sqlplus (không có Flyway) trên **schema thật `EDUCATION`**. Không có môi trường PRE riêng, nên **bắt buộc export (Bước 0) trước khi chạy**.
Bot không chạy script này. Mật khẩu chỉ gõ tại prompt, không ghi vào file, lệnh, biến môi trường dùng chung hay log. **Không dùng SYS** (kể cả `AS SYSDBA`).

## Thứ tự và tính chất

| # | File | Loại | Ghi chú |
|---|------|------|---------|
| 0 | (Data Pump export) | Sao lưu | Bước 0 bên dưới. Bắt buộc. |
| 1 | `V23_1__fee_period_columns.sql` | DDL | Thêm `PERIOD_FROM`, `PERIOD_TO` (DATE) và `IS_RANGE NUMBER(1) DEFAULT 0 NOT NULL` + `CK_FEES_IS_RANGE CHECK (IS_RANGE IN (0,1))`. Phiếu hiện có tự nhận `IS_RANGE = 0`, không cần backfill. |
| 2 | `V23_2__fee_period_check.sql` | DDL | `CK_FEES_PERIOD` (PERIOD_FROM ≤ PERIOD_TO) |
| 3 | `V23_3__seq_fee_sessions.sql` | DDL | `SEQ_FIN_TUITION_FEE_SESSIONS` NOCACHE |
| 4 | `V23_4__fee_sessions_table.sql` | DDL | Bảng `FIN_TUITION_FEE_SESSIONS` + PK, 2 FK, `UQ_FEE_SESSIONS_ATTENDANCE`. Dùng tablespace mặc định của user (không ghi `TABLESPACE EDU`). |
| 5 | `V23_5__idx_fee_sessions_fee.sql` | DDL | `IDX_FEE_SESSIONS_FEE (FEE_ID, SESSION_DATE)` |
| 6 | `V23_6__idx_fees_period.sql` | DDL | `IDX_FEES_PERIOD (CLASS_ID, PERIOD_FROM, PERIOD_TO)` |
| 7 | `V23_9__rpt_class_collection_by_session.sql` | DDL (procedure báo cáo, tự commit) | `CREATE OR REPLACE PRC_RPT_CLASS_COLLECTION`: phiếu `IS_RANGE = 1` chia doanh thu theo tháng của `SESSION_DATE`. Công tắc 1 dòng `C_SPLIT_BY_SESSION`. **Chạy ngay sau V23_6, trước V23_7.** |
| 8 | `V23_7__backfill_legacy_fee_sessions.sql` | **DML, chạy tay** | Tạo dòng session cho phiếu tháng cũ còn hiệu lực. `SET EXITCOMMIT OFF`. **Không COMMIT, không EXIT trong file.** |
| 9 | `V23_8__backfill_legacy_fee_period.sql` | **DML, chạy tay** | Ghi `PERIOD_FROM/TO` = đầu/cuối tháng cho phiếu tháng cũ. `SET EXITCOMMIT OFF`. **Không COMMIT, không EXIT.** `IS_RANGE` giữ 0. **Sau khi COMMIT không có script rollback; chỉ khôi phục được từ bản export Bước 0.** |

**Thứ tự chạy bắt buộc:** V23_1 → V23_6 → **biên dịch lại INVALID (Bước 3b)** → **V23_9** → **biên dịch lại INVALID, phải 0 dòng (Bước 4b)** → V23_7 → V23_8 → chỉ SELECT kiểm tra → Tuấn gõ `COMMIT;` hoặc `ROLLBACK;`.

- DDL (V23_1–V23_6) và procedure V23_9 là DDL nên **tự commit**, kể cả commit luôn mọi DML đang mở. Vì vậy mọi DDL, kể cả V23_9, phải chạy **trước** V23_7.
- V23_7 và V23_8 là DML, chạy trong **cùng một giao dịch**.
  - Sau khi chạy hai file này chỉ được chạy **SELECT kiểm tra**.
  - **Không chạy DDL nào và không `EXIT`** trước khi Tuấn quyết định `COMMIT;` hoặc `ROLLBACK;`. Một câu DDL sẽ tự commit giao dịch đang mở.
- Hai file DML có `SET EXITCOMMIT OFF`: lỡ thoát sqlplus trước khi quyết định thì giao dịch bị **hủy** (không tự ghi hẳn). Đây chỉ là lưới an toàn, không phải cách làm.
- Mỗi file có `WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK`. Nếu V23_8 lỗi thì sqlplus thoát và rollback **cả V23_7 lẫn V23_8** chưa commit. Khi đó chạy lại từ Bước 5.
- Chạy lại an toàn: DDL báo `already exists`, V23_9 là `CREATE OR REPLACE`, còn V23_7 và V23_8 bỏ qua dòng đã có.

## Bước 0. Sao lưu bằng Data Pump (bắt buộc, trước V23_1)

- **Tài khoản:** dùng tài khoản DBA riêng (có quyền `DATAPUMP_EXP_FULL_DATABASE`) hoặc chính tài khoản `EDUCATION`. **Không dùng SYS.**
- **File dump:** nằm **ngoài repo**, trong thư mục của directory object trên máy DB (ví dụ `DATA_PUMP_DIR`). Không đặt dưới `E:\JAVA\...` và không commit vào git.
- **Mật khẩu:** không ghi `user/password` trên dòng lệnh. Chỉ ghi tên user; `expdp` sẽ hỏi mật khẩu tại prompt.

Xem đường dẫn của directory object (chạy trong sqlplus bằng tài khoản ở trên):

```sql
SELECT DIRECTORY_NAME, DIRECTORY_PATH FROM ALL_DIRECTORIES WHERE DIRECTORY_NAME = 'DATA_PUMP_DIR';
```

Chọn **một** trong hai cách dưới (cmd, chạy trên máy DB):

```bat
set NLS_LANG=AMERICAN_AMERICA.AL32UTF8

rem Cach A - chi bang FIN_TUITION_FEES (du de khoi phuc V23_8)
F:\Database\bin\expdp.exe <DB_USER>@//localhost:1521/<SERVICE> TABLES=EDUCATION.FIN_TUITION_FEES DIRECTORY=DATA_PUMP_DIR DUMPFILE=edu_fees_before_v23.dmp LOGFILE=edu_fees_before_v23.log

rem Cach B - ca schema EDUCATION (khuyen dung)
F:\Database\bin\expdp.exe <DB_USER>@//localhost:1521/<SERVICE> SCHEMAS=EDUCATION DIRECTORY=DATA_PUMP_DIR DUMPFILE=edu_schema_before_v23.dmp LOGFILE=edu_schema_before_v23.log
```

- Gõ mật khẩu khi được hỏi.
- Kiểm tra log báo `successfully completed` và số dòng `FIN_TUITION_FEES` trong log khớp với `SELECT COUNT(*) FROM FIN_TUITION_FEES;`.
- Ghi lại tên file dump và giờ export. Đây là điểm khôi phục duy nhất cho V23_8.

## Bước 1. Mở phiên (một phiên cho V23_1 → V23_6, V23_9, V23_7, V23_8)

```bat
cd /d E:\JAVA\edu-wt\fee-range\src\main\resources\db\migration
set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
F:\Database\bin\sqlplus.exe /nolog
```

```sql
CONNECT <DB_USER>@//localhost:1521/<SERVICE>
-- go mat khau tai prompt
SET LINESIZE 200
SET PAGESIZE 100
SHOW USER
SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') SCHEMA_NAME, SYS_CONTEXT('USERENV','SERVICE_NAME') SVC FROM DUAL;
```

`SCHEMA_NAME` phải là `EDUCATION`. Nếu đăng nhập bằng tài khoản DBA thì chạy thêm `ALTER SESSION SET CURRENT_SCHEMA = EDUCATION;` và kiểm tra lại. Các script không ghi tên schema.

## Bước 2. Kiểm tra trước (không ghi gì)

```sql
-- Kieu ID de khop FK cua V23_4 (mong doi NUMBER 19 cho ca hai)
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, DATA_PRECISION, DATA_SCALE
  FROM ALL_TAB_COLUMNS
 WHERE OWNER = 'EDUCATION'
   AND ((TABLE_NAME = 'FIN_TUITION_FEES' AND COLUMN_NAME IN ('ID','FEE_MONTH','FEE_YEAR','PERIOD_FROM','PERIOD_TO','IS_RANGE'))
     OR (TABLE_NAME = 'EDU_ATTENDANCE' AND COLUMN_NAME IN ('ID','ATTENDANCE_DATE','STATUS')))
 ORDER BY TABLE_NAME, COLUMN_NAME;

-- Chua co doi tuong V23 (lan chay dau: 0 dong)
SELECT OBJECT_TYPE, OBJECT_NAME FROM ALL_OBJECTS
 WHERE OWNER = 'EDUCATION'
   AND OBJECT_NAME IN ('FIN_TUITION_FEE_SESSIONS','SEQ_FIN_TUITION_FEE_SESSIONS','IDX_FEE_SESSIONS_FEE','IDX_FEES_PERIOD');

-- Tablespace mac dinh (bang moi se nam o day)
SELECT USERNAME, DEFAULT_TABLESPACE FROM ALL_USERS WHERE USERNAME = 'EDUCATION';

-- Phieu thang cu trung hoc sinh/lop/thang con hieu luc (phai 0 dong, neu khong V23_7 se loi ORA-00001 va dung)
SELECT STUDENT_ID, CLASS_ID, FEE_YEAR, FEE_MONTH, COUNT(*) CNT
  FROM FIN_TUITION_FEES
 WHERE IS_DELETED = 0 AND STATUS <> 'CANCELLED' AND FEE_MONTH IS NOT NULL AND FEE_YEAR IS NOT NULL
 GROUP BY STUDENT_ID, CLASS_ID, FEE_YEAR, FEE_MONTH
HAVING COUNT(*) > 1;

-- So lieu goc de doi chieu
SELECT COUNT(*) TOTAL_FEES,
       SUM(CASE WHEN FEE_MONTH IS NOT NULL AND FEE_YEAR IS NOT NULL THEN 1 ELSE 0 END) MONTHLY_FEES,
       SUM(CASE WHEN FEE_MONTH IS NOT NULL AND IS_DELETED = 0 AND STATUS <> 'CANCELLED' THEN 1 ELSE 0 END) ACTIVE_MONTHLY_FEES
  FROM FIN_TUITION_FEES;
```

## Bước 3. DDL V23_1 → V23_6

```sql
@V23_1__fee_period_columns.sql
@V23_2__fee_period_check.sql
@V23_3__seq_fee_sessions.sql
@V23_4__fee_sessions_table.sql
@V23_5__idx_fee_sessions_fee.sql
@V23_6__idx_fees_period.sql
```

Mỗi file in `created` hoặc `already exists`. Kiểm tra:

```sql
SELECT COLUMN_NAME, DATA_TYPE, NULLABLE, DATA_DEFAULT FROM USER_TAB_COLUMNS
 WHERE TABLE_NAME = 'FIN_TUITION_FEES' AND COLUMN_NAME IN ('PERIOD_FROM','PERIOD_TO','IS_RANGE');

SELECT IS_RANGE, COUNT(*) FROM FIN_TUITION_FEES GROUP BY IS_RANGE;
-- mong doi: chi IS_RANGE = 0

SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE, TABLE_NAME, STATUS FROM USER_CONSTRAINTS
 WHERE CONSTRAINT_NAME IN ('CK_FEES_IS_RANGE','CK_FEES_PERIOD','PK_FIN_TUITION_FEE_SESSIONS',
                           'FK_FEE_SESSIONS_FEE','FK_FEE_SESSIONS_ATT','UQ_FEE_SESSIONS_ATTENDANCE')
 ORDER BY CONSTRAINT_NAME;
-- mong doi: 6 dong, STATUS = ENABLED

SELECT INDEX_NAME, TABLE_NAME, UNIQUENESS, STATUS FROM USER_INDEXES
 WHERE INDEX_NAME IN ('IDX_FEE_SESSIONS_FEE','IDX_FEES_PERIOD','UQ_FEE_SESSIONS_ATTENDANCE','PK_FIN_TUITION_FEE_SESSIONS');
-- mong doi: 4 dong, STATUS = VALID

SELECT SEQUENCE_NAME, CACHE_SIZE, LAST_NUMBER FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_FIN_TUITION_FEE_SESSIONS';
```

## Bước 3b. Biên dịch lại object bị INVALID (bắt buộc)

ALTER TABLE ở V23_1/V23_2 làm các object phụ thuộc `FIN_TUITION_FEES` bị INVALID. Lệnh biên dịch lại là DDL, nên chỉ chạy khi **không** có giao dịch V23_7/V23_8 đang mở. Ở bước này `PRC_RPT_CLASS_COLLECTION` có thể vẫn INVALID vì chưa chạy V23_9; điều đó chấp nhận được, mọi object khác phải VALID.

Cách 1, biên dịch các object đang INVALID của schema:

```sql
EXEC DBMS_UTILITY.COMPILE_SCHEMA(schema => 'EDUCATION', compile_all => FALSE);
```

Cách 2, biên dịch từng object phụ thuộc đã biết:

```sql
ALTER PROCEDURE PRC_GET_TUITION_SLIP_DATA COMPILE;
SHOW ERRORS PROCEDURE PRC_GET_TUITION_SLIP_DATA
ALTER PROCEDURE PRC_GET_TUITION_FEE_DETAIL COMPILE;
SHOW ERRORS PROCEDURE PRC_GET_TUITION_FEE_DETAIL
ALTER PROCEDURE PRC_RPT_DASHBOARD_METRICS COMPILE;
SHOW ERRORS PROCEDURE PRC_RPT_DASHBOARD_METRICS
ALTER PROCEDURE PRC_RPT_FINANCE_SUMMARY COMPILE;
SHOW ERRORS PROCEDURE PRC_RPT_FINANCE_SUMMARY
ALTER PROCEDURE PRC_RPT_DEBT_AGING COMPILE;
SHOW ERRORS PROCEDURE PRC_RPT_DEBT_AGING
ALTER PROCEDURE PRC_RPT_STUDENT_LEDGER COMPILE;
SHOW ERRORS PROCEDURE PRC_RPT_STUDENT_LEDGER
```

Mỗi `SHOW ERRORS` phải in `No errors.`

**Tiêu chí đạt** (bắt buộc trước khi sang bước tiếp theo; ở Bước 3b chỉ được còn `PRC_RPT_CLASS_COLLECTION`):

```sql
SELECT OBJECT_NAME, OBJECT_TYPE FROM USER_OBJECTS WHERE STATUS = 'INVALID';
-- phai tra 0 dong ("no rows selected")
```

Nếu còn dòng nào:

1. Chạy `ALTER <TYPE> <NAME> COMPILE;` rồi `SHOW ERRORS <TYPE> <NAME>` cho từng object.
2. Ghi lại lỗi và dừng lại.

Không chạy V23_7/V23_8 khi còn object INVALID.

## Bước 4. V23_9 (procedure báo cáo thu tiền theo lớp, DDL, chạy trước V23_7)

```sql
@V23_9__rpt_class_collection_by_session.sql
-- "Procedure created." + "No errors."

SELECT OBJECT_NAME, OBJECT_TYPE, STATUS, LAST_DDL_TIME FROM USER_OBJECTS
 WHERE OBJECT_NAME IN ('PRC_RPT_CLASS_COLLECTION','PRC_GET_TUITION_SLIP_DATA');
-- mong doi STATUS = VALID

SELECT OBJECT_NAME, OBJECT_TYPE FROM USER_OBJECTS WHERE STATUS = 'INVALID';
-- khong co doi tuong moi bi INVALID
```

Muốn đổi cách tính doanh thu phiếu khoảng ngày: sửa dòng `C_SPLIT_BY_SESSION CONSTANT NUMBER := 1;` (0 = tính trọn vào tháng của `PERIOD_FROM`) rồi chạy lại file.
**Chỉ chạy lại V23_9 khi không có giao dịch V23_7/V23_8 đang mở**: hoặc chạy trước Bước 5, hoặc sau khi đã COMMIT/ROLLBACK ở Bước 7.

## Bước 4b. Biên dịch lại object bị INVALID (bắt buộc)

Chạy lại sau V23_9 để chắc chắn `PRC_RPT_CLASS_COLLECTION` và các object phụ thuộc đều VALID. ALTER TABLE ở V23_1/V23_2 làm các object phụ thuộc `FIN_TUITION_FEES` bị INVALID. Lệnh biên dịch lại là DDL, nên chỉ chạy khi **không** có giao dịch V23_7/V23_8 đang mở.

Cách 1, biên dịch các object đang INVALID của schema:

```sql
EXEC DBMS_UTILITY.COMPILE_SCHEMA(schema => 'EDUCATION', compile_all => FALSE);
```

Cách 2, biên dịch từng object phụ thuộc đã biết:

```sql
ALTER PROCEDURE PRC_GET_TUITION_SLIP_DATA COMPILE;
SHOW ERRORS PROCEDURE PRC_GET_TUITION_SLIP_DATA
ALTER PROCEDURE PRC_GET_TUITION_FEE_DETAIL COMPILE;
SHOW ERRORS PROCEDURE PRC_GET_TUITION_FEE_DETAIL
ALTER PROCEDURE PRC_RPT_DASHBOARD_METRICS COMPILE;
SHOW ERRORS PROCEDURE PRC_RPT_DASHBOARD_METRICS
ALTER PROCEDURE PRC_RPT_FINANCE_SUMMARY COMPILE;
SHOW ERRORS PROCEDURE PRC_RPT_FINANCE_SUMMARY
ALTER PROCEDURE PRC_RPT_DEBT_AGING COMPILE;
SHOW ERRORS PROCEDURE PRC_RPT_DEBT_AGING
ALTER PROCEDURE PRC_RPT_STUDENT_LEDGER COMPILE;
SHOW ERRORS PROCEDURE PRC_RPT_STUDENT_LEDGER
```

Mỗi `SHOW ERRORS` phải in `No errors.`

**Tiêu chí đạt** (bắt buộc trước Bước 5, V23_7):

```sql
SELECT OBJECT_NAME, OBJECT_TYPE FROM USER_OBJECTS WHERE STATUS = 'INVALID';
-- phai tra 0 dong ("no rows selected")
```

Nếu còn dòng nào:

1. Chạy `ALTER <TYPE> <NAME> COMPILE;` rồi `SHOW ERRORS <TYPE> <NAME>` cho từng object.
2. Ghi lại lỗi và dừng lại.

Không chạy V23_7/V23_8 khi còn object INVALID.

### Chạy lại riêng V23_9 (ví dụ sau lỗi biên dịch)

V23_9 là `CREATE OR REPLACE`, nên chạy lại riêng file này an toàn.

- **Sự cố 29/10/2026:** alias `AS SHARE` (SHARE là từ dành riêng của Oracle) gây ORA-00923 và procedure bị INVALID. Đã sửa thành `FEE_SHARE`.
- **Điều kiện chạy lại:** chỉ khi **không** có giao dịch V23_7/V23_8 đang mở, vì V23_9 là DDL và sẽ tự commit. Nghĩa là chạy trước Bước 5, hoặc sau khi đã COMMIT/ROLLBACK ở Bước 7.
- **Không chạy lại** V23_1..V23_8.

```sql
@V23_9__rpt_class_collection_by_session.sql
SHOW ERRORS PROCEDURE PRC_RPT_CLASS_COLLECTION
-- mong doi: "No errors."

SELECT STATUS FROM USER_OBJECTS WHERE OBJECT_NAME = 'PRC_RPT_CLASS_COLLECTION';
-- mong doi: VALID
```

Sau khi chạy lại V23_9, chạy lại **Bước 4b** (biên dịch lại và kiểm tra 0 object INVALID).

## Bước 5. V23_7 (DML chạy tay): backfill dòng session cho phiếu tháng cũ

**Trước khi chạy:**

```sql
SELECT COUNT(*) SESSION_ROWS_BEFORE FROM FIN_TUITION_FEE_SESSIONS;
-- lan dau: 0

-- So dong du kien se tao (cung dieu kien voi V23_7)
SELECT COUNT(*) EXPECTED_ROWS
  FROM FIN_TUITION_FEES f
  JOIN EDU_ATTENDANCE a ON a.STUDENT_ID = f.STUDENT_ID AND a.CLASS_ID = f.CLASS_ID
 WHERE f.IS_DELETED = 0 AND f.STATUS <> 'CANCELLED' AND f.FEE_MONTH IS NOT NULL AND f.FEE_YEAR IS NOT NULL
   AND a.IS_DELETED = 0 AND a.STATUS IN ('PRESENT','LATE')
   AND a.ATTENDANCE_DATE >= TO_DATE(TO_CHAR(f.FEE_YEAR,'FM0000') || LPAD(TO_CHAR(f.FEE_MONTH),2,'0') || '01','YYYYMMDD')
   AND a.ATTENDANCE_DATE <  ADD_MONTHS(TO_DATE(TO_CHAR(f.FEE_YEAR,'FM0000') || LPAD(TO_CHAR(f.FEE_MONTH),2,'0') || '01','YYYYMMDD'), 1)
   AND NOT EXISTS (SELECT 1 FROM FIN_TUITION_FEE_SESSIONS s WHERE s.ATTENDANCE_ID = a.ID);
```

**Chạy:**

```sql
@V23_7__backfill_legacy_fee_sessions.sql
-- sqlplus in "N rows created." -> N phai bang EXPECTED_ROWS
```

**Sau khi chạy (chỉ SELECT, chưa COMMIT):**

```sql
SELECT COUNT(*) SESSION_ROWS_AFTER, COUNT(DISTINCT FEE_ID) FEES FROM FIN_TUITION_FEE_SESSIONS;
SELECT COUNT(*) BACKFILLED FROM FIN_TUITION_FEE_SESSIONS WHERE CREATED_BY = 'V23_7_BACKFILL';

-- So so dong session voi TOTAL_SESSIONS tung phieu; liet ke phieu lech
SELECT f.ID, f.FEE_CODE, f.STUDENT_ID, f.CLASS_ID, f.FEE_YEAR, f.FEE_MONTH, f.STATUS,
       f.TOTAL_SESSIONS, NVL(s.CNT, 0) SESSION_ROWS, NVL(s.CNT, 0) - NVL(f.TOTAL_SESSIONS, 0) DIFF
  FROM FIN_TUITION_FEES f
  LEFT JOIN (SELECT FEE_ID, COUNT(*) CNT FROM FIN_TUITION_FEE_SESSIONS GROUP BY FEE_ID) s ON s.FEE_ID = f.ID
 WHERE f.IS_DELETED = 0 AND f.STATUS <> 'CANCELLED' AND f.FEE_MONTH IS NOT NULL AND f.FEE_YEAR IS NOT NULL
   AND NVL(s.CNT, 0) <> NVL(f.TOTAL_SESSIONS, 0)
 ORDER BY f.FEE_YEAR, f.FEE_MONTH, f.CLASS_ID, f.STUDENT_ID;

-- Tom tat khop / lech
SELECT CASE WHEN NVL(s.CNT, 0) = NVL(f.TOTAL_SESSIONS, 0) THEN 'MATCH' ELSE 'MISMATCH' END KQ, COUNT(*) FEES
  FROM FIN_TUITION_FEES f
  LEFT JOIN (SELECT FEE_ID, COUNT(*) CNT FROM FIN_TUITION_FEE_SESSIONS GROUP BY FEE_ID) s ON s.FEE_ID = f.ID
 WHERE f.IS_DELETED = 0 AND f.STATUS <> 'CANCELLED' AND f.FEE_MONTH IS NOT NULL AND f.FEE_YEAR IS NOT NULL
 GROUP BY CASE WHEN NVL(s.CNT, 0) = NVL(f.TOTAL_SESSIONS, 0) THEN 'MATCH' ELSE 'MISMATCH' END;
```

- Phiếu lệch có thể xảy ra, ví dụ điểm danh bị sửa sau khi lập phiếu hoặc phiếu đã sửa tay.
- Phiếu lệch vẫn giữ số tiền cũ. Dòng session chỉ dùng để chặn thu trùng và in ngày.

**Chưa COMMIT/ROLLBACK ở bước này.** Chuyển sang Bước 6 (V23_8) trong cùng phiên. Không chạy DDL, không `EXIT`.

## Bước 6. V23_8 (DML chạy tay): PERIOD_* cho phiếu tháng cũ

**Trước khi chạy:**

```sql
SELECT COUNT(*) LEGACY_NO_PERIOD_BEFORE FROM FIN_TUITION_FEES
 WHERE FEE_MONTH IS NOT NULL AND FEE_YEAR IS NOT NULL AND PERIOD_FROM IS NULL;
-- = so dong V23_8 se cap nhat

SELECT COUNT(*) WITH_PERIOD_BEFORE FROM FIN_TUITION_FEES WHERE PERIOD_FROM IS NOT NULL;
-- lan dau: 0
```

**Chạy:**

```sql
@V23_8__backfill_legacy_fee_period.sql
-- "N rows updated." -> N phai bang LEGACY_NO_PERIOD_BEFORE
```

**Sau khi chạy (chỉ SELECT, chưa COMMIT):**

```sql
SELECT COUNT(*) LEGACY_NO_PERIOD_AFTER FROM FIN_TUITION_FEES
 WHERE FEE_MONTH IS NOT NULL AND FEE_YEAR IS NOT NULL AND PERIOD_FROM IS NULL;
-- mong doi 0

SELECT COUNT(*) WITH_PERIOD_AFTER FROM FIN_TUITION_FEES WHERE PERIOD_FROM IS NOT NULL;

SELECT COUNT(*) BAD_PERIOD FROM FIN_TUITION_FEES
 WHERE FEE_MONTH IS NOT NULL
   AND (EXTRACT(MONTH FROM PERIOD_FROM) <> FEE_MONTH OR PERIOD_TO <> LAST_DAY(PERIOD_FROM));
-- mong doi 0

SELECT IS_RANGE, COUNT(*) FROM FIN_TUITION_FEES GROUP BY IS_RANGE;
-- van chi IS_RANGE = 0
```

## Bước 7. Tuấn quyết định COMMIT hoặc ROLLBACK (V23_7 + V23_8)

Từ lúc chạy V23_7 đến đây chỉ được chạy SELECT. **Không DDL, không EXIT.**
Xem lại số dòng trước/sau của Bước 5 và Bước 6, rồi gõ **một** trong hai:

```sql
COMMIT;
```

```sql
ROLLBACK;
```

> **Lưu ý:** sau khi đã COMMIT, các UPDATE của V23_8 **không có script rollback**. Muốn trả lại dữ liệu cũ thì phải khôi phục từ bản export ở Bước 0 (xem mục Rollback). V23_7 thì gỡ được bằng `DELETE ... WHERE CREATED_BY = 'V23_7_BACKFILL'`.

## Bước 8. Thoát

Chỉ thoát sau khi đã gõ COMMIT hoặc ROLLBACK ở Bước 7:

```sql
EXIT
```

## Rollback (theo thứ tự ngược)

Ngược với thứ tự chạy (V23_8 → V23_7 → V23_9 → V23_6 … V23_1):

```sql
-- 1) V23_8 (da COMMIT): KHONG co script rollback -> khoi phuc tu ban export Buoc 0 (ben duoi)
-- 2) V23_7 (da COMMIT):
DELETE FROM FIN_TUITION_FEE_SESSIONS WHERE CREATED_BY = 'V23_7_BACKFILL';
-- kiem tra SELECT COUNT(*) roi Tuan tu go COMMIT; hoac ROLLBACK; TRUOC khi chay bat ky DDL nao ben duoi
COMMIT;
-- 3) V23_9: chay lai khoi PRC_RPT_CLASS_COLLECTION trong V14_3__fin_reports.sql (procedure bao cao cu)
-- 4) V23_6..V23_1:
DROP INDEX IDX_FEES_PERIOD;
DROP INDEX IDX_FEE_SESSIONS_FEE;
DROP TABLE FIN_TUITION_FEE_SESSIONS;
DROP SEQUENCE SEQ_FIN_TUITION_FEE_SESSIONS;
ALTER TABLE FIN_TUITION_FEES DROP CONSTRAINT CK_FEES_PERIOD;
ALTER TABLE FIN_TUITION_FEES DROP (PERIOD_FROM, PERIOD_TO, IS_RANGE);
```

Nếu V23_7/V23_8 **chưa COMMIT** thì chỉ cần gõ `ROLLBACK;` ở Bước 7, không cần bước 1–2 ở trên.

**Khôi phục từ bản export (Bước 0):**

- **Cách an toàn:** import bảng ra một bảng phụ để đối chiếu và sửa dữ liệu. Cách này không đụng các FK đang trỏ vào `FIN_TUITION_FEES`.

  ```bat
  F:\Database\bin\impdp.exe <DB_USER>@//localhost:1521/<SERVICE> DIRECTORY=DATA_PUMP_DIR DUMPFILE=<file_buoc_0>.dmp TABLES=EDUCATION.FIN_TUITION_FEES REMAP_TABLE=EDUCATION.FIN_TUITION_FEES:FIN_TUITION_FEES_BK_V23 LOGFILE=imp_fees_bk_v23.log
  ```

  Sau đó Tuấn so `FIN_TUITION_FEES` với `FIN_TUITION_FEES_BK_V23` theo `ID` rồi tự quyết câu sửa và COMMIT.
- **Khôi phục toàn bộ** (`TABLE_EXISTS_ACTION=REPLACE` hoặc import lại cả schema): chỉ làm khi thật cần và có DBA đi cùng.
  - REPLACE drop rồi tạo lại bảng, nên mất mọi thay đổi sau giờ export.
  - Cần kiểm tra lại FK của bảng con, ví dụ `FIN_PAYMENT_TRANSACTIONS`.

**Lưu ý chung:**

- Rollback DDL làm mất các phiếu khoảng ngày (`IS_RANGE = 1`) đã tạo. Trước khi rollback phải hủy/xóa các phiếu này, hoặc rollback cả backend.
- Backend mới cần đủ V23_1..V23_6 (cột `IS_RANGE`, `PERIOD_*` và bảng session). Không deploy backend mới khi DB chưa chạy V23.

## Kiểm thử sau khi chạy (chỉ trên lớp test riêng)

Chỉ test trên **một lớp test riêng**, có học sinh test và điểm danh test. **Không tạo phiếu thử trên lớp thật.**

1. Lớp test → Xem trước → Tạo phiếu → in phiếu, đối chiếu ngày với điểm danh.

   ```sql
   SELECT ID, FEE_CODE, CLASS_ID, IS_RANGE, FEE_MONTH, PERIOD_FROM, PERIOD_TO, DISCOUNT_AMOUNT, STATUS
     FROM FIN_TUITION_FEES WHERE IS_RANGE = 1 ORDER BY ID DESC;
   -- mong doi: chi CLASS_ID cua lop test; IS_RANGE = 1, FEE_MONTH NULL, DISCOUNT_AMOUNT = 0
   ```

2. Tạo lại với khoảng chồng lấn → 409 `FEE_SESSION_CONFLICT`, có ngày và mã phiếu.
3. Hủy phiếu → `SELECT COUNT(*) FROM FIN_TUITION_FEE_SESSIONS WHERE FEE_ID = <id>;` = 0 → tạo lại được.
4. Kiểm tra nội dung VietQR `<mã HS> HP ddMM-ddMM`. Không thanh toán thật.
5. Xong thì hủy/xóa toàn bộ phiếu test của lớp test, rồi kiểm tra: `SELECT COUNT(*) FROM FIN_TUITION_FEES WHERE IS_RANGE = 1 AND IS_DELETED = 0 AND STATUS <> 'CANCELLED';`

## Rủi ro đã biết

- **Từ dành riêng Oracle:** V23_9 từng dùng alias `SHARE` (ORA-00923). `V23FeeRangeScriptTest` giờ chặn mọi alias/tên trong V23_1..V23_9 trùng danh sách từ dành riêng (`V$RESERVED_WORDS`, `RESERVED = 'Y'`). Unit test không biên dịch PL/SQL thật, nên vẫn phải kiểm `SHOW ERRORS` và `STATUS` trên DB.

(Quy tắc sửa phiếu: phiếu `IS_RANGE = 1` luôn có giảm giá = 0. Phiếu tháng cũ / khoản tạo tay mà PUT không gửi `discountAmount` thì **giữ** giảm giá hiện có, xem SPEC v1.1 §0 V1. Đây không còn là rủi ro.)

- **Không có audit** cho sinh phiếu theo khoảng ngày: generate cũ không có audit và `TuitionSlipService` không thuộc `AuditTrailAspect`. Hiện chỉ có log ứng dụng (lớp, khoảng ngày, số phiếu, người tạo).
- **Đối soát VietQR làm tay**, khớp theo nội dung + số tiền. Mã học sinh dài hơn 12 ký tự sẽ bị cắt trong nội dung, và nếu có nhiều giao dịch khớp thì phải đối soát tay. Backend không có đối soát tự động.
- **Xung đột tính theo ID điểm danh.** Một buổi điểm danh thêm/sửa sau, rơi vào khoảng ngày của phiếu cũ, không bị coi là đã thu, nên có thể được tính vào phiếu mới.
