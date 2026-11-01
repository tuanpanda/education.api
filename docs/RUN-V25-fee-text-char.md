# RUN V25: cột văn bản phí → `VARCHAR2(n CHAR)` (V25_1) — BUG-FEE-RANGE-04

Spec: `docs/SPEC-fee-date-range.md` §16 · Contract fee-range §16 (giới hạn tính theo **ký tự**).
Chạy tay bằng sqlplus trên **schema thật `EDUCATION`**, **sau V24** (`docs/RUN-V24-fee-partial.md`).
Bot không chạy script này. Mật khẩu chỉ gõ tại prompt, không ghi vào file, lệnh, biến môi trường dùng chung hay log. **Không dùng SYS** (kể cả `AS SYSDBA`).

## Vì sao

`TEACHER_COMMENT` được tạo là `VARCHAR2(1000)` ở V10. Với `NLS_LENGTH_SEMANTICS = BYTE` (mặc định) cột này là **1000 byte**, trong khi một ký tự tiếng Việt có dấu chiếm 2–3 byte UTF-8 → 1000 ký tự có dấu gây ORA-12899 (500). V25_1 đổi đơn vị sang CHAR, giữ nguyên số `n`.

| Bảng.Cột | Tạo ở | Trước | Sau |
|---|---|---|---|
| `FIN_TUITION_FEES.NOTE` | V1 | `VARCHAR2(255)` BYTE | `VARCHAR2(255 CHAR)` |
| `FIN_TUITION_FEES.CANCEL_REASON` | V14_1 | `VARCHAR2(255)` BYTE | `VARCHAR2(255 CHAR)` |
| `FIN_TUITION_FEES.TEACHER_COMMENT` | V10 | `VARCHAR2(1000)` BYTE | `VARCHAR2(1000 CHAR)` |
| `FIN_TUITION_FEES.FOOTER_WISH` | V10 | `VARCHAR2(500)` BYTE | `VARCHAR2(500 CHAR)` |
| `FIN_TUITION_FEES.SLIP_LABEL` | V10 | `VARCHAR2(50)` BYTE | `VARCHAR2(50 CHAR)` |
| `FIN_PAYMENT_TRANSACTIONS.NOTE` | V1 | `VARCHAR2(255)` BYTE | `VARCHAR2(255 CHAR)` |
| `FIN_PAYMENT_TRANSACTIONS.PAYER_NAME` | V14_2 | `VARCHAR2(150)` BYTE | `VARCHAR2(150 CHAR)` |
| `FIN_PAYMENT_TRANSACTIONS.VOID_REASON` | V14_2 | `VARCHAR2(255)` BYTE | `VARCHAR2(255 CHAR)` |
| `FIN_TUITION_FEES.PARTIAL_REASON` | V24_1 | `VARCHAR2(200 CHAR)` | **chỉ kiểm tra**, không sửa |

- Chỉ sửa cột có `CHAR_USED = 'B'`; cột đã `C` in `already CHAR`. Số `n` mới = `GREATEST(n hiện tại, n mong đợi)`, không bao giờ thu nhỏ.
- Không đổi: cột mã/trạng thái/người tạo (`FEE_CODE`, `STATUS`, `CREATED_BY`…; chỉ ASCII), `BANK_REFERENCE_NO`, `RECEIPT_NO`, `FIN_STUDENT_DISCOUNTS.REASON` (ngoài phạm vi API phiếu/phí lần này).
- `VARCHAR2(1000 CHAR)` cần tối đa 4000 byte, vừa giới hạn `MAX_STRING_SIZE = STANDARD`.
- Chỉ DDL (tự commit). Không có DML, không `COMMIT`/`EXIT` trong file. Không chạy khi đang mở giao dịch DML khác.
- `PARTIAL_REASON` chưa có hoặc chưa CHAR → dừng với `ORA-20025` **trước** khi sửa cột nào (chạy V24 trước).

## Bước 0. Sao lưu bằng Data Pump

Tài khoản DBA riêng hoặc `EDUCATION`, **không SYS**; file dump ngoài repo; mật khẩu nhập tại prompt (cmd, trên máy DB):

```bat
set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
F:\Database\bin\expdp.exe <DB_USER>@//localhost:1521/<SERVICE> TABLES=EDUCATION.FIN_TUITION_FEES,EDUCATION.FIN_PAYMENT_TRANSACTIONS DIRECTORY=DATA_PUMP_DIR DUMPFILE=edu_fees_before_v25.dmp LOGFILE=edu_fees_before_v25.log
```

Log phải báo `successfully completed`.

## Bước 1. Mở phiên + SPOOL

```bat
cd /d E:\JAVA\edu-wt\fee-range\src\main\resources\db\migration
set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
F:\Database\bin\sqlplus.exe /nolog
```

