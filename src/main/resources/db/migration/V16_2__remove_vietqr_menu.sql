-- =============================================================================
-- EDUCATION - MIGRATION V16_2: GO MENU "THU HOC PHI VIETQR" (MENU_TUITION_PAYMENT, /finance/vietqr)
--
-- Menu nay mo CUNG man hinh voi "Khoan hoc phi" (/finance/fees); frontend moi chuyen /finance/vietqr
-- sang /finance/fees. Quyen GEN_QR da duoc V16_1 chuyen sang MENU_TUITION_FEE.
--
-- CHAY SAU KHI DEPLOY backend + frontend moi (backend moi kiem tra MENU_TUITION_FEE:GEN_QR). Chay truoc
-- deploy thi backend cu mat quyen MENU_TUITION_PAYMENT:GEN_QR -> nut "Tao VietQR" bi an / 403.
--
-- Script nay lam (cung cach MenuAdminServiceImpl.delete xoa mem mot menu):
--   V16_2.1 Kiem tra truoc: dung lai neu V16_1 chua chay (thieu MENU_TUITION_FEE:GEN_QR hoac con vai
--           tro co GEN_QR tren menu cu ma chua co tren MENU_TUITION_FEE). Chua doi gi.
--   V16_2.2 SYS_ROLE_MENU_PERMISSIONS: xoa cac dong phan quyen cua MENU_TUITION_PAYMENT.
--   V16_2.3 SYS_FUNCTIONS: xoa mem (IS_DELETED = 1) VIEW / GEN_QR cua MENU_TUITION_PAYMENT.
--   V16_2.4 SYS_MENUS: xoa mem + an (IS_DELETED = 1, IS_HIDDEN = 1) dong MENU_TUITION_PAYMENT.
--           Giu dong (ID 201) vi SYS_FUNCTIONS / SYS_ROLE_MENU_PERMISSIONS tham chieu bang FK.
--   V16_2.5 Kiem tra sau.
--
-- Khong doi cau truc bang. Chay lai nhieu lan an toan (chi tac dong dong con hieu luc).
-- V1 chay lai khong khoi phuc menu: MERGE WHEN MATCHED cua V1 khong dat lai IS_DELETED cua SYS_MENUS /
-- SYS_FUNCTIONS. V1 co the tao lai dong phan quyen ROLE_ACCOUNTANT cua menu 201 nhung vo hieu (menu da
-- xoa, AccessControlServiceImpl bo qua); chay lai V16_2 de don.
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V16_2__remove_vietqr_menu.sql
--
-- ROLLBACK (khoi phuc menu; chi can khi quay lai backend / frontend cu):
--   UPDATE SYS_MENUS SET IS_DELETED = 0, IS_HIDDEN = 0, UPDATED_AT = SYSTIMESTAMP, UPDATED_BY = 'V16_2_ROLLBACK'
--    WHERE MENU_CODE = 'MENU_TUITION_PAYMENT';
--   UPDATE SYS_FUNCTIONS SET IS_DELETED = 0, UPDATED_AT = SYSTIMESTAMP
--    WHERE MENU_ID = (SELECT ID FROM SYS_MENUS WHERE MENU_CODE = 'MENU_TUITION_PAYMENT');
--   -- Phan quyen truoc V16_2 (DB dev 2026-10-04): ROLE_ADMIN + ROLE_ACCOUNTANT = 'VIEW,GEN_QR'.
--   INSERT INTO SYS_ROLE_MENU_PERMISSIONS (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS, CREATED_BY)
--   SELECT SEQ_SYS_ROLE_MENU_PERM.NEXTVAL, r.ID, m.ID, 'VIEW,GEN_QR', 'V16_2_ROLLBACK'
--     FROM SYS_ROLES r JOIN SYS_MENUS m ON m.MENU_CODE = 'MENU_TUITION_PAYMENT'
--    WHERE r.ROLE_CODE IN ('ROLE_ADMIN', 'ROLE_ACCOUNTANT')
--      AND NOT EXISTS (SELECT 1 FROM SYS_ROLE_MENU_PERMISSIONS p WHERE p.ROLE_ID = r.ID AND p.MENU_ID = m.ID);
--   COMMIT;
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V16_2.1 Kiem tra truoc (V16_1 da chay) ============

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
        RAISE_APPLICATION_ERROR(-20001, 'V16_2: chua co MENU_TUITION_FEE:GEN_QR - hay chay V16_1 truoc');
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
        RAISE_APPLICATION_ERROR(-20002,
            'V16_2: ' || V_MISSING || ' vai tro co GEN_QR tren menu cu nhung chua co tren MENU_TUITION_FEE - hay chay V16_1 truoc');
    END IF;
    DBMS_OUTPUT.PUT_LINE('  V16_1                                   OK');
END;
/

PROMPT ============ V16_2.2 Phan quyen MENU_TUITION_PAYMENT ============

DELETE FROM SYS_ROLE_MENU_PERMISSIONS
 WHERE MENU_ID IN (SELECT ID FROM SYS_MENUS WHERE MENU_CODE = 'MENU_TUITION_PAYMENT');

PROMPT ============ V16_2.3 Chuc nang MENU_TUITION_PAYMENT ============

UPDATE SYS_FUNCTIONS
   SET IS_DELETED = 1,
       UPDATED_AT = SYSTIMESTAMP
 WHERE MENU_ID IN (SELECT ID FROM SYS_MENUS WHERE MENU_CODE = 'MENU_TUITION_PAYMENT')
   AND IS_DELETED = 0;

PROMPT ============ V16_2.4 Menu MENU_TUITION_PAYMENT ============

UPDATE SYS_MENUS
   SET IS_DELETED = 1,
       IS_HIDDEN  = 1,
       UPDATED_AT = SYSTIMESTAMP,
       UPDATED_BY = 'V16_2_MIGRATION'
 WHERE MENU_CODE = 'MENU_TUITION_PAYMENT'
   AND (IS_DELETED = 0 OR IS_HIDDEN = 0);

PROMPT ============ V16_2.5 Kiem tra sau ============

DECLARE
    V_LEFT NUMBER;
BEGIN
    SELECT (SELECT COUNT(*) FROM SYS_MENUS WHERE MENU_CODE = 'MENU_TUITION_PAYMENT' AND IS_DELETED = 0)
         + (SELECT COUNT(*)
              FROM SYS_FUNCTIONS f
              JOIN SYS_MENUS m ON m.ID = f.MENU_ID AND m.MENU_CODE = 'MENU_TUITION_PAYMENT'
             WHERE f.IS_DELETED = 0)
         + (SELECT COUNT(*)
              FROM SYS_ROLE_MENU_PERMISSIONS p
              JOIN SYS_MENUS m ON m.ID = p.MENU_ID AND m.MENU_CODE = 'MENU_TUITION_PAYMENT')
      INTO V_LEFT
      FROM DUAL;
    IF V_LEFT <> 0 THEN
        RAISE_APPLICATION_ERROR(-20003, 'V16_2: MENU_TUITION_PAYMENT con ' || V_LEFT || ' dong hieu luc');
    END IF;
    DBMS_OUTPUT.PUT_LINE('  MENU_TUITION_PAYMENT                    DA GO');
END;
/

COMMIT;

PROMPT ============ V16_2 DONE ============
EXIT
