-- =============================================================================
-- EDUCATION - MIGRATION V14_3: TAI CHINH - BAO CAO, DASHBOARD, XUAT EXCEL
--
-- Stream C (feat/fin-reports). CHI Stream C sua file nay; stream khac khong sua.
--
-- Pham vi (Stream C) - CHI procedure / menu, KHONG doi bang, KHONG ghi bang nghiep vu:
--   * Sua PRC_RPT_DASHBOARD_METRICS (B7): giu nguyen tham so va cac cot cu, them cot moi (chi tiet ben duoi).
--   * Procedure moi (chi SELECT FIN_TUITION_FEES / FIN_PAYMENT_TRANSACTIONS / EDU_STUDENTS / EDU_CLASSES):
--       PRC_RPT_FINANCE_SUMMARY, PRC_RPT_DEBT_AGING, PRC_RPT_CLASS_COLLECTION, PRC_RPT_STUDENT_LEDGER.
--   * Menu MENU_FINANCE_DASHBOARD (/finance/dashboard) va MENU_FINANCE_REPORT (/finance/reports) duoi DIR_FINANCE.
--
-- DINH NGHIA SO LIEU (dung chung cho moi procedure trong file nay):
--   * Khoan phi hop le: FIN_TUITION_FEES.IS_DELETED = 0. Khoan CANCELLED khong tinh vao phai thu / da lap.
--   * Khoan con mo (open): STATUS IN ('UNPAID', 'PARTIAL', 'OVERDUE').
--   * Con phai thu cua mot khoan: GREATEST(TOTAL_AMOUNT - DISCOUNT_AMOUNT - PAID_AMOUNT, 0).
--   * Qua han (OVERDUE, B7): STATUS = 'OVERDUE'
--                        OR (STATUS IN ('UNPAID', 'PARTIAL') AND DUE_DATE < TRUNC(SYSDATE)),
--     loai IS_DELETED = 1 va CANCELLED. Tinh theo ngay hien tai (khong theo khoang loc).
--   * Ky cua khoan phi khi loc theo khoang ngay (dashboard, tong hop): theo DUE_DATE;
--     khoan khong co DUE_DATE thi lay TRUNC(CREATED_AT). Khoan thuoc khoang neu ngay do nam trong [tu ngay, den ngay].
--   * Ky cua khoan phi khi loc theo nam / thang (thu tien theo lop): FEE_YEAR / FEE_MONTH (phieu thang, V10);
--     khoan khong co FEE_YEAR / FEE_MONTH thi lay nam / thang cua NVL(DUE_DATE, TRUNC(CREATED_AT)).
--   * Thuc thu (theo ngay tien vao / ra, loc theo TRUNC(PAYMENT_DATE)), cung quy tac PAID_AMOUNT cua V14_2:
--       + AMOUNT cua dong TRANSACTION_TYPE = 'PAYMENT' co STATUS IN ('SUCCESS', 'REFUNDED')
--       - AMOUNT cua dong TRANSACTION_TYPE = 'REFUND'  co STATUS = 'SUCCESS'   (ngay hoan = PAYMENT_DATE cua dong REFUND)
--     IS_DELETED = 0. Dong 'VOIDED' / 'PENDING' / 'FAILED' KHONG tinh. Thang chi co hoan tien co the am.
--     Da hoan (TOTAL_REFUNDED / REFUNDED_AMOUNT) = tong AMOUNT dong REFUND 'SUCCESS' trong khoang.
--     So giao dich (TRANSACTION_COUNT) = so dong PAYMENT duoc tinh (khong dem dong REFUND).
--
-- PRC_RPT_DASHBOARD_METRICS (B7):
--   * OVERDUE_FEES  : dem theo dinh nghia qua han o tren (truoc day bo sot STATUS = 'OVERDUE').
--   * TOTAL_RECEIVABLE: DOI NGHIA - truoc la SUM(TOTAL - DISCOUNT) moi khoan chua huy tu truoc toi nay;
--     nay la SUM(con phai thu) cua cac khoan con mo co ky (DUE_DATE) trong khoang loc.
--   * Cot MOI: TOTAL_BILLED = SUM(TOTAL - DISCOUNT) cac khoan chua huy co ky trong khoang loc;
--             OVERDUE_AMOUNT = SUM(con phai thu) cua cac khoan qua han.
--   * TOTAL_COLLECTED / O_REVENUE_CURSOR: thuc thu SAU hoan tien theo dinh nghia o tren (truoc: SUM STATUS = 'SUCCESS',
--     cong nham dong REFUND va bo sot thu da hoan toan bo). Cac chi so hoc sinh / lop / lead giu nguyen.
--
-- Tuoi no (PRC_RPT_DEBT_AGING), so ngay qua han = ngay chot - TRUNC(DUE_DATE):
--   NOT_DUE (chua toi han hoac khong co DUE_DATE), D0_30 (1-30 ngay), D31_60, D61_90, D90_PLUS (> 90 ngay).
--   Gom ca hoc sinh khong con ACTIVE / da xoa mem (B8 - phia bao cao). So du lay theo PAID_AMOUNT hien tai.
--
-- QUY UOC CHUNG CHO V14_x (kiem tra tu dong boi V14ScriptConventionTest):
--   * WHENEVER SQLERROR EXIT ... ROLLBACK truoc lenh dau tien; script ket thuc bang COMMIT + EXIT.
--   * Chay lai nhieu lan an toan (idempotent): procedure dung CREATE OR REPLACE, seed dung MERGE.
--   * Menu: MERGE ON (MENU_CODE) va ID = SEQ_SYS_MENUS.NEXTVAL - KHONG dung ID co dinh.
--     Chuc nang: SEQ_SYS_FUNCTIONS.NEXTVAL; phan quyen: SEQ_SYS_ROLE_MENU_PERM.NEXTVAL.
--   * ROLE_ADMIN duoc cap moi chuc nang cua menu do script nay tao; vai tro khac liet ke tuong minh.
--   * File UTF-8 co dau tieng Viet: chay voi NLS_LANG=AMERICAN_AMERICA.AL32UTF8.
--   * Thu tu chay: V14_1 -> V14_2 -> V14_3 (sau V13_3).
--   * PHU THUOC V14_2: cac procedure doc FIN_PAYMENT_TRANSACTIONS.TRANSACTION_TYPE / RECEIPT_NO va ma 'VOIDED'.
--     Muc V14_3.0 dung script (khong thay doi gi) neu chua chay V14_2. Khong phu thuoc doi tuong cua V14_1.
--   * Sau phan procedure, kiem tra 5 procedure o trang thai VALID (loi bien dich -> dung script, chua seed menu).
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V14_3__fin_reports.sql
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V14_3.0 Kiem tra phu thuoc V14_2 ============

DECLARE
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'FIN_PAYMENT_TRANSACTIONS'
       AND COLUMN_NAME IN ('TRANSACTION_TYPE', 'RECEIPT_NO');
    IF V_COUNT < 2 THEN
        RAISE_APPLICATION_ERROR(-20002,
            'V14_3: chua co FIN_PAYMENT_TRANSACTIONS.TRANSACTION_TYPE / RECEIPT_NO - chay V14_2__fin_payments.sql truoc.');
    END IF;
    DBMS_OUTPUT.PUT_LINE('V14_2 columns: OK');
