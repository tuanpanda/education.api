-- =============================================================================
-- EDUCATION - MIGRATION V16_1: CHUYEN QUYEN GEN_QR SANG MENU_TUITION_FEE
--
-- Boi canh: menu "Thu hoc phi VietQR" (MENU_TUITION_PAYMENT, /finance/vietqr) mo CUNG man hinh voi
-- "Khoan hoc phi" (MENU_TUITION_FEE, /finance/fees) va bi go bo. Nut "Tao VietQR" va API
-- /api/v1/tuition-fees/{id}/create-qr, /api/v1/payments/generate-qr chuyen sang MENU_TUITION_FEE:GEN_QR
-- (Permissions.TUITION_FEE_GEN_QR). Script nay CHI THEM, khong xoa / an gi, nen chay TRUOC khi deploy
-- backend + frontend moi (backend cu van dung MENU_TUITION_PAYMENT:GEN_QR, khong bi anh huong).
--
-- Script nay lam:
--   V16_1.1 SYS_FUNCTIONS: them GEN_QR cho MENU_TUITION_FEE (ten lay tu dong GEN_QR cu cua menu VietQR).
--   V16_1.2 SYS_ROLE_MENU_PERMISSIONS: vai tro nao co VIEW / GEN_QR tren MENU_TUITION_PAYMENT thi duoc them
--           dung ma do tren MENU_TUITION_FEE (VIEW: truoc day MENU_TUITION_PAYMENT:VIEW mo duoc danh sach
--           khoan phi; GEN_QR: nut Tao VietQR). Chi noi them ma con thieu, khong ghi de quyen da chinh;
--           vai tro chua co dong MENU_TUITION_FEE thi tao dong moi.
--   V16_1.3 Kiem tra: MENU_TUITION_FEE co GEN_QR va moi quyen cu da duoc chuyen.
--
-- Khong doi cau truc bang. Chay lai nhieu lan an toan (MERGE + dieu kien INSTR bo qua ma da co).
-- Thu tu chay: sau V15, TRUOC khi deploy; V16_2 chay SAU khi deploy.
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V16_1__move_gen_qr_to_tuition_fee.sql
--
-- ROLLBACK (chi can khi quay lai backend cu VA da chay V16_2 thi phai rollback V16_2 truoc):
--   UPDATE SYS_ROLE_MENU_PERMISSIONS
--      SET ALLOWED_FUNCTIONS = TRIM(BOTH ',' FROM REPLACE(',' || ALLOWED_FUNCTIONS || ',', ',GEN_QR,', ','))
--    WHERE MENU_ID = (SELECT ID FROM SYS_MENUS WHERE MENU_CODE = 'MENU_TUITION_FEE');
--   UPDATE SYS_FUNCTIONS SET IS_DELETED = 1, UPDATED_AT = SYSTIMESTAMP
--    WHERE FUNCTION_CODE = 'GEN_QR' AND MENU_ID = (SELECT ID FROM SYS_MENUS WHERE MENU_CODE = 'MENU_TUITION_FEE');
--   COMMIT;
--   (VIEW da them cho MENU_TUITION_FEE giu nguyen; vai tro ADMIN / ACCOUNTANT da co VIEW tu V1.)
--   Backend cu bo qua MENU_TUITION_FEE:GEN_QR nen de nguyen cung khong sao.
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V16_1.1 Chuc nang MENU_TUITION_FEE:GEN_QR ============

MERGE INTO SYS_FUNCTIONS t
USING (
    SELECT m.ID AS MENU_ID,
           'GEN_QR' AS FUNCTION_CODE,
           NVL((SELECT MAX(f.FUNCTION_NAME)
                  FROM SYS_FUNCTIONS f
                  JOIN SYS_MENUS o ON o.ID = f.MENU_ID AND o.MENU_CODE = 'MENU_TUITION_PAYMENT'
                 WHERE f.FUNCTION_CODE = 'GEN_QR'), 'Sinh ma VietQR') AS FUNCTION_NAME
      FROM SYS_MENUS m
     WHERE m.MENU_CODE = 'MENU_TUITION_FEE'
       AND m.IS_DELETED = 0
) s ON (t.MENU_ID = s.MENU_ID AND t.FUNCTION_CODE = s.FUNCTION_CODE)
WHEN MATCHED THEN UPDATE SET t.IS_DELETED = 0, t.UPDATED_AT = SYSTIMESTAMP
                       WHERE t.IS_DELETED <> 0
