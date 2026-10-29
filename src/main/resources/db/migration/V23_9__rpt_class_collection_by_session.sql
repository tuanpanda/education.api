-- =============================================================================
-- MIGRATION V23_9__rpt_class_collection_by_session: THU HOC PHI THEO KHOANG NGAY
--
-- CREATE OR REPLACE PRC_RPT_CLASS_COLLECTION (ban V14_3) - bao cao "Thu tien theo lop" theo nam / thang:
--   * Phieu theo khoang ngay (IS_RANGE = 1, co dong FIN_TUITION_FEE_SESSIONS): chia doanh thu theo tung buoi,
--     nhom theo thang cua SESSION_DATE, moi buoi = PRICE_PER_SESSION (contract fee-range-api v1.3 muc 10).
--     Mien giam / da thu / con no cua phieu chia theo cung ty le (so tien buoi trong thang / TOTAL_AMOUNT).
--   * Phieu thang cu va khoan tao tay: giu nguyen nhu V14_3 (FEE_YEAR/FEE_MONTH, khong co thi NVL(DUE_DATE, CREATED_AT)).
--   * FEE_COUNT dem phieu khac nhau (COUNT DISTINCT) - phieu khoang ngay qua 2 thang chi dem 1 lan moi thang.
-- Cong tac 1 dong: hang C_SPLIT_BY_SESSION trong procedure. = 1: chia theo buoi (mac dinh da chot).
--   = 0: phieu khoang ngay tinh tron vao thang cua PERIOD_FROM.
-- Thu tuc PRC_GET_TUITION_SLIP_DATA (V14_1) KHONG doi: backend tu doc ngay tren phieu tu FIN_TUITION_FEE_SESSIONS.
--
-- Spec: docs/SPEC-fee-date-range.md muc 4. Huong dan chay: docs/RUN-V23-fee-range.md
-- Moi file 1 khoi lenh. Chay lai an toan (CREATE OR REPLACE).
--
-- THU TU CHAY: sau V23_8 (can cot IS_RANGE cua V23_1 va bang FIN_TUITION_FEE_SESSIONS cua V23_4).
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe /nolog
--   CONNECT <DB_USER>@//localhost:1521/<SERVICE>   (mat khau nhap tai prompt)
--   @V23_9__rpt_class_collection_by_session.sql
--
-- ROLLBACK:
--   Chay lai khoi PRC_RPT_CLASS_COLLECTION trong V14_3__fin_reports.sql.
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED

