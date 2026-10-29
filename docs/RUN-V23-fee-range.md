# RUN V23: thu học phí theo khoảng ngày (V23_1 → V23_9)

Spec: `docs/SPEC-fee-date-range.md` v1.1 · Contract: fee-range-api v1.3.
Chạy tay bằng sqlplus (không có Flyway). **Chạy trên EDUCATION_PRE trước**, kiểm tra xong và được duyệt mới chạy trên EDUCATION.
Bot không chạy script này. Mật khẩu chỉ gõ tại prompt `CONNECT`, không ghi vào file, lệnh hay log. Không dùng SYS.

## 0. Thứ tự và tính chất

| # | File | Loại | Ghi chú |
|---|------|------|---------|
| 1 | `V23_1__fee_period_columns.sql` | DDL | Thêm `PERIOD_FROM`, `PERIOD_TO` (DATE) và `IS_RANGE NUMBER(1) DEFAULT 0 NOT NULL` + `CK_FEES_IS_RANGE CHECK (IS_RANGE IN (0,1))`. Phiếu cũ / khoản tạo tay tự nhận 0, không cần backfill. |
| 2 | `V23_2__fee_period_check.sql` | DDL | `CK_FEES_PERIOD` (PERIOD_FROM ≤ PERIOD_TO) |
| 3 | `V23_3__seq_fee_sessions.sql` | DDL | `SEQ_FIN_TUITION_FEE_SESSIONS` NOCACHE |
| 4 | `V23_4__fee_sessions_table.sql` | DDL | Bảng `FIN_TUITION_FEE_SESSIONS` + PK, 2 FK, `UQ_FEE_SESSIONS_ATTENDANCE`. Dùng tablespace mặc định của user (không ghi `TABLESPACE EDU`). |
| 5 | `V23_5__idx_fee_sessions_fee.sql` | DDL | `IDX_FEE_SESSIONS_FEE (FEE_ID, SESSION_DATE)` |
| 6 | `V23_6__idx_fees_period.sql` | DDL | `IDX_FEES_PERIOD (CLASS_ID, PERIOD_FROM, PERIOD_TO)` |
| 7 | `V23_7__backfill_legacy_fee_sessions.sql` | **DML** | Tạo dòng session cho phiếu tháng cũ còn hiệu lực. **Không COMMIT, không EXIT trong file.** |
| 8 | `V23_8__backfill_legacy_fee_period.sql` | **DML** | Ghi `PERIOD_FROM/TO` = đầu/cuối tháng cho phiếu tháng cũ. **Không COMMIT, không EXIT.** `IS_RANGE` giữ 0. |
| 9 | `V23_9__rpt_class_collection_by_session.sql` | PL/SQL | `CREATE OR REPLACE PRC_RPT_CLASS_COLLECTION`: phiếu `IS_RANGE = 1` chia doanh thu theo tháng của `SESSION_DATE`. Công tắc 1 dòng `C_SPLIT_BY_SESSION`. |

DDL (1–6, 9) tự commit; chạy lại an toàn (báo `already exists`). DML (7, 8) cần **bạn tự gõ `COMMIT;` hoặc `ROLLBACK;`** sau khi kiểm tra. Lưu ý: thoát sqlplus bằng `EXIT` sẽ **ghi hẳn** giao dịch đang mở, nên quyết định COMMIT/ROLLBACK trước khi thoát.

Mỗi file có `WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK`: gặp lỗi sqlplus thoát và rollback phần DML chưa commit.

## 1. Mở phiên (một phiên cho cả 9 file)

```bat
cd /d E:\JAVA\edu-wt\fee-range\src\main\resources\db\migration
set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
F:\Database\bin\sqlplus.exe /nolog
```

```sql
CONNECT <DB_USER>@//localhost:1521/<SERVICE_PRE>
-- gõ mật khẩu tại prompt
SET LINESIZE 200
SET PAGESIZE 100
SHOW USER
SELECT SYS_CONTEXT('USERENV','DB_NAME') DB, SYS_CONTEXT('USERENV','SERVICE_NAME') SVC FROM DUAL;
```

Kiểm tra đúng user ứng dụng và đúng service PRE trước khi chạy tiếp.

## 2. Kiểm tra trước (không ghi gì)

