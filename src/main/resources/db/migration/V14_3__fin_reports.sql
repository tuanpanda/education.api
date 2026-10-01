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
--   * Thuc thu: SUM(AMOUNT) cua FIN_PAYMENT_TRANSACTIONS co STATUS = 'SUCCESS', IS_DELETED = 0, loc theo
--     TRUNC(PAYMENT_DATE). Chua xu ly TRANSACTION_TYPE = 'REFUND' / STATUS = 'VOIDED' (cot / ma cua V14_2,
--     Stream B): script nay KHONG phu thuoc V14_2; Stream C se sua lai sau khi Stream B merge.
--
-- PRC_RPT_DASHBOARD_METRICS (B7):
--   * OVERDUE_FEES  : dem theo dinh nghia qua han o tren (truoc day bo sot STATUS = 'OVERDUE').
--   * TOTAL_RECEIVABLE: DOI NGHIA - truoc la SUM(TOTAL - DISCOUNT) moi khoan chua huy tu truoc toi nay;
--     nay la SUM(con phai thu) cua cac khoan con mo co ky (DUE_DATE) trong khoang loc.
--   * Cot MOI: TOTAL_BILLED = SUM(TOTAL - DISCOUNT) cac khoan chua huy co ky trong khoang loc;
--             OVERDUE_AMOUNT = SUM(con phai thu) cua cac khoan qua han.
--   * TOTAL_COLLECTED, O_REVENUE_CURSOR va cac chi so hoc sinh / lop / lead giu nguyen.
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
--   * Thu tu chay: V14_1 -> V14_2 -> V14_3 (sau V13_3). Moi script doc lap, khong phu thuoc doi tuong
--     cua script V14 khac.
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V14_3__fin_reports.sql
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

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
            (SELECT NVL(SUM(AMOUNT), 0) FROM FIN_PAYMENT_TRANSACTIONS
              WHERE IS_DELETED = 0 AND STATUS = 'SUCCESS'
                AND TRUNC(PAYMENT_DATE) BETWEEN V_FROM AND V_TO)                            AS TOTAL_COLLECTED,
            a.OVERDUE_FEES,
            a.TOTAL_BILLED,
            a.OVERDUE_AMOUNT,
            V_FROM AS FROM_DATE,
            V_TO   AS TO_DATE
          FROM FEE_AGG a;

    OPEN O_REVENUE_CURSOR FOR
        SELECT TO_CHAR(t.PAYMENT_DATE, 'YYYY-MM') AS REVENUE_MONTH,
               SUM(t.AMOUNT)                      AS COLLECTED_AMOUNT,
               COUNT(*)                           AS TRANSACTION_COUNT
          FROM FIN_PAYMENT_TRANSACTIONS t
         WHERE t.IS_DELETED = 0
           AND t.STATUS = 'SUCCESS'
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

-- TODO(Stream C): CREATE OR REPLACE PROCEDURE PRC_RPT_FINANCE_SUMMARY / PRC_RPT_DEBT_AGING / ...

PROMPT ============ V14_3.2 Menu bao cao tai chinh ============

-- TODO(Stream C): bo comment va dieu chinh.
-- MERGE INTO SYS_MENUS t
-- USING (
--     SELECT x.MENU_CODE, x.MENU_NAME, x.PATH, x.ICON, x.SORT_ORDER, p.ID PARENT_ID
--       FROM (
--             SELECT 'MENU_FINANCE_DASHBOARD' MENU_CODE, 'Tổng quan tài chính' MENU_NAME, '/finance/dashboard' PATH,
--                    'gauge' ICON, 0 SORT_ORDER FROM DUAL UNION ALL
--             SELECT 'MENU_FINANCE_REPORT', 'Báo cáo tài chính', '/finance/reports', 'chart-line', 5 FROM DUAL
--            ) x
--       JOIN SYS_MENUS p ON p.MENU_CODE = 'DIR_FINANCE'
-- ) s ON (t.MENU_CODE = s.MENU_CODE)
-- WHEN MATCHED THEN UPDATE SET t.PARENT_ID = s.PARENT_ID, t.MENU_NAME = s.MENU_NAME, t.PATH = s.PATH,
--                              t.ICON = s.ICON, t.SORT_ORDER = s.SORT_ORDER, t.MENU_TYPE = 'MENU',
--                              t.IS_HIDDEN = 0, t.IS_DELETED = 0, t.STATUS = 'ACTIVE',
--                              t.UPDATED_AT = SYSTIMESTAMP, t.UPDATED_BY = 'V14_3_MIGRATION'
-- WHEN NOT MATCHED THEN INSERT (ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, PATH, ICON, SORT_ORDER,
--                               IS_HIDDEN, STATUS, IS_DELETED, CREATED_BY)
--                       VALUES (SEQ_SYS_MENUS.NEXTVAL, s.PARENT_ID, s.MENU_CODE, s.MENU_NAME, 'MENU', s.PATH, s.ICON,
--                               s.SORT_ORDER, 0, 'ACTIVE', 0, 'V14_3_MIGRATION');

