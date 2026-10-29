# RUN V24: phiếu gói chưa đủ 10 buổi (V24_1 → V24_2)

Spec: `docs/SPEC-fee-date-range.md` §13 · Contract: fee-range-api v1.4 §13.2.
Chạy tay bằng sqlplus (không có Flyway) trên **schema thật `EDUCATION`**, **sau khi đã chạy xong V23** (xem `docs/RUN-V23-fee-range.md`).
Bot không chạy script này. Mật khẩu chỉ gõ tại prompt, không ghi vào file, lệnh, biến môi trường dùng chung hay log. **Không dùng SYS** (kể cả `AS SYSDBA`).

**Backend mới (commit này) cần V24 trước khi chạy:** entity đọc/ghi `IS_PARTIAL`, `PARTIAL_REASON`; thiếu cột thì mọi truy vấn `FIN_TUITION_FEES` lỗi ORA-00904. Thứ tự triển khai: chạy V24, rồi mới chạy bản BE mới.

## Thứ tự và tính chất

| # | File | Loại | Ghi chú |
|---|------|------|---------|
| 0 | (Data Pump export) | Sao lưu | Khuyến nghị. V24 chỉ thêm cột/ràng buộc, rollback được bằng DROP (không mất dữ liệu cũ). |
| 1 | `V24_1__fee_partial_columns.sql` | DDL | `IS_PARTIAL NUMBER(1) DEFAULT 0 NOT NULL` + `CK_FEES_IS_PARTIAL CHECK (IS_PARTIAL IN (0,1))`; `PARTIAL_REASON VARCHAR2(200 CHAR)`. Phiếu hiện có tự nhận `IS_PARTIAL = 0`, không cần backfill. |
| 2 | `V24_2__fee_partial_reason_check.sql` | DDL | `CK_FEES_PARTIAL_REASON`: `IS_PARTIAL = 0` thì `PARTIAL_REASON IS NULL`; `IS_PARTIAL = 1` thì `IS_RANGE = 1` và có lý do. |

- Cả hai đều là DDL nên **tự commit**; không có DML, không có `COMMIT`/`EXIT` trong file. Giống quy ước V23, `SET EXITCOMMIT OFF` chỉ dùng cho file DML nên **không** có trong V24.
- Không chạy V24 khi đang mở giao dịch DML khác (DDL sẽ commit luôn giao dịch đó).
- Mỗi file có `WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK`. Chạy lại an toàn: in `already exists`.
- **Không cần sửa UQ `UQ_FEE_SESSIONS_ATTENDANCE`:** hủy/xóa phiếu (`cancel`/`delete`) đã xóa các dòng `FIN_TUITION_FEE_SESSIONS` của phiếu đó, nên buổi được tính lại là "chưa thu" (§13.4). Phiếu đã thu tiền không hủy được (`FEE_HAS_PAYMENTS`).

## Bước 0. Sao lưu (khuyến nghị)

Như Bước 0 của RUN-V23 (tài khoản DBA riêng hoặc `EDUCATION`, **không SYS**, file dump ngoài repo, mật khẩu nhập tại prompt):

```bat
set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
F:\Database\bin\expdp.exe <DB_USER>@//localhost:1521/<SERVICE> TABLES=EDUCATION.FIN_TUITION_FEES DIRECTORY=DATA_PUMP_DIR DUMPFILE=edu_fees_before_v24.dmp LOGFILE=edu_fees_before_v24.log
```

## Bước 1. Mở phiên

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
SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') SCHEMA_NAME FROM DUAL;
```

`SCHEMA_NAME` phải là `EDUCATION` (tài khoản DBA: `ALTER SESSION SET CURRENT_SCHEMA = EDUCATION;`).

## Bước 2. Kiểm tra trước (không ghi gì)

```sql
-- V23 da chay (mong doi 1 dong IS_RANGE)
SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'FIN_TUITION_FEES' AND COLUMN_NAME = 'IS_RANGE';

-- Chua co cot V24 (lan chay dau: 0 dong)
SELECT COLUMN_NAME FROM USER_TAB_COLUMNS
 WHERE TABLE_NAME = 'FIN_TUITION_FEES' AND COLUMN_NAME IN ('IS_PARTIAL','PARTIAL_REASON');

-- Truoc khi chay: 0 INVALID
SELECT OBJECT_NAME, OBJECT_TYPE FROM USER_OBJECTS WHERE STATUS = 'INVALID';