```sql
SPOOL C:\Users\tuand\v23\08_v25.log
CONNECT <DB_USER>@//localhost:1521/<SERVICE>
-- go mat khau tai prompt (khong hien trong spool)
SET LINESIZE 200
SET PAGESIZE 100
COLUMN TABLE_NAME FORMAT A26
COLUMN COLUMN_NAME FORMAT A18
SHOW USER
SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') SCHEMA_NAME FROM DUAL;
```

`SCHEMA_NAME` phải là `EDUCATION` (tài khoản DBA: `ALTER SESSION SET CURRENT_SCHEMA = EDUCATION;`).

## Bước 2. Trước khi chạy (không ghi gì)

```sql
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHAR_LENGTH, CHAR_USED FROM USER_TAB_COLUMNS
 WHERE TABLE_NAME IN ('FIN_TUITION_FEES','FIN_PAYMENT_TRANSACTIONS') AND DATA_TYPE = 'VARCHAR2'
 ORDER BY TABLE_NAME, COLUMN_NAME;
-- mong doi: 8 cot o bang tren CHAR_USED = B; PARTIAL_REASON 200 C

SELECT VALUE FROM NLS_DATABASE_PARAMETERS WHERE PARAMETER IN ('NLS_CHARACTERSET','NLS_LENGTH_SEMANTICS');

SELECT OBJECT_NAME, OBJECT_TYPE FROM USER_OBJECTS WHERE STATUS = 'INVALID';
-- mong doi: 0 dong
```

## Bước 3. Chạy V25_1

```sql
@V25_1__fee_text_columns_char.sql
```

Mỗi cột in `n BYTE -> n CHAR` hoặc `already CHAR`; `PARTIAL_REASON: OK (200 CHAR)`.

## Bước 4. Biên dịch lại object INVALID (bắt buộc)

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
ALTER TRIGGER TRG_FIN_TUITION_FEES_BI_CODE COMPILE;
SHOW ERRORS TRIGGER TRG_FIN_TUITION_FEES_BI_CODE
```

**Tiêu chí đạt:**

```sql
SELECT OBJECT_NAME, OBJECT_TYPE FROM USER_OBJECTS WHERE STATUS = 'INVALID';
-- phai tra 0 dong ("no rows selected")
```

## Bước 5. Sau khi chạy

```sql
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHAR_LENGTH, CHAR_USED FROM USER_TAB_COLUMNS
 WHERE TABLE_NAME IN ('FIN_TUITION_FEES','FIN_PAYMENT_TRANSACTIONS') AND DATA_TYPE = 'VARCHAR2'
 ORDER BY TABLE_NAME, COLUMN_NAME;
-- mong doi: 8 cot o bang tren + PARTIAL_REASON co CHAR_USED = C, CHAR_LENGTH dung bang

SELECT COUNT(*) TOTAL_FEES FROM FIN_TUITION_FEES;
-- bang so truoc khi chay (khong doi du lieu)

SPOOL OFF
EXIT
```

Gửi lại `C:\Users\tuand\v23\08_v25.log` để đối chiếu.

## Rollback

Chỉ làm được khi mọi giá trị hiện có còn vừa số **byte** cũ; nếu đã có dữ liệu dài hơn (ví dụ 1000 ký tự có dấu) thì ORA-01441, khi đó giữ CHAR hoặc khôi phục từ export Bước 0.

```sql
ALTER TABLE FIN_TUITION_FEES MODIFY (NOTE VARCHAR2(255 BYTE), CANCEL_REASON VARCHAR2(255 BYTE),
  TEACHER_COMMENT VARCHAR2(1000 BYTE), FOOTER_WISH VARCHAR2(500 BYTE), SLIP_LABEL VARCHAR2(50 BYTE));
ALTER TABLE FIN_PAYMENT_TRANSACTIONS MODIFY (NOTE VARCHAR2(255 BYTE), PAYER_NAME VARCHAR2(150 BYTE),
  VOID_REASON VARCHAR2(255 BYTE));
```

Sau rollback lặp lại Bước 4 (0 INVALID). Backend vẫn chạy được với cột BYTE: dữ liệu quá dài theo byte trả 400 `VALIDATION_ERROR` (ORA-12899 được map 400) thay vì 500.

## Kiểm thử sau khi chạy (chỉ trên lớp test riêng)

1. Tạo phiếu khoảng ngày với `teacherComment` = 1000 ký tự có dấu (ví dụ `"ạ"` × 1000) → 201.
2. 1001 ký tự → 400 `VALIDATION_ERROR`. Tương tự `footerWish` 500/501, `partialReason` 200/201.
3. Dọn: hủy/xóa phiếu test.