END;
/

PROMPT ============ V14_3.1 Procedure bao cao ============

-- ----------------------------------------------------------------------------
-- PRC_RPT_DASHBOARD_METRICS - Bao cao & Thong ke (sua B7, xem header)
--     O_SUMMARY_CURSOR: 1 dong cac chi so tong hop
--     O_REVENUE_CURSOR: doanh thu thuc thu theo thang trong khoang loc
-- ----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE PRC_RPT_DASHBOARD_METRICS(
    P_FROM_DATE      IN  DATE,
    P_TO_DATE        IN  DATE,
    O_SUMMARY_CURSOR OUT SYS_REFCURSOR,
    O_REVENUE_CURSOR OUT SYS_REFCURSOR,
    O_ERR_CODE       OUT VARCHAR2,
    O_ERR_MSG        OUT VARCHAR2
) AS
    V_FROM  DATE;
    V_TO    DATE;
    V_TODAY DATE := TRUNC(SYSDATE);
BEGIN
    -- Mac dinh 12 thang gan nhat neu client khong truyen khoang thoi gian
    V_FROM := NVL(TRUNC(P_FROM_DATE), ADD_MONTHS(TRUNC(SYSDATE, 'MM'), -11));
    V_TO   := NVL(TRUNC(P_TO_DATE), TRUNC(SYSDATE));

    IF V_TO < V_FROM THEN
        O_ERR_CODE := 'INVALID_DATE_RANGE';
        O_ERR_MSG  := 'Tu ngay phai nho hon hoac bang den ngay.';
        RETURN;
    END IF;

    OPEN O_SUMMARY_CURSOR FOR
        WITH FEES AS (
            SELECT f.STATUS,
                   f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT                             AS NET_AMOUNT,
                   GREATEST(f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT - f.PAID_AMOUNT, 0) AS REMAINING_AMOUNT,
                   NVL(TRUNC(f.DUE_DATE), TRUNC(f.CREATED_AT))                    AS PERIOD_DATE,
                   CASE
                       WHEN f.STATUS = 'OVERDUE' THEN 1
                       WHEN f.STATUS IN ('UNPAID', 'PARTIAL') AND f.DUE_DATE < V_TODAY THEN 1
                       ELSE 0
                   END                                                            AS IS_OVERDUE
              FROM FIN_TUITION_FEES f
             WHERE f.IS_DELETED = 0
               AND f.STATUS <> 'CANCELLED'
        ),
        FEE_AGG AS (
            SELECT NVL(SUM(CASE WHEN STATUS IN ('UNPAID', 'PARTIAL', 'OVERDUE')
                                 AND PERIOD_DATE BETWEEN V_FROM AND V_TO
                                THEN REMAINING_AMOUNT END), 0)                    AS TOTAL_RECEIVABLE,
                   NVL(SUM(CASE WHEN PERIOD_DATE BETWEEN V_FROM AND V_TO
                                THEN NET_AMOUNT END), 0)                          AS TOTAL_BILLED,
                   COUNT(CASE WHEN IS_OVERDUE = 1 THEN 1 END)                     AS OVERDUE_FEES,
                   NVL(SUM(CASE WHEN IS_OVERDUE = 1 THEN REMAINING_AMOUNT END), 0) AS OVERDUE_AMOUNT
              FROM FEES
        )
        SELECT
            (SELECT COUNT(*) FROM EDU_STUDENTS WHERE IS_DELETED = 0)                        AS TOTAL_STUDENTS,
            (SELECT COUNT(*) FROM EDU_STUDENTS WHERE IS_DELETED = 0 AND STATUS = 'ACTIVE')  AS ACTIVE_STUDENTS,
            (SELECT COUNT(*) FROM EDU_CLASSES  WHERE IS_DELETED = 0)                        AS TOTAL_CLASSES,
            (SELECT COUNT(*) FROM EDU_CLASSES  WHERE IS_DELETED = 0
                                                AND STATUS IN ('OPEN', 'ONGOING'))          AS ACTIVE_CLASSES,
            (SELECT COUNT(*) FROM EDU_LEADS    WHERE IS_DELETED = 0
                                                AND TRUNC(CREATED_AT) BETWEEN V_FROM AND V_TO) AS NEW_LEADS,
            (SELECT COUNT(*) FROM EDU_LEADS    WHERE IS_DELETED = 0
                                                AND STATUS = 'CONVERTED'
                                                AND TRUNC(UPDATED_AT) BETWEEN V_FROM AND V_TO) AS CONVERTED_LEADS,
            a.TOTAL_RECEIVABLE,
            (SELECT NVL(SUM(CASE WHEN TRANSACTION_TYPE = 'REFUND' THEN -AMOUNT ELSE AMOUNT END), 0)
               FROM FIN_PAYMENT_TRANSACTIONS
              WHERE IS_DELETED = 0
                AND ((TRANSACTION_TYPE = 'PAYMENT' AND STATUS IN ('SUCCESS', 'REFUNDED'))
                  OR (TRANSACTION_TYPE = 'REFUND' AND STATUS = 'SUCCESS'))
                AND TRUNC(PAYMENT_DATE) BETWEEN V_FROM AND V_TO)                            AS TOTAL_COLLECTED,
            a.OVERDUE_FEES,
            a.TOTAL_BILLED,
            a.OVERDUE_AMOUNT,
            V_FROM AS FROM_DATE,
            V_TO   AS TO_DATE
          FROM FEE_AGG a;

    OPEN O_REVENUE_CURSOR FOR
        SELECT TO_CHAR(t.PAYMENT_DATE, 'YYYY-MM')                                        AS REVENUE_MONTH,
               SUM(CASE WHEN t.TRANSACTION_TYPE = 'REFUND' THEN -t.AMOUNT ELSE t.AMOUNT END) AS COLLECTED_AMOUNT,
               COUNT(CASE WHEN t.TRANSACTION_TYPE = 'PAYMENT' THEN 1 END)                 AS TRANSACTION_COUNT
          FROM FIN_PAYMENT_TRANSACTIONS t
         WHERE t.IS_DELETED = 0
           AND ((t.TRANSACTION_TYPE = 'PAYMENT' AND t.STATUS IN ('SUCCESS', 'REFUNDED'))
             OR (t.TRANSACTION_TYPE = 'REFUND' AND t.STATUS = 'SUCCESS'))
           AND TRUNC(t.PAYMENT_DATE) BETWEEN V_FROM AND V_TO
         GROUP BY TO_CHAR(t.PAYMENT_DATE, 'YYYY-MM')
         ORDER BY 1;

    O_ERR_CODE := '0';
    O_ERR_MSG  := 'SUCCESS';
EXCEPTION
    WHEN OTHERS THEN
        IF O_SUMMARY_CURSOR IS NOT NULL AND O_SUMMARY_CURSOR%ISOPEN THEN CLOSE O_SUMMARY_CURSOR; END IF;
        IF O_REVENUE_CURSOR IS NOT NULL AND O_REVENUE_CURSOR%ISOPEN THEN CLOSE O_REVENUE_CURSOR; END IF;
        O_ERR_CODE := TO_CHAR(SQLCODE);
        O_ERR_MSG  := SUBSTR(SQLERRM, 1, 255);