CREATE OR REPLACE PROCEDURE PRC_RPT_CLASS_COLLECTION(
    P_YEAR        IN  NUMBER,
    P_MONTH       IN  NUMBER,
    O_DATA_CURSOR OUT SYS_REFCURSOR,
    O_ERR_CODE    OUT VARCHAR2,
    O_ERR_MSG     OUT VARCHAR2
) AS
    C_SPLIT_BY_SESSION CONSTANT NUMBER := 1;
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
        WITH RANGE_FEES AS (
            SELECT f.ID
              FROM FIN_TUITION_FEES f
             WHERE f.IS_DELETED = 0
               AND f.STATUS <> 'CANCELLED'
               AND f.IS_RANGE = 1
               AND C_SPLIT_BY_SESSION = 1
               AND EXISTS (SELECT 1 FROM FIN_TUITION_FEE_SESSIONS s WHERE s.FEE_ID = f.ID)
        ),
        WHOLE_FEES AS (
            SELECT f.ID,
                   f.STUDENT_ID,
                   f.CLASS_ID,
                   f.STATUS,
                   f.TOTAL_AMOUNT,
                   f.DISCOUNT_AMOUNT,
                   GREATEST(f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT - f.PAID_AMOUNT, 0) AS REMAINING_AMOUNT,
                   1                                                              AS SHARE,
                   CASE
                       WHEN f.FEE_YEAR IS NOT NULL AND f.FEE_MONTH IS NOT NULL THEN f.FEE_YEAR
                       WHEN f.PERIOD_FROM IS NOT NULL THEN EXTRACT(YEAR FROM f.PERIOD_FROM)
                       ELSE EXTRACT(YEAR FROM NVL(TRUNC(f.DUE_DATE), TRUNC(f.CREATED_AT)))
                   END                                                            AS PERIOD_YEAR,
                   CASE
                       WHEN f.FEE_YEAR IS NOT NULL AND f.FEE_MONTH IS NOT NULL THEN f.FEE_MONTH
                       WHEN f.PERIOD_FROM IS NOT NULL THEN EXTRACT(MONTH FROM f.PERIOD_FROM)
                       ELSE EXTRACT(MONTH FROM NVL(TRUNC(f.DUE_DATE), TRUNC(f.CREATED_AT)))
                   END                                                            AS PERIOD_MONTH
              FROM FIN_TUITION_FEES f
             WHERE f.IS_DELETED = 0
               AND f.STATUS <> 'CANCELLED'
               AND f.ID NOT IN (SELECT r.ID FROM RANGE_FEES r)
        ),
        SPLIT_FEES AS (
            SELECT f.ID,
                   f.STUDENT_ID,
                   f.CLASS_ID,
                   f.STATUS,
                   COUNT(s.ID) * NVL(f.PRICE_PER_SESSION, 0)                       AS TOTAL_AMOUNT,
                   CASE WHEN f.TOTAL_AMOUNT > 0
                        THEN f.DISCOUNT_AMOUNT * COUNT(s.ID) * NVL(f.PRICE_PER_SESSION, 0) / f.TOTAL_AMOUNT
                        ELSE 0 END                                                 AS DISCOUNT_AMOUNT,
                   CASE WHEN f.TOTAL_AMOUNT > 0
                        THEN GREATEST(f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT - f.PAID_AMOUNT, 0)
                             * COUNT(s.ID) * NVL(f.PRICE_PER_SESSION, 0) / f.TOTAL_AMOUNT
                        ELSE 0 END                                                 AS REMAINING_AMOUNT,
                   CASE WHEN f.TOTAL_AMOUNT > 0
                        THEN COUNT(s.ID) * NVL(f.PRICE_PER_SESSION, 0) / f.TOTAL_AMOUNT
                        ELSE 0 END                                                 AS SHARE,
                   EXTRACT(YEAR FROM s.SESSION_DATE)                               AS PERIOD_YEAR,
                   EXTRACT(MONTH FROM s.SESSION_DATE)                              AS PERIOD_MONTH
              FROM FIN_TUITION_FEES f
              JOIN RANGE_FEES r ON r.ID = f.ID
              JOIN FIN_TUITION_FEE_SESSIONS s ON s.FEE_ID = f.ID
             GROUP BY f.ID, f.STUDENT_ID, f.CLASS_ID, f.STATUS, f.TOTAL_AMOUNT, f.DISCOUNT_AMOUNT, f.PAID_AMOUNT,
                      f.PRICE_PER_SESSION, EXTRACT(YEAR FROM s.SESSION_DATE), EXTRACT(MONTH FROM s.SESSION_DATE)
        ),
        FEES AS (
            SELECT * FROM WHOLE_FEES
            UNION ALL
            SELECT * FROM SPLIT_FEES
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
               COUNT(DISTINCT f.ID)                                    AS FEE_COUNT,
               COUNT(DISTINCT f.STUDENT_ID)                            AS STUDENT_COUNT,
               ROUND(SUM(f.TOTAL_AMOUNT), 2)                           AS BILLED_AMOUNT,
               ROUND(SUM(f.DISCOUNT_AMOUNT), 2)                        AS DISCOUNT_AMOUNT,
               ROUND(SUM(f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT), 2)       AS NET_AMOUNT,
               ROUND(NVL(SUM(p.COLLECTED_AMOUNT * f.SHARE), 0), 2)     AS COLLECTED_AMOUNT,
               ROUND(SUM(CASE WHEN f.STATUS IN ('UNPAID', 'PARTIAL', 'OVERDUE')
                              THEN f.REMAINING_AMOUNT ELSE 0 END), 2)  AS OUTSTANDING_AMOUNT,
               CASE
                   WHEN SUM(f.TOTAL_AMOUNT - f.DISCOUNT_AMOUNT) > 0
                   THEN ROUND(NVL(SUM(p.COLLECTED_AMOUNT * f.SHARE), 0) * 100
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
        IF O_DATA_CURSOR IS NOT NULL AND O_DATA_CURSOR%ISOPEN THEN
            CLOSE O_DATA_CURSOR;
        END IF;
        O_ERR_CODE := TO_CHAR(SQLCODE);
        O_ERR_MSG  := SUBSTR(SQLERRM, 1, 255);
END PRC_RPT_CLASS_COLLECTION;
/
SHOW ERRORS PROCEDURE PRC_RPT_CLASS_COLLECTION