SELECT COUNT(*) TOTAL_FEES FROM FIN_TUITION_FEES;
```

## Bước 3. DDL V24_1 → V24_2

```sql
@V24_1__fee_partial_columns.sql
@V24_2__fee_partial_reason_check.sql
```

Mỗi file in `created` hoặc `already exists`. Nếu V24_2 báo `ORA-02293` thì có dòng vi phạm: chạy SELECT cuối ở Bước 5 để xem, **dừng lại** và báo.

## Bước 4. Biên dịch lại object bị INVALID (bắt buộc)

ALTER TABLE `FIN_TUITION_FEES` làm các procedure phụ thuộc bị INVALID (như sự cố sau V23_1).

```sql
EXEC DBMS_UTILITY.COMPILE_SCHEMA(schema => 'EDUCATION', compile_all => FALSE);
```

hoặc từng object:

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
ALTER PROCEDURE PRC_RPT_CLASS_COLLECTION COMPILE;
SHOW ERRORS PROCEDURE PRC_RPT_CLASS_COLLECTION
```

Mỗi `SHOW ERRORS` phải in `No errors.` **Tiêu chí đạt:**

```sql
SELECT OBJECT_NAME, OBJECT_TYPE FROM USER_OBJECTS WHERE STATUS = 'INVALID';
-- phai tra 0 dong ("no rows selected")
```

Còn dòng nào: `ALTER <TYPE> <NAME> COMPILE;` + `SHOW ERRORS <TYPE> <NAME>`, ghi lại lỗi và dừng.

## Bước 5. Kiểm tra sau khi chạy

```sql
SELECT COLUMN_NAME, DATA_TYPE, CHAR_LENGTH, NULLABLE, DATA_DEFAULT FROM USER_TAB_COLUMNS
 WHERE TABLE_NAME = 'FIN_TUITION_FEES' AND COLUMN_NAME IN ('IS_PARTIAL','PARTIAL_REASON');
-- mong doi: IS_PARTIAL NUMBER N DEFAULT 0; PARTIAL_REASON VARCHAR2 200 Y

SELECT CONSTRAINT_NAME, STATUS, VALIDATED FROM USER_CONSTRAINTS
 WHERE TABLE_NAME = 'FIN_TUITION_FEES' AND CONSTRAINT_NAME IN ('CK_FEES_IS_PARTIAL','CK_FEES_PARTIAL_REASON');
-- mong doi: 2 dong, ENABLED, VALIDATED

SELECT IS_RANGE, IS_PARTIAL, COUNT(*) CNT FROM FIN_TUITION_FEES GROUP BY IS_RANGE, IS_PARTIAL ORDER BY 1, 2;
-- lan dau: chi IS_PARTIAL = 0; tong CNT = TOTAL_FEES o Buoc 2

SELECT COUNT(*) BAD_ROWS FROM FIN_TUITION_FEES
 WHERE (IS_PARTIAL = 0 AND PARTIAL_REASON IS NOT NULL)
    OR (IS_PARTIAL = 1 AND (IS_RANGE <> 1 OR PARTIAL_REASON IS NULL));
-- mong doi: 0
```

Không có DML nên không cần `COMMIT;`. Thoát: `EXIT`.

## Rollback (theo thứ tự ngược)

Chạy bản BE cũ trước (bản BE mới cần hai cột này), rồi:

```sql
ALTER TABLE FIN_TUITION_FEES DROP CONSTRAINT CK_FEES_PARTIAL_REASON;
ALTER TABLE FIN_TUITION_FEES DROP (IS_PARTIAL, PARTIAL_REASON);
```

Sau đó lặp lại Bước 4 (0 INVALID). DROP cột làm mất cờ/lý do của các phiếu partial đã tạo (phiếu vẫn còn, thành phiếu thường); muốn giữ thì export trước.

## Kiểm thử sau khi chạy (chỉ trên lớp test riêng)

1. Lớp test có học sinh A ≥ 12 buổi PRESENT/LATE, học sinh B 3 buổi.
2. Xem trước: A `unbilledCount = 12`, `eligible = true`, `packageDates` 10 ngày cũ nhất; B `eligible = false`.
3. Tạo cho A: phiếu 10 buổi, `PERIOD_FROM/TO` = ngày buổi đầu/cuối; xem trước lại: A còn 2.
4. Tạo cho B không `allowPartial` → 400 `STUDENT_NOT_ELIGIBLE`; có `allowPartial` + lý do → phiếu 3 buổi, `IS_PARTIAL = 1`.
5. Hủy phiếu của A → xem trước lại: A về 12; tạo lại cùng khoảng → thành công.
6. Dọn: hủy/xóa các phiếu test.
