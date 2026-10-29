# SPEC: Tuition fee by date range (thu học phí theo khoảng ngày)

Version: **v1.1** (2026-10-29), aligned with API contract `fee-range-api` **v1.3 (final)**. v1.0 text below is kept and amended in place; where v1.0 and §0 disagree, **§0 wins**.

## 0. v1.1 decisions (contract v1.3 + QC)

| # | Decision |
|---|----------|
| V1 | **No discount on range fees yet (deferred).** `DISCOUNT_AMOUNT = 0`; `StudentDiscountService.computeDiscount` is not called. **PUT `/{id}` rule (LEADER):** `IS_RANGE = 1` → discount always forced to 0 server-side (request value ignored). `IS_RANGE = 0` (legacy monthly or manual) → `discountAmount` given: use it (validated ≤ total, net ≥ paid); `discountAmount` null/omitted: **keep the existing discount** (no longer reset to 0). |
| V2 | **Explicit marker `FIN_TUITION_FEES.IS_RANGE NUMBER(1) DEFAULT 0 NOT NULL`** + `CK_FEES_IS_RANGE CHECK (IS_RANGE IN (0,1))` (V23_1). Only `generate-range` writes 1. Legacy and manual fees stay 0 (no backfill). `rangeFee` in every fee DTO = `IS_RANGE = 1` (not derived from `FEE_MONTH IS NULL`, since manual fees may have no month either). |
| V3 | Range fees keep **`FEE_MONTH`/`FEE_YEAR` NULL in the DB** (so `UQ_FEES_MONTHLY` never applies). DTOs expose a *display* `feeMonth`/`feeYear` derived from `PERIOD_FROM`, plus `periodFrom`, `periodTo`, `periodText`, `rangeFee` on all fee DTOs: slip, search list, detail, portal list/detail, debt aging, student ledger, export rows, payment transaction fee summary. |
| V4 | Slip dates stay in **`attendedDates`** (dd/MM, sorted, from `FIN_TUITION_FEE_SESSIONS`); no `sessionBadges` field. A **cancelled range slip shows no dates** and never re-reads attendance. The attendance fallback is only for legacy monthly fees without session rows. |
| V5 | 409 `FEE_SESSION_CONFLICT`: details in **`data.details`** `[{studentId, studentCode, studentName, dates, feeCodes}]`. An ORA-00001 on `UQ_FEE_SESSIONS_ATTENDANCE` (race) maps to the same 409 with `data.details = []`. One `@Transactional` for the whole request: all or nothing. |
| V6 | Conflict lookup is a **JOIN by class + date range** (session × fee × attendance), not an `IN` list of attendance IDs (avoids ORA-01795 above 1000 IDs). Report period lookups by ID are chunked at 900. |
| V7 | **Students in scope:** active (ENROLLED) students are always listed, even with 0 sessions (`skippedNoAttendance`). Students who left the class but have billable sessions in the range are billed and listed with `inactive: true`; left students with 0 sessions are not shown. `inactive` is always present in the preview JSON. Money fields are JSON `null` (not omitted) when no price is passed. |
| V8 | **Revenue by session:** `PRC_RPT_CLASS_COLLECTION` (V23_9) splits `IS_RANGE = 1` fees by the month of `SESSION_DATE` × `PRICE_PER_SESSION` (discount/paid/remaining pro-rated). One-line switch `C_SPLIT_BY_SESSION` (0 = whole fee in the month of `PERIOD_FROM`). Debt-aging and dashboard procedures are unchanged. |
| V9 | **VietQR reconciliation is manual** for range fees: match by transfer content + amount; a truncated student code (> 12 chars) or multiple matches need manual reconciliation. The backend has no automatic content-parsing reconciliation, so nothing changes there. |
| V10 | Validation: `toDate - fromDate + 1 <= 366`; a price with more than 2 decimals is 400 (no rounding). 0 fees created → **200**; ≥ 1 → **201**. Cancel/delete always releases the session rows. Missing class → 404 `CLASS_NOT_FOUND`; no permission → 403 (MENU permission `MENU_TUITION_FEE:CREATE`; no per-class restriction this round). `POST /generate-monthly` is removed (now 405 because the path matches `/{id}`). |
| V11 | Target DB: the **real schema `EDUCATION`** (there is no separate PRE schema). Before V23_1 the owner takes a **Data Pump export** of `FIN_TUITION_FEES` or the whole `EDUCATION` schema (DBA or `EDUCATION` account, never SYS; dump file outside the repo; password at the prompt only). V23_7/V23_8 are run by hand, with row counts before/after; the owner decides COMMIT/ROLLBACK. After COMMIT, V23_8 UPDATEs cannot be undone by script and are restored from that export. **Run order (QC): V23_1 → V23_6 → recompile INVALID objects → V23_9 → recompile INVALID objects → V23_7 → V23_8 → verification SELECTs only → owner types `COMMIT;` or `ROLLBACK;`.** Recompile = `EXEC DBMS_UTILITY.COMPILE_SCHEMA(schema => 'EDUCATION', compile_all => FALSE);` or `ALTER PROCEDURE ... COMPILE` + `SHOW ERRORS` for the known dependents (`PRC_GET_TUITION_SLIP_DATA`, `PRC_GET_TUITION_FEE_DETAIL`, `PRC_RPT_DASHBOARD_METRICS`, `PRC_RPT_FINANCE_SUMMARY`, `PRC_RPT_DEBT_AGING`, `PRC_RPT_STUDENT_LEDGER`). Pass criterion after V23_9: `SELECT OBJECT_NAME, OBJECT_TYPE FROM USER_OBJECTS WHERE STATUS = 'INVALID'` returns 0 rows. V23_9 is DDL (auto-commits), so it runs before the DML. No DDL and no `EXIT` between V23_7 and that decision. V23_7/V23_8 start with `SET EXITCOMMIT OFF` (an accidental exit rolls back). Rollback runs in reverse: V23_8 (restore from export) → V23_7 (`DELETE ... CREATED_BY = 'V23_7_BACKFILL'`, then COMMIT/ROLLBACK before any DDL) → V23_9 (re-run V14_3 procedure) → V23_6 … V23_1. Functional testing only on a **dedicated test class**. Unit tests use Mockito / the in-memory transaction manager; no DB. See `docs/RUN-V23-fee-range.md`. |
| V12 | Audit: there was no audit entry for `generate-monthly` and `TuitionSlipService` is not covered by `AuditTrailAspect`, so "the same audit action" does not exist; generation is logged (class, range, counts, actor). Open item. |
| V13 | `V23_4` omits `TABLESPACE EDU`; the table uses the user's default tablespace (checked in the RUN guide). |