END PRC_RPT_DASHBOARD_METRICS;
/
SHOW ERRORS PROCEDURE PRC_RPT_DASHBOARD_METRICS

-- ----------------------------------------------------------------------------
-- PRC_RPT_FINANCE_SUMMARY - Tong hop tai chinh trong khoang ngay
--     O_SUMMARY_CURSOR: 1 dong - da lap, mien giam, thuc thu (sau hoan), da hoan, con phai thu, qua han
--     O_STATUS_CURSOR : so khoan / so tien theo trang thai hieu luc (UNPAID/PARTIAL qua han tinh la OVERDUE)
--     O_MONTHLY_CURSOR: tung thang trong khoang (ke ca thang 0 dong) - da lap (theo ky), thuc thu, da hoan
-- ----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE PRC_RPT_FINANCE_SUMMARY(
    P_FROM_DATE      IN  DATE,
    P_TO_DATE        IN  DATE,
    O_SUMMARY_CURSOR OUT SYS_REFCURSOR,
    O_STATUS_CURSOR  OUT SYS_REFCURSOR,
    O_MONTHLY_CURSOR OUT SYS_REFCURSOR,
    O_ERR_CODE       OUT VARCHAR2,
    O_ERR_MSG        OUT VARCHAR2
) AS
    V_FROM  DATE;
    V_TO    DATE;
    V_TODAY DATE := TRUNC(SYSDATE);
BEGIN
    V_FROM := NVL(TRUNC(P_FROM_DATE), ADD_MONTHS(TRUNC(SYSDATE, 'MM'), -11));
    V_TO   := NVL(TRUNC(P_TO_DATE), TRUNC(SYSDATE));

    IF V_TO < V_FROM THEN
        O_ERR_CODE := 'INVALID_DATE_RANGE';
        O_ERR_MSG  := 'Tu ngay phai nho hon hoac bang den ngay.';
        RETURN;
    END IF;

    IF MONTHS_BETWEEN(TRUNC(V_TO, 'MM'), TRUNC(V_FROM, 'MM')) >= 120 THEN
        O_ERR_CODE := 'DATE_RANGE_TOO_LARGE';
        O_ERR_MSG  := 'Khoang thoi gian bao cao toi da 120 thang.';
        RETURN;
    END IF;

    OPEN O_SUMMARY_CURSOR FOR
        WITH FEES AS (
            SELECT f.STATUS,
                   f.TOTAL_AMOUNT,
                   f.DISCOUNT_AMOUNT,
                   f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT                             AS NET_AMOUNT,
                   GREATEST(f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT - f.PAID_AMOUNT, 0) AS REMAINING_AMOUNT,
                   CASE
                       WHEN f.STATUS = 'OVERDUE' THEN 1
                       WHEN f.STATUS IN ('UNPAID', 'PARTIAL') AND f.DUE_DATE < V_TODAY THEN 1
                       ELSE 0
                   END                                                            AS IS_OVERDUE
              FROM FIN_TUITION_FEES f
             WHERE f.IS_DELETED = 0
               AND f.STATUS <> 'CANCELLED'
               AND NVL(TRUNC(f.DUE_DATE), TRUNC(f.CREATED_AT)) BETWEEN V_FROM AND V_TO
        ),
        FEE_AGG AS (
            SELECT NVL(SUM(TOTAL_AMOUNT), 0)                                       AS TOTAL_BILLED,
                   NVL(SUM(DISCOUNT_AMOUNT), 0)                                    AS TOTAL_DISCOUNT,
                   NVL(SUM(NET_AMOUNT), 0)                                         AS NET_BILLED,
                   NVL(SUM(CASE WHEN STATUS IN ('UNPAID', 'PARTIAL', 'OVERDUE')
                                THEN REMAINING_AMOUNT END), 0)                    AS TOTAL_OUTSTANDING,
                   NVL(SUM(CASE WHEN IS_OVERDUE = 1 THEN REMAINING_AMOUNT END), 0) AS OVERDUE_AMOUNT,
                   COUNT(CASE WHEN IS_OVERDUE = 1 THEN 1 END)                     AS OVERDUE_FEES,
                   COUNT(*)                                                        AS FEE_COUNT
              FROM FEES
        ),
        TRANS_AGG AS (
            SELECT NVL(SUM(CASE WHEN t.TRANSACTION_TYPE = 'REFUND' THEN -t.AMOUNT ELSE t.AMOUNT END), 0)
                                                                          AS TOTAL_COLLECTED,
                   NVL(SUM(CASE WHEN t.TRANSACTION_TYPE = 'REFUND' THEN t.AMOUNT END), 0) AS TOTAL_REFUNDED,
                   COUNT(CASE WHEN t.TRANSACTION_TYPE = 'PAYMENT' THEN 1 END)          AS TRANSACTION_COUNT
              FROM FIN_PAYMENT_TRANSACTIONS t
             WHERE t.IS_DELETED = 0
               AND ((t.TRANSACTION_TYPE = 'PAYMENT' AND t.STATUS IN ('SUCCESS', 'REFUNDED'))
                 OR (t.TRANSACTION_TYPE = 'REFUND' AND t.STATUS = 'SUCCESS'))
               AND TRUNC(t.PAYMENT_DATE) BETWEEN V_FROM AND V_TO
        )
        SELECT a.TOTAL_BILLED,
               a.TOTAL_DISCOUNT,
               a.NET_BILLED,
               tr.TOTAL_COLLECTED,
               tr.TOTAL_REFUNDED,
               tr.TRANSACTION_COUNT,
               a.TOTAL_OUTSTANDING,
               a.OVERDUE_AMOUNT,
               a.OVERDUE_FEES,
               a.FEE_COUNT,
               V_FROM AS FROM_DATE,
               V_TO   AS TO_DATE
          FROM FEE_AGG a
         CROSS JOIN TRANS_AGG tr;

    OPEN O_STATUS_CURSOR FOR
        SELECT x.EFFECTIVE_STATUS                    AS STATUS,
               COUNT(*)                              AS FEE_COUNT,
               SUM(x.NET_AMOUNT)                     AS NET_AMOUNT,
               SUM(x.REMAINING_AMOUNT)               AS REMAINING_AMOUNT
          FROM (
                SELECT CASE
                           WHEN f.STATUS IN ('UNPAID', 'PARTIAL') AND f.DUE_DATE < V_TODAY THEN 'OVERDUE'
                           ELSE f.STATUS
                       END                                                        AS EFFECTIVE_STATUS,
                       f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT                         AS NET_AMOUNT,
                       CASE
                           WHEN f.STATUS IN ('UNPAID', 'PARTIAL', 'OVERDUE')
                           THEN GREATEST(f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT - f.PAID_AMOUNT, 0)
                           ELSE 0
                       END                                                        AS REMAINING_AMOUNT
                  FROM FIN_TUITION_FEES f
                 WHERE f.IS_DELETED = 0
                   AND NVL(TRUNC(f.DUE_DATE), TRUNC(f.CREATED_AT)) BETWEEN V_FROM AND V_TO
               ) x
         GROUP BY x.EFFECTIVE_STATUS
         ORDER BY DECODE(x.EFFECTIVE_STATUS, 'UNPAID', 1, 'PARTIAL', 2, 'OVERDUE', 3, 'PAID', 4, 'CANCELLED', 5, 9);

    OPEN O_MONTHLY_CURSOR FOR
        WITH MONTHS AS (
            SELECT ADD_MONTHS(TRUNC(V_FROM, 'MM'), LEVEL - 1) AS MONTH_START
              FROM DUAL
           CONNECT BY LEVEL <= MONTHS_BETWEEN(TRUNC(V_TO, 'MM'), TRUNC(V_FROM, 'MM')) + 1
        ),
        BILLED AS (
            SELECT TRUNC(NVL(TRUNC(f.DUE_DATE), TRUNC(f.CREATED_AT)), 'MM') AS MONTH_START,
                   SUM(f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT)                  AS BILLED_AMOUNT,
                   COUNT(*)                                                 AS FEE_COUNT
              FROM FIN_TUITION_FEES f
             WHERE f.IS_DELETED = 0
               AND f.STATUS <> 'CANCELLED'
               AND NVL(TRUNC(f.DUE_DATE), TRUNC(f.CREATED_AT)) BETWEEN V_FROM AND V_TO
             GROUP BY TRUNC(NVL(TRUNC(f.DUE_DATE), TRUNC(f.CREATED_AT)), 'MM')
        ),
        COLLECTED AS (
            SELECT TRUNC(t.PAYMENT_DATE, 'MM')                                                 AS MONTH_START,
                   SUM(CASE WHEN t.TRANSACTION_TYPE = 'REFUND' THEN -t.AMOUNT ELSE t.AMOUNT END) AS COLLECTED_AMOUNT,
                   SUM(CASE WHEN t.TRANSACTION_TYPE = 'REFUND' THEN t.AMOUNT ELSE 0 END)        AS REFUNDED_AMOUNT,
                   COUNT(CASE WHEN t.TRANSACTION_TYPE = 'PAYMENT' THEN 1 END)                  AS TRANSACTION_COUNT
              FROM FIN_PAYMENT_TRANSACTIONS t
             WHERE t.IS_DELETED = 0
               AND ((t.TRANSACTION_TYPE = 'PAYMENT' AND t.STATUS IN ('SUCCESS', 'REFUNDED'))
                 OR (t.TRANSACTION_TYPE = 'REFUND' AND t.STATUS = 'SUCCESS'))
               AND TRUNC(t.PAYMENT_DATE) BETWEEN V_FROM AND V_TO
             GROUP BY TRUNC(t.PAYMENT_DATE, 'MM')
        )
        SELECT TO_CHAR(m.MONTH_START, 'YYYY-MM') AS PERIOD_MONTH,
               NVL(b.BILLED_AMOUNT, 0)           AS BILLED_AMOUNT,
               NVL(b.FEE_COUNT, 0)               AS FEE_COUNT,
               NVL(c.COLLECTED_AMOUNT, 0)        AS COLLECTED_AMOUNT,
               NVL(c.REFUNDED_AMOUNT, 0)         AS REFUNDED_AMOUNT,
               NVL(c.TRANSACTION_COUNT, 0)       AS TRANSACTION_COUNT
          FROM MONTHS m
          LEFT JOIN BILLED b    ON b.MONTH_START = m.MONTH_START
          LEFT JOIN COLLECTED c ON c.MONTH_START = m.MONTH_START
         ORDER BY m.MONTH_START;

    O_ERR_CODE := '0';
    O_ERR_MSG  := 'SUCCESS';