PROMPT ============ V14_3.3 Chuc nang ============

-- MERGE INTO SYS_FUNCTIONS t
-- USING (
--     SELECT m.ID AS MENU_ID, x.FUNCTION_CODE, x.FUNCTION_NAME
--       FROM SYS_MENUS m
--       JOIN (
--             SELECT 'MENU_FINANCE_DASHBOARD' MENU_CODE, 'VIEW' FUNCTION_CODE, 'Xem danh sách' FUNCTION_NAME FROM DUAL UNION ALL
--             SELECT 'MENU_FINANCE_REPORT', 'VIEW', 'Xem danh sách' FROM DUAL UNION ALL
--             SELECT 'MENU_FINANCE_REPORT', 'EXPORT', 'Xuất dữ liệu' FROM DUAL
--            ) x ON x.MENU_CODE = m.MENU_CODE
--      WHERE m.IS_DELETED = 0
-- ) s ON (t.MENU_ID = s.MENU_ID AND t.FUNCTION_CODE = s.FUNCTION_CODE)
-- WHEN MATCHED THEN UPDATE SET t.FUNCTION_NAME = s.FUNCTION_NAME, t.IS_DELETED = 0
-- WHEN NOT MATCHED THEN INSERT (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
--                       VALUES (SEQ_SYS_FUNCTIONS.NEXTVAL, s.MENU_ID, s.FUNCTION_CODE, s.FUNCTION_NAME, 0);

PROMPT ============ V14_3.4 Phan quyen ============

-- ROLE_ADMIN: toan quyen tren cac menu cua script nay (sinh tu SYS_FUNCTIONS, khong liet ke tay).
-- MERGE INTO SYS_ROLE_MENU_PERMISSIONS t
-- USING (
--     SELECT r.ID AS ROLE_ID,
--            f.MENU_ID,
--            LISTAGG(f.FUNCTION_CODE, ',') WITHIN GROUP (ORDER BY f.ID) AS ALLOWED_FUNCTIONS
--       FROM SYS_FUNCTIONS f
--       JOIN SYS_MENUS m ON m.ID = f.MENU_ID AND m.IS_DELETED = 0
--                       AND m.MENU_CODE IN ('MENU_FINANCE_DASHBOARD', 'MENU_FINANCE_REPORT')
--       CROSS JOIN SYS_ROLES r
--      WHERE r.ROLE_CODE = 'ROLE_ADMIN'
--        AND f.IS_DELETED = 0
--      GROUP BY r.ID, f.MENU_ID
-- ) s ON (t.ROLE_ID = s.ROLE_ID AND t.MENU_ID = s.MENU_ID)
-- WHEN MATCHED THEN UPDATE SET t.ALLOWED_FUNCTIONS = s.ALLOWED_FUNCTIONS, t.UPDATED_AT = SYSTIMESTAMP,
--                              t.UPDATED_BY = 'V14_3_MIGRATION'
-- WHEN NOT MATCHED THEN INSERT (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
--                       VALUES (SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, s.ROLE_ID, s.MENU_ID, s.ALLOWED_FUNCTIONS, 'V14_3_MIGRATION');
--
-- ROLE_ACCOUNTANT: liet ke tuong minh (ALLOWED_FUNCTIONS ghi de toan bo gia tri cu cua menu do).
-- MERGE INTO SYS_ROLE_MENU_PERMISSIONS t
-- USING (
--     SELECT r.ID AS ROLE_ID, m.ID AS MENU_ID, x.ALLOWED_FUNCTIONS
--       FROM (
--             SELECT 'ROLE_ACCOUNTANT' ROLE_CODE, 'MENU_FINANCE_DASHBOARD' MENU_CODE, 'VIEW' ALLOWED_FUNCTIONS FROM DUAL UNION ALL
--             SELECT 'ROLE_ACCOUNTANT', 'MENU_FINANCE_REPORT', 'VIEW,EXPORT' FROM DUAL
--            ) x
--       JOIN SYS_ROLES r ON r.ROLE_CODE = x.ROLE_CODE
--       JOIN SYS_MENUS m ON m.MENU_CODE = x.MENU_CODE AND m.IS_DELETED = 0
-- ) s ON (t.ROLE_ID = s.ROLE_ID AND t.MENU_ID = s.MENU_ID)
-- WHEN MATCHED THEN UPDATE SET t.ALLOWED_FUNCTIONS = s.ALLOWED_FUNCTIONS, t.UPDATED_AT = SYSTIMESTAMP,
--                              t.UPDATED_BY = 'V14_3_MIGRATION'
-- WHEN NOT MATCHED THEN INSERT (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
--                       VALUES (SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, s.ROLE_ID, s.MENU_ID, s.ALLOWED_FUNCTIONS, 'V14_3_MIGRATION');

COMMIT;

PROMPT ============ V14_3 DONE ============
EXIT