```sql
-- Kiểu ID để khớp FK của V23_4 (mong đợi NUMBER 19 cho cả hai)
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, DATA_PRECISION, DATA_SCALE
  FROM USER_TAB_COLUMNS
 WHERE (TABLE_NAME = 'FIN_TUITION_FEES' AND COLUMN_NAME IN ('ID','FEE_MONTH','FEE_YEAR','PERIOD_FROM','PERIOD_TO','IS_RANGE'))
    OR (TABLE_NAME = 'EDU_ATTENDANCE' AND COLUMN_NAME IN ('ID','ATTENDANCE_DATE','STATUS'))
 ORDER BY TABLE_NAME, COLUMN_NAME;

-- Chưa có đối tượng V23 (lần chạy đầu: 0 dòng)
SELECT OBJECT_TYPE, OBJECT_NAME FROM USER_OBJECTS
 WHERE OBJECT_NAME IN ('FIN_TUITION_FEE_SESSIONS','SEQ_FIN_TUITION_FEE_SESSIONS','IDX_FEE_SESSIONS_FEE','IDX_FEES_PERIOD');

-- Tablespace mặc định của user (bảng mới sẽ nằm ở đây)
SELECT DEFAULT_TABLESPACE FROM USER_USERS;

-- Phiếu tháng cũ trùng học sinh/lớp/tháng còn hiệu lực (phải 0 dòng, nếu không V23_7 sẽ lỗi ORA-00001 và dừng)
SELECT STUDENT_ID, CLASS_ID, FEE_YEAR, FEE_MONTH, COUNT(*) CNT
  FROM FIN_TUITION_FEES
 WHERE IS_DELETED = 0 AND STATUS <> 'CANCELLED' AND FEE_MONTH IS NOT NULL AND FEE_YEAR IS NOT NULL
 GROUP BY STUDENT_ID, CLASS_ID, FEE_YEAR, FEE_MONTH
HAVING COUNT(*) > 1;
```

## 3. DDL V23_1 → V23_6

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
-- mong đợi: chỉ IS_RANGE = 0 (toàn bộ phiếu hiện có)

SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE, TABLE_NAME, STATUS FROM USER_CONSTRAINTS
 WHERE CONSTRAINT_NAME IN ('CK_FEES_IS_RANGE','CK_FEES_PERIOD','PK_FIN_TUITION_FEE_SESSIONS',
                           'FK_FEE_SESSIONS_FEE','FK_FEE_SESSIONS_ATT','UQ_FEE_SESSIONS_ATTENDANCE')
 ORDER BY CONSTRAINT_NAME;
-- mong đợi: 6 dòng, STATUS = ENABLED

SELECT INDEX_NAME, TABLE_NAME, UNIQUENESS, STATUS FROM USER_INDEXES
 WHERE INDEX_NAME IN ('IDX_FEE_SESSIONS_FEE','IDX_FEES_PERIOD','UQ_FEE_SESSIONS_ATTENDANCE','PK_FIN_TUITION_FEE_SESSIONS');
-- mong đợi: 4 dòng, STATUS = VALID

SELECT SEQUENCE_NAME, CACHE_SIZE, LAST_NUMBER FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_FIN_TUITION_FEE_SESSIONS';
```

## 4. DML V23_7 (backfill dòng session cho phiếu tháng cũ)

```sql
@V23_7__backfill_legacy_fee_sessions.sql
-- sqlplus in "N rows created."
```

Kiểm tra trước khi COMMIT:

```sql
-- Tổng số dòng vừa tạo
SELECT COUNT(*) BACKFILLED, COUNT(DISTINCT FEE_ID) FEES FROM FIN_TUITION_FEE_SESSIONS WHERE CREATED_BY = 'V23_7_BACKFILL';

-- So số dòng session với TOTAL_SESSIONS từng phiếu; liệt kê phiếu lệch
SELECT f.ID, f.FEE_CODE, f.STUDENT_ID, f.CLASS_ID, f.FEE_YEAR, f.FEE_MONTH, f.STATUS,
       f.TOTAL_SESSIONS, NVL(s.CNT, 0) SESSION_ROWS, NVL(s.CNT, 0) - NVL(f.TOTAL_SESSIONS, 0) DIFF
  FROM FIN_TUITION_FEES f
  LEFT JOIN (SELECT FEE_ID, COUNT(*) CNT FROM FIN_TUITION_FEE_SESSIONS GROUP BY FEE_ID) s ON s.FEE_ID = f.ID
 WHERE f.IS_DELETED = 0 AND f.STATUS <> 'CANCELLED' AND f.FEE_MONTH IS NOT NULL AND f.FEE_YEAR IS NOT NULL
   AND NVL(s.CNT, 0) <> NVL(f.TOTAL_SESSIONS, 0)
 ORDER BY f.FEE_YEAR, f.FEE_MONTH, f.CLASS_ID, f.STUDENT_ID;

-- Tóm tắt khớp / lệch
SELECT CASE WHEN NVL(s.CNT, 0) = NVL(f.TOTAL_SESSIONS, 0) THEN 'MATCH' ELSE 'MISMATCH' END KQ, COUNT(*) FEES
  FROM FIN_TUITION_FEES f
  LEFT JOIN (SELECT FEE_ID, COUNT(*) CNT FROM FIN_TUITION_FEE_SESSIONS GROUP BY FEE_ID) s ON s.FEE_ID = f.ID
 WHERE f.IS_DELETED = 0 AND f.STATUS <> 'CANCELLED' AND f.FEE_MONTH IS NOT NULL AND f.FEE_YEAR IS NOT NULL
 GROUP BY CASE WHEN NVL(s.CNT, 0) = NVL(f.TOTAL_SESSIONS, 0) THEN 'MATCH' ELSE 'MISMATCH' END;