EXCEPTION
    WHEN OTHERS THEN
        IF O_SUMMARY_CURSOR IS NOT NULL AND O_SUMMARY_CURSOR%ISOPEN THEN CLOSE O_SUMMARY_CURSOR; END IF;
        IF O_STATUS_CURSOR IS NOT NULL AND O_STATUS_CURSOR%ISOPEN THEN CLOSE O_STATUS_CURSOR; END IF;
        IF O_MONTHLY_CURSOR IS NOT NULL AND O_MONTHLY_CURSOR%ISOPEN THEN CLOSE O_MONTHLY_CURSOR; END IF;
        O_ERR_CODE := TO_CHAR(SQLCODE);
        O_ERR_MSG  := SUBSTR(SQLERRM, 1, 255);
END PRC_RPT_FINANCE_SUMMARY;
/
SHOW ERRORS PROCEDURE PRC_RPT_FINANCE_SUMMARY

-- ----------------------------------------------------------------------------
-- PRC_RPT_DEBT_AGING - Tuoi no tai ngay chot (mac dinh hom nay)
--     O_SUMMARY_CURSOR: 1 dong - tong con phai thu theo tung nhom tuoi no
--     O_STUDENT_CURSOR: moi hoc sinh con no 1 dong (ke ca hoc sinh khong con ACTIVE / da xoa mem)
--     O_DATA_CURSOR   : moi khoan phi con no 1 dong
-- ----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE PRC_RPT_DEBT_AGING(
    P_AS_OF_DATE     IN  DATE,
    O_SUMMARY_CURSOR OUT SYS_REFCURSOR,
    O_STUDENT_CURSOR OUT SYS_REFCURSOR,
    O_DATA_CURSOR    OUT SYS_REFCURSOR,
    O_ERR_CODE       OUT VARCHAR2,
    O_ERR_MSG        OUT VARCHAR2
) AS
    V_AS_OF DATE;
