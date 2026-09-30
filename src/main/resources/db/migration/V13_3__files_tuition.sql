-- =============================================================================
-- EDUCATION - MIGRATION V13_3: FILE & HOC PHI
--
--   * FIN_PAYMENT_TRANSACTIONS.BANK_REFERENCE_NO: unique index UQ_FIN_TRANS_BANK_REF
--     (moi ma tham chieu ngan hang chi duoc ghi nhan 1 lan; nhieu dong NULL van hop le).
--     Truoc khi tao index, script kiem tra du lieu trung (khong NULL). Neu co trung, script dung
--     lai (ORA-20133) va in danh sach ma trung de xu ly tay - KHONG tu y sua / xoa du lieu.
--   * Chay lai nhieu lan an toan (idempotent): index da ton tai thi bo qua.
--   * Khong thay doi du lieu SYS_ATTACHED_FILES: kiem tra dinh dang file va phan quyen theo
--     module duoc xu ly o tang ung dung.
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @V13_3__files_tuition.sql
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============ V13_3.1 Kiem tra BANK_REFERENCE_NO trung ============

DECLARE
    v_dup_values NUMBER;
    v_dup_rows   NUMBER;
    v_list       VARCHAR2(2000);
BEGIN
    SELECT COUNT(*), NVL(SUM(cnt), 0)
      INTO v_dup_values, v_dup_rows
      FROM (SELECT BANK_REFERENCE_NO, COUNT(*) cnt
              FROM FIN_PAYMENT_TRANSACTIONS
             WHERE BANK_REFERENCE_NO IS NOT NULL
             GROUP BY BANK_REFERENCE_NO
            HAVING COUNT(*) > 1);

    IF v_dup_values > 0 THEN
        FOR r IN (SELECT BANK_REFERENCE_NO, COUNT(*) cnt
                    FROM FIN_PAYMENT_TRANSACTIONS
                   WHERE BANK_REFERENCE_NO IS NOT NULL
                   GROUP BY BANK_REFERENCE_NO
                  HAVING COUNT(*) > 1
                   ORDER BY COUNT(*) DESC, BANK_REFERENCE_NO)
        LOOP
            DBMS_OUTPUT.PUT_LINE('  DUPLICATE BANK_REFERENCE_NO=' || r.BANK_REFERENCE_NO || ' x' || r.cnt);
            IF v_list IS NULL OR LENGTH(v_list) < 1500 THEN
                v_list := v_list || CASE WHEN v_list IS NOT NULL THEN ', ' END
                          || SUBSTR(r.BANK_REFERENCE_NO, 1, 100) || ' (x' || r.cnt || ')';
            END IF;
        END LOOP;
        RAISE_APPLICATION_ERROR(-20133,
            'V13_3: FIN_PAYMENT_TRANSACTIONS co ' || v_dup_values || ' ma BANK_REFERENCE_NO trung ('
            || v_dup_rows || ' dong). Xu ly du lieu trung (doi ma / de NULL cho giao dich sai) roi chay lai. '
            || 'Danh sach: ' || v_list);
    END IF;

    DBMS_OUTPUT.PUT_LINE('BANK_REFERENCE_NO: khong co gia tri trung');
END;
/

PROMPT ============ V13_3.2 Unique index UQ_FIN_TRANS_BANK_REF ============

DECLARE
    v_exists       NUMBER;
    v_other_index  VARCHAR2(128);
    v_other_unique VARCHAR2(9);
BEGIN
    SELECT COUNT(*) INTO v_exists FROM USER_INDEXES WHERE INDEX_NAME = 'UQ_FIN_TRANS_BANK_REF';
    IF v_exists > 0 THEN
        DBMS_OUTPUT.PUT_LINE('UQ_FIN_TRANS_BANK_REF: already exists');
        RETURN;
    END IF;

    -- Index khac dang phu dung (chi) cot BANK_REFERENCE_NO se lam CREATE INDEX loi ORA-01408.
    BEGIN
        SELECT i.INDEX_NAME, i.UNIQUENESS
          INTO v_other_index, v_other_unique
          FROM USER_INDEXES i
         WHERE i.TABLE_NAME = 'FIN_PAYMENT_TRANSACTIONS'
           AND (SELECT COUNT(*) FROM USER_IND_COLUMNS c WHERE c.INDEX_NAME = i.INDEX_NAME) = 1
           AND EXISTS (SELECT 1 FROM USER_IND_COLUMNS c
                        WHERE c.INDEX_NAME = i.INDEX_NAME AND c.COLUMN_NAME = 'BANK_REFERENCE_NO')
           AND ROWNUM = 1;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            v_other_index := NULL;
    END;

    IF v_other_index IS NOT NULL THEN
        IF v_other_unique = 'UNIQUE' THEN
            DBMS_OUTPUT.PUT_LINE('BANK_REFERENCE_NO: da co unique index ' || v_other_index || ' - bo qua');
            RETURN;
        END IF;
        RAISE_APPLICATION_ERROR(-20134,
            'V13_3: index ' || v_other_index || ' (NONUNIQUE) dang phu cot BANK_REFERENCE_NO. '
            || 'DROP INDEX ' || v_other_index || ' roi chay lai de tao UQ_FIN_TRANS_BANK_REF.');
    END IF;

    EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UQ_FIN_TRANS_BANK_REF ON FIN_PAYMENT_TRANSACTIONS (BANK_REFERENCE_NO)';
    DBMS_OUTPUT.PUT_LINE('UQ_FIN_TRANS_BANK_REF: created');
END;
/

COMMIT;

PROMPT ============ V13_3 DONE ============
EXIT