```

Lệch là có thể xảy ra (điểm danh bị sửa sau khi lập phiếu, phiếu sửa tay). Phiếu lệch vẫn giữ số tiền cũ; dòng session chỉ dùng để chặn thu trùng và in ngày.
Quyết định:

```sql
COMMIT;
-- hoặc
ROLLBACK;
```

## 5. DML V23_8 (PERIOD_* cho phiếu tháng cũ)

```sql
@V23_8__backfill_legacy_fee_period.sql
-- "N rows updated."

SELECT COUNT(*) LEGACY_NO_PERIOD FROM FIN_TUITION_FEES
 WHERE FEE_MONTH IS NOT NULL AND FEE_YEAR IS NOT NULL AND PERIOD_FROM IS NULL;
-- mong đợi 0

SELECT COUNT(*) BAD_PERIOD FROM FIN_TUITION_FEES
 WHERE FEE_MONTH IS NOT NULL
   AND (EXTRACT(MONTH FROM PERIOD_FROM) <> FEE_MONTH OR PERIOD_TO <> LAST_DAY(PERIOD_FROM));
-- mong đợi 0

SELECT IS_RANGE, COUNT(*) FROM FIN_TUITION_FEES GROUP BY IS_RANGE;
-- vẫn chỉ IS_RANGE = 0

COMMIT;
-- hoặc ROLLBACK;
```

## 6. V23_9 (báo cáo thu tiền theo lớp)

```sql
@V23_9__rpt_class_collection_by_session.sql
-- "Procedure created." + "No errors."

SELECT OBJECT_NAME, OBJECT_TYPE, STATUS, LAST_DDL_TIME FROM USER_OBJECTS
 WHERE OBJECT_NAME IN ('PRC_RPT_CLASS_COLLECTION','PRC_GET_TUITION_SLIP_DATA');
-- mong đợi STATUS = VALID

SELECT OBJECT_NAME, OBJECT_TYPE FROM USER_OBJECTS WHERE STATUS = 'INVALID';
-- không có đối tượng mới bị INVALID
```

Đổi cách tính doanh thu phiếu khoảng ngày: sửa 1 dòng `C_SPLIT_BY_SESSION CONSTANT NUMBER := 1;` (0 = tính trọn vào tháng của `PERIOD_FROM`) rồi chạy lại file.

## 7. Thoát

Chỉ thoát sau khi đã COMMIT/ROLLBACK V23_7 và V23_8:

```sql
EXIT
```

## 8. Rollback (theo thứ tự ngược)

```sql
-- V23_9: chạy lại khối PRC_RPT_CLASS_COLLECTION trong V14_3__fin_reports.sql
-- V23_8 (đã COMMIT):
UPDATE FIN_TUITION_FEES SET PERIOD_FROM = NULL, PERIOD_TO = NULL WHERE FEE_MONTH IS NOT NULL AND IS_RANGE = 0;
-- V23_7 (đã COMMIT):
DELETE FROM FIN_TUITION_FEE_SESSIONS WHERE CREATED_BY = 'V23_7_BACKFILL';
COMMIT;
-- V23_6..V23_1:
DROP INDEX IDX_FEES_PERIOD;
DROP INDEX IDX_FEE_SESSIONS_FEE;
DROP TABLE FIN_TUITION_FEE_SESSIONS;
DROP SEQUENCE SEQ_FIN_TUITION_FEE_SESSIONS;
ALTER TABLE FIN_TUITION_FEES DROP CONSTRAINT CK_FEES_PERIOD;
ALTER TABLE FIN_TUITION_FEES DROP (PERIOD_FROM, PERIOD_TO, IS_RANGE);
```

Rollback DDL làm mất dữ liệu phiếu khoảng ngày (`IS_RANGE = 1`) đã tạo. Trước đó cần hủy/xóa các phiếu này hoặc rollback cả backend.
Backend mới cần đủ V23_1..V23_6 (cột `IS_RANGE`, `PERIOD_*`, bảng session). Không deploy backend mới khi DB chưa chạy V23.

## 9. Sau khi chạy (QA trên PRE)

- Chọn lớp có điểm danh tháng 10 → Xem trước → Tạo phiếu → in phiếu, đối chiếu ngày.
  `SELECT ID, FEE_CODE, IS_RANGE, FEE_MONTH, PERIOD_FROM, PERIOD_TO, DISCOUNT_AMOUNT FROM FIN_TUITION_FEES WHERE IS_RANGE = 1 ORDER BY ID DESC;`
  mong đợi `IS_RANGE = 1`, `FEE_MONTH` NULL, `DISCOUNT_AMOUNT = 0`.
- Tạo lại khoảng chồng lấn → 409 `FEE_SESSION_CONFLICT`.
- Hủy phiếu → `SELECT COUNT(*) FROM FIN_TUITION_FEE_SESSIONS WHERE FEE_ID = <id>;` = 0 → tạo lại được.
- Kiểm tra nội dung VietQR `<mã HS> HP ddMM-ddMM`.