BEGIN
    V_AS_OF := NVL(TRUNC(P_AS_OF_DATE), TRUNC(SYSDATE));

    OPEN O_SUMMARY_CURSOR FOR
        WITH OPEN_FEES AS (
            SELECT f.STUDENT_ID,
                   f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT - f.PAID_AMOUNT AS REMAINING_AMOUNT,
                   CASE
                       WHEN f.DUE_DATE IS NULL OR TRUNC(f.DUE_DATE) >= V_AS_OF THEN 'NOT_DUE'
                       WHEN V_AS_OF - TRUNC(f.DUE_DATE) <= 30 THEN 'D0_30'
                       WHEN V_AS_OF - TRUNC(f.DUE_DATE) <= 60 THEN 'D31_60'
                       WHEN V_AS_OF - TRUNC(f.DUE_DATE) <= 90 THEN 'D61_90'
                       ELSE 'D90_PLUS'
                   END                                                 AS AGING_BUCKET
              FROM FIN_TUITION_FEES f
             WHERE f.IS_DELETED = 0
               AND f.STATUS IN ('UNPAID', 'PARTIAL', 'OVERDUE')
               AND f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT - f.PAID_AMOUNT > 0
               AND TRUNC(f.CREATED_AT) <= V_AS_OF
        )
        SELECT NVL(SUM(CASE WHEN AGING_BUCKET = 'NOT_DUE'  THEN REMAINING_AMOUNT END), 0) AS NOT_DUE_AMOUNT,
               NVL(SUM(CASE WHEN AGING_BUCKET = 'D0_30'    THEN REMAINING_AMOUNT END), 0) AS D0_30_AMOUNT,
               NVL(SUM(CASE WHEN AGING_BUCKET = 'D31_60'   THEN REMAINING_AMOUNT END), 0) AS D31_60_AMOUNT,
               NVL(SUM(CASE WHEN AGING_BUCKET = 'D61_90'   THEN REMAINING_AMOUNT END), 0) AS D61_90_AMOUNT,
               NVL(SUM(CASE WHEN AGING_BUCKET = 'D90_PLUS' THEN REMAINING_AMOUNT END), 0) AS D90_PLUS_AMOUNT,
               NVL(SUM(REMAINING_AMOUNT), 0)                                              AS TOTAL_OUTSTANDING,
               COUNT(*)                                                                   AS FEE_COUNT,
               COUNT(DISTINCT STUDENT_ID)                                                 AS STUDENT_COUNT,
               V_AS_OF                                                                    AS AS_OF_DATE
          FROM OPEN_FEES;

    OPEN O_STUDENT_CURSOR FOR
        WITH OPEN_FEES AS (
            SELECT f.STUDENT_ID,
                   TRUNC(f.DUE_DATE)                                   AS DUE_DATE,
                   f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT - f.PAID_AMOUNT AS REMAINING_AMOUNT,
                   CASE
                       WHEN f.DUE_DATE IS NULL THEN 0
                       ELSE GREATEST(V_AS_OF - TRUNC(f.DUE_DATE), 0)
                   END                                                 AS DAYS_PAST_DUE,
                   CASE
                       WHEN f.DUE_DATE IS NULL OR TRUNC(f.DUE_DATE) >= V_AS_OF THEN 'NOT_DUE'
                       WHEN V_AS_OF - TRUNC(f.DUE_DATE) <= 30 THEN 'D0_30'
                       WHEN V_AS_OF - TRUNC(f.DUE_DATE) <= 60 THEN 'D31_60'
                       WHEN V_AS_OF - TRUNC(f.DUE_DATE) <= 90 THEN 'D61_90'
                       ELSE 'D90_PLUS'
                   END                                                 AS AGING_BUCKET
              FROM FIN_TUITION_FEES f
             WHERE f.IS_DELETED = 0
               AND f.STATUS IN ('UNPAID', 'PARTIAL', 'OVERDUE')
               AND f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT - f.PAID_AMOUNT > 0
               AND TRUNC(f.CREATED_AT) <= V_AS_OF
        )
        SELECT o.STUDENT_ID,
               s.STUDENT_CODE,
               s.FULL_NAME                                                                  AS STUDENT_NAME,
               CASE WHEN s.IS_DELETED = 1 THEN 'DELETED' ELSE s.STATUS END                  AS STUDENT_STATUS,
               NVL(SUM(CASE WHEN o.AGING_BUCKET = 'NOT_DUE'  THEN o.REMAINING_AMOUNT END), 0) AS NOT_DUE_AMOUNT,
               NVL(SUM(CASE WHEN o.AGING_BUCKET = 'D0_30'    THEN o.REMAINING_AMOUNT END), 0) AS D0_30_AMOUNT,
               NVL(SUM(CASE WHEN o.AGING_BUCKET = 'D31_60'   THEN o.REMAINING_AMOUNT END), 0) AS D31_60_AMOUNT,
               NVL(SUM(CASE WHEN o.AGING_BUCKET = 'D61_90'   THEN o.REMAINING_AMOUNT END), 0) AS D61_90_AMOUNT,
               NVL(SUM(CASE WHEN o.AGING_BUCKET = 'D90_PLUS' THEN o.REMAINING_AMOUNT END), 0) AS D90_PLUS_AMOUNT,
               SUM(o.REMAINING_AMOUNT)                                                      AS TOTAL_OUTSTANDING,
               COUNT(*)                                                                     AS FEE_COUNT,
               MIN(o.DUE_DATE)                                                              AS OLDEST_DUE_DATE,
               MAX(o.DAYS_PAST_DUE)                                                         AS MAX_DAYS_PAST_DUE
          FROM OPEN_FEES o
          JOIN EDU_STUDENTS s ON s.ID = o.STUDENT_ID
         GROUP BY o.STUDENT_ID, s.STUDENT_CODE, s.FULL_NAME, s.STATUS, s.IS_DELETED
         ORDER BY MAX(o.DAYS_PAST_DUE) DESC, SUM(o.REMAINING_AMOUNT) DESC, s.STUDENT_CODE;

    OPEN O_DATA_CURSOR FOR
        SELECT f.ID                                                AS FEE_ID,
               f.FEE_CODE,
               f.STUDENT_ID,
               s.STUDENT_CODE,
               s.FULL_NAME                                         AS STUDENT_NAME,
               CASE WHEN s.IS_DELETED = 1 THEN 'DELETED' ELSE s.STATUS END AS STUDENT_STATUS,
               f.CLASS_ID,
               c.CLASS_CODE,
               c.CLASS_NAME,
               f.FEE_YEAR,
               f.FEE_MONTH,
               TRUNC(f.DUE_DATE)                                   AS DUE_DATE,
               f.STATUS,
               f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT                  AS NET_AMOUNT,
               f.PAID_AMOUNT,
               f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT - f.PAID_AMOUNT  AS REMAINING_AMOUNT,
               CASE
                   WHEN f.DUE_DATE IS NULL THEN 0
                   ELSE GREATEST(V_AS_OF - TRUNC(f.DUE_DATE), 0)
               END                                                 AS DAYS_PAST_DUE,
               CASE
                   WHEN f.DUE_DATE IS NULL OR TRUNC(f.DUE_DATE) >= V_AS_OF THEN 'NOT_DUE'
                   WHEN V_AS_OF - TRUNC(f.DUE_DATE) <= 30 THEN 'D0_30'
                   WHEN V_AS_OF - TRUNC(f.DUE_DATE) <= 60 THEN 'D31_60'
                   WHEN V_AS_OF - TRUNC(f.DUE_DATE) <= 90 THEN 'D61_90'
                   ELSE 'D90_PLUS'
               END                                                 AS AGING_BUCKET
          FROM FIN_TUITION_FEES f
          JOIN EDU_STUDENTS s     ON s.ID = f.STUDENT_ID
          LEFT JOIN EDU_CLASSES c ON c.ID = f.CLASS_ID
         WHERE f.IS_DELETED = 0
           AND f.STATUS IN ('UNPAID', 'PARTIAL', 'OVERDUE')
           AND f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT - f.PAID_AMOUNT > 0
           AND TRUNC(f.CREATED_AT) <= V_AS_OF
         ORDER BY DAYS_PAST_DUE DESC, s.STUDENT_CODE, f.FEE_CODE;

    O_ERR_CODE := '0';
    O_ERR_MSG  := 'SUCCESS';
EXCEPTION
    WHEN OTHERS THEN
        IF O_SUMMARY_CURSOR IS NOT NULL AND O_SUMMARY_CURSOR%ISOPEN THEN CLOSE O_SUMMARY_CURSOR; END IF;
        IF O_STUDENT_CURSOR IS NOT NULL AND O_STUDENT_CURSOR%ISOPEN THEN CLOSE O_STUDENT_CURSOR; END IF;
        IF O_DATA_CURSOR IS NOT NULL AND O_DATA_CURSOR%ISOPEN THEN CLOSE O_DATA_CURSOR; END IF;
        O_ERR_CODE := TO_CHAR(SQLCODE);
        O_ERR_MSG  := SUBSTR(SQLERRM, 1, 255);