Status: approved by the owner on 2026-10-29 (widget t102 + "oke" t103). Branches: `feat/fee-date-range` (API, worktree `E:\JAVA\edu-wt\fee-range`) and `feat/fee-date-range-ui` (UI, worktree `E:\JAVA\edu-wt\fee-range-ui`).
Rules: commit locally only. No push, merge or deploy until the owner has tested (v1.1: on a dedicated test class in `EDUCATION`) and approved.

## 1. Decisions (locked)

| # | Decision |
|---|----------|
| D1 | Amount is computed automatically: `count(billable sessions) × pricePerSession`. ~~− discounts~~ **v1.1: discount deferred, always 0 (§0 V1).** |
| D2 | If any billable date of a student in the range is already on another fee that is **not cancelled and not deleted**, the request is **rejected** with a conflict error that lists the dates (and the fee codes). Nothing is created. |
| D3 | The slip does **not** mark late sessions; there is no "(muộn)" marker. LATE and PRESENT dates look the same. |
| D4 | **Month mode is removed.** Generation only accepts `fromDate` / `toDate`. |
| D5 | The previous uncommitted WIP in `E:\JAVA\education` was discarded. A backup is at `E:\JAVA\edu-wt\backup\wip-attendance-billing-20261029\` (reference only; do not re-apply as-is). V23_1 is free again. |

## 2. Business rules

- **Billable session:** a row in `EDU_ATTENDANCE` with
  - `CLASS_ID` equal to the class,
  - `STUDENT_ID` equal to the student,
  - `IS_DELETED = 0`,
  - `STATUS IN ('PRESENT','LATE')` (`DomainConstants.BILLABLE_ATTENDANCE_STATUSES`),
  - `fromDate <= ATTENDANCE_DATE <= toDate` (both ends inclusive).
- **Validation:**
  - `fromDate` and `toDate` are required.
  - `fromDate <= toDate`.
  - The range is at most 366 days.
  - `pricePerSession > 0`, with the same constraints as today.
  - `classId` is required.
  - `teacherComment` and `footerWish` keep their current limits (1000 and 500).
- **Scope:** generation is per class and covers all active students of the class, as `generate-monthly` does now. A student with 0 billable sessions in the range is skipped and counted in `skippedNoAttendance`.
- **Conflict (D2):**
  - Before writing anything, look up session rows of the class in the range: `FIN_TUITION_FEE_SESSIONS` joined to `FIN_TUITION_FEES` (`IS_DELETED=0 AND STATUS<>'CANCELLED'`) and `EDU_ATTENDANCE` (class, billable status, date in range). v1.1: JOIN, no ID list (§0 V6).
  - If there is any hit, return **HTTP 409** with error code `FEE_SESSION_CONFLICT`. The details list one entry per student: `{studentId, studentCode, studentName, dates:[yyyy-MM-dd...], feeCodes:[...]}`.
  - This is all-or-nothing for the whole request.
  - Race safety: the `UQ_FEE_SESSIONS_ATTENDANCE` unique violation (ORA-00001) must map to the same 409 and must not surface as a 500.
- **No more "update existing monthly fee":** the old regenerate/update path (`findMonthlyFeeId`, auto-cancel when there are no sessions, net-below-paid conflict) does not apply to range fees. To re-bill, the user cancels the old fee first.
- **Cancel / delete fee:** cancelling (`POST /{id}/cancel`) or soft-deleting a fee **deletes its rows in `FIN_TUITION_FEE_SESSIONS`** in the same transaction. This frees those dates for a new fee. Paid fees still follow the existing cancel rules.
- **New fee fields:**
  - `PERIOD_FROM = fromDate`, `PERIOD_TO = toDate`.
  - `FEE_MONTH = NULL`, `FEE_YEAR = NULL`.
  - `TOTAL_SESSIONS = count`, `PRICE_PER_SESSION = price`.
  - `TOTAL_AMOUNT = count×price` (scale 2), `DISCOUNT_AMOUNT = 0` (v1.1), `PAID_AMOUNT = 0`.
  - `IS_RANGE = 1` (v1.1).
  - `STATUS` via `FeeStatusCalculator`.
  - `DUE_DATE = resolveDueDate(toDate, today)`, the existing rule: if `toDate < today`, then today + `FEE_BACKBILL_GRACE_DAYS`.
  - `NOTE = "HP dd/MM/yyyy-dd/MM/yyyy"`.
  - `SLIP_LABEL` default.
  - One row in `FIN_TUITION_FEE_SESSIONS` per billable attendance.
- **Slip dates come from `FIN_TUITION_FEE_SESSIONS`** (a snapshot taken at billing time), not from re-reading attendance. If attendance is edited after billing, the slip stays as billed. Re-billing goes through cancel and recreate.
- **Legacy fees** (`FEE_MONTH`/`FEE_YEAR` set, created before this change) still display and print. They get session rows through the backfill script (§4, V23_7), so the conflict check covers them too.

## 3. UQ_FEES_MONTHLY

- Keep the index as it is; do not drop it.
- It only indexes rows where `FEE_MONTH`/`FEE_YEAR` are NOT NULL. Range fees leave both NULL, so it never blocks them, and it still protects legacy rows.
- Duplicate protection for range fees comes from `UQ_FEE_SESSIONS_ATTENDANCE` (one attendance on at most one active fee) together with the service-level check.
- Add a unit test proving that two range fees in the same month for the same student and class (with disjoint dates) are allowed.

## 4. Migrations (the owner runs them himself on `EDUCATION`, after a Data Pump export; v1.1, see §0 V11)

Format rules (sqlplus pitfalls seen before):

- **One statement block per file** (one PL/SQL `BEGIN ... END;` + `/`, or one DDL).
- **No comment after `;` on the same line.**
- Put `SET DEFINE OFF` and `SET SQLBLANKLINES ON` at the top.
- Make every block idempotent: catch -1430 / -955 / -2261 / -2264 / -2275 and print "already exists".
- **No usernames or passwords in comments or `PROMPT`s.** For the run line, write only `sqlplus /nolog` then `CONNECT` (the user types the credentials himself).
- Files go in `src/main/resources/db/migration/`, using the next number V23_x (V23_1 is free now):

| File | Content |
|------|---------|
| `V23_1__fee_period_columns.sql` | `ALTER TABLE FIN_TUITION_FEES ADD (PERIOD_FROM DATE, PERIOD_TO DATE)` and (v1.1) `IS_RANGE NUMBER(1) DEFAULT 0 NOT NULL CONSTRAINT CK_FEES_IS_RANGE CHECK (IS_RANGE IN (0,1))`, each column in its own sub-block (one top-level block). Rollback drops all three. |
| `V23_2__fee_period_check.sql` | `ALTER TABLE FIN_TUITION_FEES ADD CONSTRAINT CK_FEES_PERIOD CHECK (PERIOD_FROM IS NULL OR PERIOD_TO IS NULL OR PERIOD_FROM <= PERIOD_TO)` |
| `V23_3__seq_fee_sessions.sql` | `CREATE SEQUENCE SEQ_FIN_TUITION_FEE_SESSIONS START WITH 1 INCREMENT BY 1 NOCACHE` |
| `V23_4__fee_sessions_table.sql` | `CREATE TABLE FIN_TUITION_FEE_SESSIONS (ID NUMBER(19) PRIMARY KEY, FEE_ID NUMBER(19) NOT NULL, ATTENDANCE_ID NUMBER(19) NOT NULL, SESSION_DATE DATE NOT NULL, CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL, CREATED_BY VARCHAR2(50), CONSTRAINT FK_FEE_SESSIONS_FEE FOREIGN KEY (FEE_ID) REFERENCES FIN_TUITION_FEES(ID), CONSTRAINT FK_FEE_SESSIONS_ATT FOREIGN KEY (ATTENDANCE_ID) REFERENCES EDU_ATTENDANCE(ID), CONSTRAINT UQ_FEE_SESSIONS_ATTENDANCE UNIQUE (ATTENDANCE_ID)) TABLESPACE EDU`. v1.1: tablespace clause omitted (§0 V13). Match the column types to the existing `FIN_TUITION_FEES.ID` / `EDU_ATTENDANCE.ID`; check them on PRE first. |
| `V23_5__idx_fee_sessions_fee.sql` | `CREATE INDEX IDX_FEE_SESSIONS_FEE ON FIN_TUITION_FEE_SESSIONS (FEE_ID, SESSION_DATE)` |
| `V23_6__idx_fees_period.sql` | `CREATE INDEX IDX_FEES_PERIOD ON FIN_TUITION_FEES (CLASS_ID, PERIOD_FROM, PERIOD_TO)` |
| `V23_7__backfill_legacy_fee_sessions.sql` | DML (one `INSERT ... SELECT`, **no COMMIT inside**; the owner decides COMMIT/ROLLBACK). For legacy fees with `IS_DELETED=0 AND STATUS<>'CANCELLED' AND FEE_MONTH IS NOT NULL`, it inserts the billable attendance of that student/class/month, skipping attendance IDs that already have a row. The owner runs it after V23_1–V23_6. |
| `V23_8__backfill_legacy_fee_period.sql` | DML: `UPDATE FIN_TUITION_FEES SET PERIOD_FROM = first day, PERIOD_TO = last day of FEE_MONTH/FEE_YEAR WHERE FEE_MONTH IS NOT NULL AND PERIOD_FROM IS NULL`. No COMMIT inside. |

- Also provide `docs/RUN-V23-fee-range.md` with the run order and verification queries (row counts, constraint list).
- Add a migration script test in the style of `V19FilesScriptTest`. It asserts:
  - there is one block per file,
  - no line has a comment after `;`,
  - the files contain no `password` / `IDENTIFIED BY` / `user/pass@` text,
  - V23_7/V23_8 contain no `COMMIT`. v1.1: they also start with `SET EXITCOMMIT OFF` (asserted by `V23FeeRangeScriptTest`).
- v1.1: `V23_9__rpt_class_collection_by_session.sql` replaces `PRC_RPT_CLASS_COLLECTION` (§0 V8). It runs **right after V23_6, before V23_7/V23_8** (§0 V11).
- `PRC_GET_TUITION_SLIP_DATA` (V10): check whether it is still used. v1.1: still used and **unchanged**; the backend reads slip dates from `FIN_TUITION_FEE_SESSIONS` itself. If it is, either update it to read the session table and period (as a separate V23_9 file) or remove the usage. Document which.

## 5. API

- **Preview:** `GET /api/v1/tuition-fees/billable-sessions?classId=&fromDate=yyyy-MM-dd&toDate=yyyy-MM-dd[&pricePerSession=]`
  - Same permission as generate. Read-only.
  - It returns per student: `{studentId, studentCode, studentName, sessionDates:[...], sessionCount, totalAmount?, discountAmount?, netAmount?, conflictDates:[...], conflictFeeCodes:[...]}`, plus totals.
  - The amount fields are filled only when `pricePerSession` is passed; otherwise they are JSON `null` (v1.1). Each row also has `inactive` (§0 V7). Totals: `studentCount, billableStudentCount, skippedNoAttendance, sessionCount, totalAmount, discountAmount, netAmount, conflictStudentCount`.
- **Generate:** `POST /api/v1/tuition-fees/generate-range`
  - Body: `{classId, fromDate, toDate, pricePerSession, teacherComment?, footerWish?}`.
  - It returns the `GenerateMonthlyInvoicesResponseDto` shape, renamed to `GenerateInvoicesResponseDto`, with created/skipped counts and fee IDs. A conflict returns 409 (see §2).
  - **Remove `POST /generate-monthly`** and `CreateMonthlyInvoiceRequestDto` (D4).
  - Audit: the same audit action as today, with `fromDate`/`toDate` in the payload.
- **Slip:** `GET /{id}/slip` and `/{id}/slip/html`
  - `TuitionSlipResponseDto` gets `periodFrom`, `periodTo` and `periodText`.
  - `periodText` is `"Từ dd/MM/yyyy đến dd/MM/yyyy"`. Legacy fees keep `"Tháng MM/yyyy"`.
  - ~~`sessionBadges`~~ v1.1: `attendedDates` are dd/MM strings, sorted ascending, read from `FIN_TUITION_FEE_SESSIONS`. If a legacy fee has no rows, fall back to the current attendance query for that month.
  - There is no late marker.
- **VietQR transfer content:**
  - Range fee: `"<studentCode> HP <ddMM>-<ddMM>"`, for example `HS000123 HP 0110-3110`.
  - It must be **≤ 25 characters**. If it is longer, truncate the student code, never the dates.
  - ASCII only, with no `/`.
  - Legacy fee: keep `"<code> HP T<m>"`.
  - Applies everywhere `transferContent` is used: `TuitionSlipServiceImpl`, `QrPaymentController`/`create-qr`, and any payment matching that parses the content.
- Error messages are Vietnamese, in the same style as existing ones. Source files must be saved as **UTF-8**; several current files print garbled in PowerShell, so check that the encoding is not double-converted.

## 6. Backend files to change (`E:\JAVA\edu-wt\fee-range`)

- `entity/TuitionFeeEntity.java`: add `periodFrom` and `periodTo`.
- New: `entity/TuitionFeeSessionEntity.java` and `repository/TuitionFeeSessionRepository.java`, which provides:
  - `findConflicts(attendanceIds)` (join fee, not cancelled/deleted),
  - `findByFeeIdOrderBySessionDate`,
  - `deleteByFeeId`.
- `repository/AttendanceRepository.java`: add a billable query by class + range (+ student), returning id, studentId and date.
- `repository/TuitionFeeRepository.java`: drop the `findMonthlyFeeId` usage from generation; keep the method only if legacy code needs it.
- `dto/request/CreateMonthlyInvoiceRequestDto.java` becomes a new `GenerateRangeInvoicesRequestDto.java`; delete the old one.
- `dto/response/GenerateMonthlyInvoicesResponseDto.java`: rename it or generalise it, and add the conflict detail DTO.
- New: `dto/response/BillableSessionsPreviewDto.java`.
- `dto/response/TuitionSlipResponseDto.java`: add the period fields.
- `service/TuitionSlipService.java` and `service/impl/TuitionSlipServiceImpl.java`: `generateRange`, `previewBillable`, slip building and `transferContent`.
- `service/impl/TuitionSlipHtmlRenderer.java`: period line and badges.
- `service/impl/TuitionFeeServiceImpl.java`: release session rows on cancel and delete.
- `controller/TuitionFeeController.java`: new endpoints; remove `generate-monthly`.
- `controller/QrPaymentController.java` and `dto/request/TuitionQrRequest.java`: transfer content.
- `common/DomainConstants.java`: date-range limit and the conflict error code. Also the `ErrorCode`/exception mapping for 409.
- Check: `FinanceExportServiceImpl` (add period columns to the export), `PortalServiceImpl`/`PortalPaymentDto` (the parent portal shows the period instead of the month), and `ReportRepositoryImpl` (reports grouped by `FEE_MONTH`; document the impact and use `PERIOD_FROM` for range fees).
- Migrations V23_1–V23_8 (+V23_9 if needed) and `docs/RUN-V23-fee-range.md`.

## 7. Frontend files to change (`E:\JAVA\edu-wt\fee-range-ui`)

- `src/api/tuition.ts`: `previewBillableSessions()` and `generateRangeInvoices()`; remove `generateMonthlyInvoices`.
- `src/api/types.ts` and `src/api/finance/billing.types.ts`: request/response/preview/conflict types, and the slip `periodFrom/periodTo/periodText`.
- `src/jsx/components/Finance/fees/GenerateMonthlyModal.tsx` becomes `GenerateRangeModal.tsx`:
  - two date pickers (Từ ngày / Đến ngày), defaulting to the first day of the current month through today,
  - the "Xem trước" button calls the preview,
  - a table of students with dd/MM badges, the count, the amount, and conflicts highlighted,
  - "Tạo phiếu" is disabled while there are conflicts,
  - the 409 response is shown with its dates.
- `src/jsx/components/Finance/fees/TuitionFeesPage.tsx`: button label and modal wiring. Show the fee list's period column as dd/MM/yyyy–dd/MM/yyyy (legacy: the month).
- `src/jsx/components/Finance/TuitionSlipCard.tsx` and `fees/FeeSlipModal.tsx`: show "Từ dd/MM/yyyy đến dd/MM/yyyy" and the dd/MM badges, with no "(muộn)".
- `src/i18n/locales.ts` and `src/i18n/finance/billing.ts`: vi and en keys (e.g. `edu.fee.generateRange` = "Tạo phiếu theo khoảng ngày", `fromDate`, `toDate`, `preview`, `conflictDates`).
- Tests: `src/api/tuition.test.ts`, `TuitionSlipCard.test.tsx`, `TuitionFeesPage.test.tsx`, and a new `GenerateRangeModal.test.tsx`.

## 8. Test expectations

**Backend (JUnit, H2 / Mockito as existing):**

1. A range counts only PRESENT and LATE, excludes ABSENT/EXCUSED/others and soft-deleted attendance, and both ends are inclusive.
2. Amount = count × price, scale 2; v1.1: discount = 0. `PUT` on a range fee (`IS_RANGE = 1`) forces discount 0; on legacy/manual fees (`IS_RANGE = 0`) a given `discountAmount` is used and a null one keeps the existing discount.
3. A conflict with an active fee gives 409 listing the exact dates and fee codes, and nothing is persisted for any student (all-or-nothing).
4. A conflict with a CANCELLED or deleted fee is not a conflict, because the rows were released on cancel.
5. A unique-constraint violation maps to 409.
6. Cancelling a fee deletes its session rows, and the same dates can then be billed again.
7. Two disjoint ranges in the same month for the same student and class both succeed (UQ_FEES_MONTHLY not hit, `FEE_MONTH`/`FEE_YEAR` NULL).
8. Validation: from > to, range > 366 days, and a missing price all give 400.
9. Slip: `periodText` is `"Từ 01/10/2026 đến 31/10/2026"`, the badges are sorted dd/MM, and no "(muộn)" appears even for LATE. Legacy fees still render "Tháng MM/yyyy".
10. VietQR: `HS000123 HP 0110-3110`. Every output is ≤ 25 characters (a property test with a long code), the dates are never cut, and there is no `/`.
11. Preview is read-only (no writes) and reports conflicts.
12. The `generate-monthly` endpoint is gone (404/405).
13. Migration script test (§4).
14. The whole existing suite stays green (`mvn test`).

**Frontend (Vitest):**

- The modal calls the preview with the correct ISO dates.
- Conflicts disable submit.
- The 409 shows the dates.
- The slip renders the period line and the badges.
- There is no month selector anymore.
- `npm run test`, `npm run lint` and `npm run build` all pass.

**QA (TEST bot + owner), v1.1: on `EDUCATION`, dedicated test class only:**

- Use a dedicated test class (test students, test attendance in October). Never create trial fees on real classes.
- Preview, then generate.
- Print the slip and check the dates against attendance.
- Generate again for an overlapping range and expect the conflict.
- Cancel and regenerate, which should succeed.
- Check the VietQR content on the slip (no real payment).
- Cancel/delete all test fees of the test class afterwards.

## 9. Environment / safety

- v1.1: the target is the real schema **`EDUCATION`** (no PRE). Integration tests, if any, use the `it` profile (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD` from **environment variables only**) and only a dedicated test class. Never write credentials into yml, scripts, docs, logs or commits. Never use SYS.
- Do not touch `E:\JAVA\education`, `E:\JAVA\education_ui` or `E:\JAVA\edu-wt\main-ui` (no checkout or commit there).
- The owner runs the migrations himself. Bots do not run DDL or DML.
- Commit locally on the feature branches. No push, merge or deploy before the owner approves.

## 10. Rủi ro đã biết (v1.1)

- **No audit** for range generation: the old generate had none and `TuitionSlipService` is not covered by `AuditTrailAspect`. Only application logs exist (class, range, counts, actor).
- **VietQR reconciliation is manual**, by transfer content + amount. A student code longer than 12 characters is truncated, and multiple matches need manual reconciliation. There is no automatic reconciliation in the backend.
- **Conflicts are detected by attendance ID.** An attendance row added or changed later inside an old slip's range is not treated as billed and can be billed on a new slip.
- After COMMIT, **V23_8 cannot be rolled back by script**; restore from the Data Pump export taken before V23_1 (§0 V11).
- **Oracle reserved words:** V23_9 first used the alias `SHARE` (ORA-00923, procedure INVALID on EDUCATION, 29/10/2026); renamed to `FEE_SHARE`. `V23FeeRangeScriptTest` now fails if any alias or identifier in V23_1..V23_9 is an Oracle reserved word. V23_9 can be re-run alone (`CREATE OR REPLACE`, only when no V23_7/V23_8 transaction is open); verify with `SHOW ERRORS` and `USER_OBJECTS.STATUS = 'VALID'`.