WHEN NOT MATCHED THEN INSERT (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
                      VALUES (SEQ_SYS_FUNCTIONS.NEXTVAL, s.MENU_ID, s.FUNCTION_CODE, s.FUNCTION_NAME, 0);

PROMPT ============ V16_1.2 Chuyen quyen VIEW / GEN_QR ============

-- Moi ma (VIEW roi GEN_QR) mot MERGE: dong MENU_TUITION_FEE tao o lan VIEW se duoc lan GEN_QR noi them.
DECLARE
    TYPE T_CODES IS TABLE OF VARCHAR2(20);
    V_CODES T_CODES := T_CODES('VIEW', 'GEN_QR');
    V_CODE  VARCHAR2(20);
BEGIN
    FOR i IN 1 .. V_CODES.COUNT LOOP
        V_CODE := V_CODES(i);
        MERGE INTO SYS_ROLE_MENU_PERMISSIONS t
        USING (
            SELECT o.ROLE_ID, fee.ID AS MENU_ID, V_CODE AS FUNCTION_CODE
              FROM SYS_ROLE_MENU_PERMISSIONS o
              JOIN SYS_MENUS old ON old.ID = o.MENU_ID AND old.MENU_CODE = 'MENU_TUITION_PAYMENT'
              JOIN SYS_MENUS fee ON fee.MENU_CODE = 'MENU_TUITION_FEE' AND fee.IS_DELETED = 0
             WHERE INSTR(',' || REPLACE(o.ALLOWED_FUNCTIONS, ' ', '') || ',', ',' || V_CODE || ',') > 0
        ) s ON (t.ROLE_ID = s.ROLE_ID AND t.MENU_ID = s.MENU_ID)
        WHEN MATCHED THEN UPDATE SET t.ALLOWED_FUNCTIONS = CASE
                                         WHEN TRIM(t.ALLOWED_FUNCTIONS) IS NULL THEN s.FUNCTION_CODE
                                         ELSE t.ALLOWED_FUNCTIONS || ',' || s.FUNCTION_CODE
                                     END,
                                     t.UPDATED_AT = SYSTIMESTAMP,
                                     t.UPDATED_BY = 'V16_1_MIGRATION'
                              WHERE INSTR(',' || REPLACE(t.ALLOWED_FUNCTIONS, ' ', '') || ',',
                                          ',' || s.FUNCTION_CODE || ',') = 0
        WHEN NOT MATCHED THEN INSERT (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
                              VALUES (SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, s.ROLE_ID, s.MENU_ID, s.FUNCTION_CODE,
                                      'V16_1_MIGRATION');
        DBMS_OUTPUT.PUT_LINE('  ' || RPAD(V_CODE, 10) || SQL%ROWCOUNT || ' dong cap nhat / them');
    END LOOP;
END;
/

PROMPT ============ V16_1.3 Kiem tra ============

DECLARE
    V_FUNC    NUMBER;
    V_MISSING NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO V_FUNC
      FROM SYS_FUNCTIONS f
      JOIN SYS_MENUS m ON m.ID = f.MENU_ID AND m.MENU_CODE = 'MENU_TUITION_FEE' AND m.IS_DELETED = 0
     WHERE f.FUNCTION_CODE = 'GEN_QR'
       AND f.IS_DELETED = 0;
    IF V_FUNC <> 1 THEN
        RAISE_APPLICATION_ERROR(-20001, 'V16_1: thieu chuc nang MENU_TUITION_FEE:GEN_QR');
    END IF;

    SELECT COUNT(*)
      INTO V_MISSING
      FROM SYS_ROLE_MENU_PERMISSIONS o
      JOIN SYS_MENUS old ON old.ID = o.MENU_ID AND old.MENU_CODE = 'MENU_TUITION_PAYMENT'
     WHERE INSTR(',' || REPLACE(o.ALLOWED_FUNCTIONS, ' ', '') || ',', ',GEN_QR,') > 0
       AND NOT EXISTS (
            SELECT 1
              FROM SYS_ROLE_MENU_PERMISSIONS n
              JOIN SYS_MENUS fee ON fee.ID = n.MENU_ID AND fee.MENU_CODE = 'MENU_TUITION_FEE'
             WHERE n.ROLE_ID = o.ROLE_ID
               AND INSTR(',' || REPLACE(n.ALLOWED_FUNCTIONS, ' ', '') || ',', ',GEN_QR,') > 0);
    IF V_MISSING > 0 THEN
        RAISE_APPLICATION_ERROR(-20002, 'V16_1: ' || V_MISSING || ' vai tro chua duoc chuyen quyen GEN_QR');
    END IF;
    DBMS_OUTPUT.PUT_LINE('  MENU_TUITION_FEE:GEN_QR                 OK');
END;
/

COMMIT;

PROMPT ============ V16_1 DONE ============
EXIT