END PRC_RPT_DEBT_AGING;
/
SHOW ERRORS PROCEDURE PRC_RPT_DEBT_AGING

-- ----------------------------------------------------------------------------
-- PRC_RPT_CLASS_COLLECTION - Thu tien theo lop cua mot nam (va thang, neu co)
--     O_DATA_CURSOR: moi lop 1 dong (khoan khong gan lop gom vao dong CLASS_ID = NULL)
--     Thuc thu = so thuc thu (thu - hoan, khong tinh VOIDED) cua cac khoan thuoc ky (moi ngay thanh toan).
-- ----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE PRC_RPT_CLASS_COLLECTION(
    P_YEAR        IN  NUMBER,
    P_MONTH       IN  NUMBER,
    O_DATA_CURSOR OUT SYS_REFCURSOR,
    O_ERR_CODE    OUT VARCHAR2,
    O_ERR_MSG     OUT VARCHAR2
) AS
    V_YEAR NUMBER;
BEGIN
    V_YEAR := NVL(P_YEAR, EXTRACT(YEAR FROM SYSDATE));

    IF V_YEAR NOT BETWEEN 2000 AND 2100 THEN
        O_ERR_CODE := 'INVALID_YEAR';
        O_ERR_MSG  := 'Nam phai trong khoang 2000 - 2100.';
        RETURN;
    END IF;

    IF P_MONTH IS NOT NULL AND P_MONTH NOT BETWEEN 1 AND 12 THEN
        O_ERR_CODE := 'INVALID_MONTH';
        O_ERR_MSG  := 'Thang phai trong khoang 1 - 12.';
        RETURN;
    END IF;

    OPEN O_DATA_CURSOR FOR
        WITH FEES AS (
            SELECT f.ID,
                   f.STUDENT_ID,
                   f.CLASS_ID,
                   f.STATUS,
                   f.TOTAL_AMOUNT,
                   f.DISCOUNT_AMOUNT,
                   GREATEST(f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT - f.PAID_AMOUNT, 0) AS REMAINING_AMOUNT,
                   CASE
                       WHEN f.FEE_YEAR IS NOT NULL AND f.FEE_MONTH IS NOT NULL THEN f.FEE_YEAR
                       ELSE EXTRACT(YEAR FROM NVL(TRUNC(f.DUE_DATE), TRUNC(f.CREATED_AT)))
                   END                                                            AS PERIOD_YEAR,
                   CASE
                       WHEN f.FEE_YEAR IS NOT NULL AND f.FEE_MONTH IS NOT NULL THEN f.FEE_MONTH
                       ELSE EXTRACT(MONTH FROM NVL(TRUNC(f.DUE_DATE), TRUNC(f.CREATED_AT)))
                   END                                                            AS PERIOD_MONTH
              FROM FIN_TUITION_FEES f
             WHERE f.IS_DELETED = 0
               AND f.STATUS <> 'CANCELLED'
        ),
        PAYMENTS AS (
            SELECT t.TUITION_FEE_ID,
                   SUM(CASE WHEN t.TRANSACTION_TYPE = 'REFUND' THEN -t.AMOUNT ELSE t.AMOUNT END) AS COLLECTED_AMOUNT
              FROM FIN_PAYMENT_TRANSACTIONS t
             WHERE t.IS_DELETED = 0
               AND ((t.TRANSACTION_TYPE = 'PAYMENT' AND t.STATUS IN ('SUCCESS', 'REFUNDED'))
                 OR (t.TRANSACTION_TYPE = 'REFUND' AND t.STATUS = 'SUCCESS'))
             GROUP BY t.TUITION_FEE_ID
        )
        SELECT f.CLASS_ID,
               c.CLASS_CODE,
               c.CLASS_NAME,
               c.STATUS                                                AS CLASS_STATUS,
               COUNT(*)                                                AS FEE_COUNT,
               COUNT(DISTINCT f.STUDENT_ID)                            AS STUDENT_COUNT,
               SUM(f.TOTAL_AMOUNT)                                     AS BILLED_AMOUNT,
               SUM(f.DISCOUNT_AMOUNT)                                  AS DISCOUNT_AMOUNT,
               SUM(f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT)                 AS NET_AMOUNT,
               NVL(SUM(p.COLLECTED_AMOUNT), 0)                         AS COLLECTED_AMOUNT,
               SUM(CASE WHEN f.STATUS IN ('UNPAID', 'PARTIAL', 'OVERDUE')
                        THEN f.REMAINING_AMOUNT ELSE 0 END)            AS OUTSTANDING_AMOUNT,
               CASE
                   WHEN SUM(f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT) > 0
                   THEN ROUND(NVL(SUM(p.COLLECTED_AMOUNT), 0) * 100
                              / SUM(f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT), 2)
               END                                                     AS COLLECTION_RATE
          FROM FEES f
          LEFT JOIN PAYMENTS p    ON p.TUITION_FEE_ID = f.ID
          LEFT JOIN EDU_CLASSES c ON c.ID = f.CLASS_ID
         WHERE f.PERIOD_YEAR = V_YEAR
           AND (P_MONTH IS NULL OR f.PERIOD_MONTH = P_MONTH)
         GROUP BY f.CLASS_ID, c.CLASS_CODE, c.CLASS_NAME, c.STATUS
         ORDER BY c.CLASS_CODE NULLS LAST;

    O_ERR_CODE := '0';
    O_ERR_MSG  := 'SUCCESS';
EXCEPTION
    WHEN OTHERS THEN
        IF O_DATA_CURSOR IS NOT NULL AND O_DATA_CURSOR%ISOPEN THEN CLOSE O_DATA_CURSOR; END IF;
        O_ERR_CODE := TO_CHAR(SQLCODE);
        O_ERR_MSG  := SUBSTR(SQLERRM, 1, 255);
END PRC_RPT_CLASS_COLLECTION;
/
SHOW ERRORS PROCEDURE PRC_RPT_CLASS_COLLECTION

-- ----------------------------------------------------------------------------
-- PRC_RPT_STUDENT_LEDGER - So cong no cua mot hoc sinh
--     O_STUDENT_CURSOR: 1 dong thong tin hoc sinh (ke ca khong con ACTIVE / da xoa mem)
--     O_DATA_CURSOR   : theo thoi gian, kem so du luy ke (no - co):
--                       FEE     - khoan phi: ghi no = TOTAL - DISCOUNT, theo CREATED_AT; khoan CANCELLED ghi no 0;
--                       PAYMENT - thu tien SUCCESS / REFUNDED: ghi co = AMOUNT, theo PAYMENT_DATE;
--                       REFUND  - hoan tien SUCCESS: ghi NO = AMOUNT (tien tra lai lam tang so con no), theo PAYMENT_DATE.
--                       Giao dich VOIDED / PENDING / FAILED khong xuat hien. RECEIPT_NO: so phieu thu / phieu chi.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE PRC_RPT_STUDENT_LEDGER(
    P_STUDENT_ID     IN  NUMBER,
    O_STUDENT_CURSOR OUT SYS_REFCURSOR,
    O_DATA_CURSOR    OUT SYS_REFCURSOR,
    O_ERR_CODE       OUT VARCHAR2,
    O_ERR_MSG        OUT VARCHAR2
) AS
    V_EXISTS NUMBER;
BEGIN
    IF P_STUDENT_ID IS NULL THEN
        O_ERR_CODE := 'STUDENT_ID_REQUIRED';
        O_ERR_MSG  := 'Thieu ID hoc sinh.';
        RETURN;
    END IF;

    SELECT COUNT(*) INTO V_EXISTS FROM EDU_STUDENTS WHERE ID = P_STUDENT_ID;
    IF V_EXISTS = 0 THEN
        O_ERR_CODE := 'STUDENT_NOT_FOUND';
        O_ERR_MSG  := 'Khong tim thay hoc sinh ID: ' || P_STUDENT_ID;
        RETURN;
    END IF;

    OPEN O_STUDENT_CURSOR FOR
        SELECT s.ID,
               s.STUDENT_CODE,
               s.FULL_NAME,
               CASE WHEN s.IS_DELETED = 1 THEN 'DELETED' ELSE s.STATUS END AS STUDENT_STATUS
          FROM EDU_STUDENTS s
         WHERE s.ID = P_STUDENT_ID;

    OPEN O_DATA_CURSOR FOR
        WITH ENTRIES AS (
            SELECT 'FEE'                                       AS ENTRY_TYPE,
                   f.CREATED_AT                                AS ENTRY_DATE,
                   1                                           AS SORT_SEQ,
                   f.ID                                        AS REF_ID,
                   f.FEE_CODE                                  AS REF_CODE,
                   f.ID                                        AS FEE_ID,
                   f.FEE_CODE,
                   f.FEE_YEAR,
                   f.FEE_MONTH,
                   TRUNC(f.DUE_DATE)                           AS DUE_DATE,
                   c.CLASS_CODE,
                   c.CLASS_NAME,
                   CAST(NULL AS VARCHAR2(20))                  AS PAYMENT_METHOD,
                   CAST(NULL AS VARCHAR2(30))                  AS RECEIPT_NO,
                   f.STATUS,
                   f.NOTE,
                   CASE WHEN f.STATUS = 'CANCELLED' THEN 0
                        ELSE f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT END AS DEBIT_AMOUNT,
                   0                                           AS CREDIT_AMOUNT
              FROM FIN_TUITION_FEES f
              LEFT JOIN EDU_CLASSES c ON c.ID = f.CLASS_ID
             WHERE f.STUDENT_ID = P_STUDENT_ID
               AND f.IS_DELETED = 0
            UNION ALL
            SELECT CASE WHEN t.TRANSACTION_TYPE = 'REFUND' THEN 'REFUND' ELSE 'PAYMENT' END,
                   t.PAYMENT_DATE,
                   2,
                   t.ID,
                   t.TRANSACTION_CODE,
                   f.ID,
                   f.FEE_CODE,
                   f.FEE_YEAR,
                   f.FEE_MONTH,
                   TRUNC(f.DUE_DATE),
                   c.CLASS_CODE,
                   c.CLASS_NAME,
                   t.PAYMENT_METHOD,
                   t.RECEIPT_NO,
                   t.STATUS,
                   t.NOTE,
                   CASE WHEN t.TRANSACTION_TYPE = 'REFUND' THEN t.AMOUNT ELSE 0 END,
                   CASE WHEN t.TRANSACTION_TYPE = 'REFUND' THEN 0 ELSE t.AMOUNT END
              FROM FIN_PAYMENT_TRANSACTIONS t
              JOIN FIN_TUITION_FEES f ON f.ID = t.TUITION_FEE_ID
              LEFT JOIN EDU_CLASSES c ON c.ID = f.CLASS_ID
             WHERE f.STUDENT_ID = P_STUDENT_ID
               AND f.IS_DELETED = 0
               AND t.IS_DELETED = 0
               AND ((t.TRANSACTION_TYPE = 'PAYMENT' AND t.STATUS IN ('SUCCESS', 'REFUNDED'))
                 OR (t.TRANSACTION_TYPE = 'REFUND' AND t.STATUS = 'SUCCESS'))
        )
        SELECT e.ENTRY_TYPE,
               e.ENTRY_DATE,
               e.REF_ID,
               e.REF_CODE,
               e.FEE_ID,
               e.FEE_CODE,
               e.FEE_YEAR,
               e.FEE_MONTH,
               e.DUE_DATE,
               e.CLASS_CODE,
               e.CLASS_NAME,
               e.PAYMENT_METHOD,
               e.RECEIPT_NO,
               e.STATUS,
               e.NOTE,
               e.DEBIT_AMOUNT,
               e.CREDIT_AMOUNT,
               SUM(e.DEBIT_AMOUNT - e.CREDIT_AMOUNT)
                   OVER (ORDER BY e.ENTRY_DATE, e.SORT_SEQ, e.REF_ID
                         ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS BALANCE
          FROM ENTRIES e
         ORDER BY e.ENTRY_DATE, e.SORT_SEQ, e.REF_ID;

    O_ERR_CODE := '0';
    O_ERR_MSG  := 'SUCCESS';
EXCEPTION
    WHEN OTHERS THEN
        IF O_STUDENT_CURSOR IS NOT NULL AND O_STUDENT_CURSOR%ISOPEN THEN CLOSE O_STUDENT_CURSOR; END IF;
        IF O_DATA_CURSOR IS NOT NULL AND O_DATA_CURSOR%ISOPEN THEN CLOSE O_DATA_CURSOR; END IF;
        O_ERR_CODE := TO_CHAR(SQLCODE);
        O_ERR_MSG  := SUBSTR(SQLERRM, 1, 255);
END PRC_RPT_STUDENT_LEDGER;
/
SHOW ERRORS PROCEDURE PRC_RPT_STUDENT_LEDGER

PROMPT ============ V14_3.2 Kiem tra procedure ============

-- CREATE OR REPLACE PROCEDURE loi bien dich chi bao "Warning" (WHENEVER SQLERROR khong bat), nen kiem tra tuong minh.
DECLARE
    V_INVALID VARCHAR2(4000);
BEGIN
    FOR r IN (SELECT n.OBJECT_NAME, NVL(o.STATUS, 'MISSING') STATUS
                FROM (SELECT 'PRC_RPT_DASHBOARD_METRICS' OBJECT_NAME FROM DUAL UNION ALL
                      SELECT 'PRC_RPT_FINANCE_SUMMARY' FROM DUAL UNION ALL
                      SELECT 'PRC_RPT_DEBT_AGING' FROM DUAL UNION ALL
                      SELECT 'PRC_RPT_CLASS_COLLECTION' FROM DUAL UNION ALL
                      SELECT 'PRC_RPT_STUDENT_LEDGER' FROM DUAL) n
                LEFT JOIN USER_OBJECTS o ON o.OBJECT_NAME = n.OBJECT_NAME AND o.OBJECT_TYPE = 'PROCEDURE'
               ORDER BY n.OBJECT_NAME) LOOP
        DBMS_OUTPUT.PUT_LINE('  ' || RPAD(r.OBJECT_NAME, 28) || r.STATUS);
        IF r.STATUS <> 'VALID' THEN
            V_INVALID := V_INVALID || ' ' || r.OBJECT_NAME;
        END IF;
    END LOOP;
    IF V_INVALID IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20001, 'V14_3: procedure khong hop le:' || V_INVALID);
    END IF;
END;
/

PROMPT ============ V14_3.3 Menu bao cao tai chinh ============

MERGE INTO SYS_MENUS t
USING (
    SELECT x.MENU_CODE, x.MENU_NAME, x.PATH, x.ICON, x.SORT_ORDER, p.ID PARENT_ID
      FROM (
            SELECT 'MENU_FINANCE_DASHBOARD' MENU_CODE, 'Tổng quan tài chính' MENU_NAME, '/finance/dashboard' PATH,
                   'gauge' ICON, 0 SORT_ORDER FROM DUAL UNION ALL
            SELECT 'MENU_FINANCE_REPORT', 'Báo cáo tài chính', '/finance/reports', 'chart-line', 5 FROM DUAL
           ) x
      JOIN SYS_MENUS p ON p.MENU_CODE = 'DIR_FINANCE'
) s ON (t.MENU_CODE = s.MENU_CODE)
WHEN MATCHED THEN UPDATE SET t.PARENT_ID = s.PARENT_ID, t.MENU_NAME = s.MENU_NAME, t.PATH = s.PATH,
                             t.ICON = s.ICON, t.SORT_ORDER = s.SORT_ORDER, t.MENU_TYPE = 'MENU',
                             t.IS_HIDDEN = 0, t.IS_DELETED = 0, t.STATUS = 'ACTIVE',
                             t.UPDATED_AT = SYSTIMESTAMP, t.UPDATED_BY = 'V14_3_MIGRATION'
WHEN NOT MATCHED THEN INSERT (ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, PATH, ICON, SORT_ORDER,
                              IS_HIDDEN, STATUS, IS_DELETED, CREATED_BY)
                      VALUES (SEQ_SYS_MENUS.NEXTVAL, s.PARENT_ID, s.MENU_CODE, s.MENU_NAME, 'MENU', s.PATH, s.ICON,
                              s.SORT_ORDER, 0, 'ACTIVE', 0, 'V14_3_MIGRATION');

PROMPT ============ V14_3.4 Chuc nang ============

MERGE INTO SYS_FUNCTIONS t
USING (
    SELECT m.ID AS MENU_ID, x.FUNCTION_CODE, x.FUNCTION_NAME
      FROM SYS_MENUS m
      JOIN (
            SELECT 'MENU_FINANCE_DASHBOARD' MENU_CODE, 'VIEW' FUNCTION_CODE, 'Xem danh sách' FUNCTION_NAME FROM DUAL UNION ALL
            SELECT 'MENU_FINANCE_REPORT', 'VIEW', 'Xem danh sách' FROM DUAL UNION ALL
            SELECT 'MENU_FINANCE_REPORT', 'EXPORT', 'Xuất dữ liệu' FROM DUAL
           ) x ON x.MENU_CODE = m.MENU_CODE
     WHERE m.IS_DELETED = 0
) s ON (t.MENU_ID = s.MENU_ID AND t.FUNCTION_CODE = s.FUNCTION_CODE)
WHEN MATCHED THEN UPDATE SET t.FUNCTION_NAME = s.FUNCTION_NAME, t.IS_DELETED = 0
WHEN NOT MATCHED THEN INSERT (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
                      VALUES (SEQ_SYS_FUNCTIONS.NEXTVAL, s.MENU_ID, s.FUNCTION_CODE, s.FUNCTION_NAME, 0);

PROMPT ============ V14_3.5 Phan quyen ============

-- ROLE_ADMIN: toan quyen tren cac menu cua script nay (sinh tu SYS_FUNCTIONS, khong liet ke tay).
MERGE INTO SYS_ROLE_MENU_PERMISSIONS t
USING (
    SELECT r.ID AS ROLE_ID,
           f.MENU_ID,
           LISTAGG(f.FUNCTION_CODE, ',') WITHIN GROUP (ORDER BY f.ID) AS ALLOWED_FUNCTIONS
      FROM SYS_FUNCTIONS f
      JOIN SYS_MENUS m ON m.ID = f.MENU_ID AND m.IS_DELETED = 0
                      AND m.MENU_CODE IN ('MENU_FINANCE_DASHBOARD', 'MENU_FINANCE_REPORT')
      CROSS JOIN SYS_ROLES r
     WHERE r.ROLE_CODE = 'ROLE_ADMIN'
       AND f.IS_DELETED = 0
     GROUP BY r.ID, f.MENU_ID
) s ON (t.ROLE_ID = s.ROLE_ID AND t.MENU_ID = s.MENU_ID)
WHEN MATCHED THEN UPDATE SET t.ALLOWED_FUNCTIONS = s.ALLOWED_FUNCTIONS, t.UPDATED_AT = SYSTIMESTAMP,
                             t.UPDATED_BY = 'V14_3_MIGRATION'
WHEN NOT MATCHED THEN INSERT (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
                      VALUES (SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, s.ROLE_ID, s.MENU_ID, s.ALLOWED_FUNCTIONS, 'V14_3_MIGRATION');

-- ROLE_ACCOUNTANT: liet ke tuong minh (ALLOWED_FUNCTIONS ghi de toan bo gia tri cu cua menu do).
MERGE INTO SYS_ROLE_MENU_PERMISSIONS t
USING (
    SELECT r.ID AS ROLE_ID, m.ID AS MENU_ID, x.ALLOWED_FUNCTIONS
      FROM (
            SELECT 'ROLE_ACCOUNTANT' ROLE_CODE, 'MENU_FINANCE_DASHBOARD' MENU_CODE, 'VIEW' ALLOWED_FUNCTIONS FROM DUAL UNION ALL
            SELECT 'ROLE_ACCOUNTANT', 'MENU_FINANCE_REPORT', 'VIEW,EXPORT' FROM DUAL
           ) x
      JOIN SYS_ROLES r ON r.ROLE_CODE = x.ROLE_CODE
      JOIN SYS_MENUS m ON m.MENU_CODE = x.MENU_CODE AND m.IS_DELETED = 0
) s ON (t.ROLE_ID = s.ROLE_ID AND t.MENU_ID = s.MENU_ID)
WHEN MATCHED THEN UPDATE SET t.ALLOWED_FUNCTIONS = s.ALLOWED_FUNCTIONS, t.UPDATED_AT = SYSTIMESTAMP,
                             t.UPDATED_BY = 'V14_3_MIGRATION'
WHEN NOT MATCHED THEN INSERT (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
                      VALUES (SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, s.ROLE_ID, s.MENU_ID, s.ALLOWED_FUNCTIONS, 'V14_3_MIGRATION');

COMMIT;

PROMPT ============ V14_3 DONE ============
EXIT
