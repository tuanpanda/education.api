-- =============================================================================
-- EDUCATION - MIGRATION V13_2: KHOA TAM THOI KHI DANG NHAP SAI + XOAY VONG REFRESH TOKEN
--
--   * SYS_USERS: FAILED_LOGIN_COUNT (so lan sai lien tiep), LOCKED_UNTIL (khoa tam thoi).
--     KHONG doi STATUS cua bat ky tai khoan nao (khong vo hieu hoa nguoi dung hien co).
--   * SYS_REFRESH_TOKENS + SEQ_SYS_REFRESH_TOKENS: refresh token da phat hanh (xoay vong, phat hien dung lai,
--     dang xuat theo phien). FAMILY_ID = claim 'sid' cua JWT.
--   * Idempotent: chay lai nhieu lan an toan (bo qua doi tuong da ton tai).
--   * Chay TRUOC khi deploy ban backend moi (entity SYS_USERS da map 2 cot moi).
--   * Sau khi deploy: refresh token cu (chua co trong SYS_REFRESH_TOKENS) khong con dung duoc,
--     nguoi dung dang nhap lai mot lan.
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V13_2__auth_tokens.sql
-- =============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON SIZE UNLIMITED
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

PROMPT ============ V13_2.1 Cot khoa tam thoi tren SYS_USERS ============

DECLARE
    PROCEDURE ADD_COLUMN(p_column IN VARCHAR2, p_ddl IN VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE SYS_USERS ADD (' || p_ddl || ')';
        DBMS_OUTPUT.PUT_LINE('SYS_USERS.' || p_column || ': added');
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE = -1430 THEN
                DBMS_OUTPUT.PUT_LINE('SYS_USERS.' || p_column || ': already exists');
            ELSE
                RAISE;
            END IF;
    END;
BEGIN
    ADD_COLUMN('FAILED_LOGIN_COUNT', 'FAILED_LOGIN_COUNT NUMBER(5) DEFAULT 0 NOT NULL');
    ADD_COLUMN('LOCKED_UNTIL',       'LOCKED_UNTIL TIMESTAMP(6)');
END;
/

DECLARE
    PROCEDURE ADD_CHECK(p_name IN VARCHAR2, p_condition IN VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE SYS_USERS ADD CONSTRAINT ' || p_name
                          || ' CHECK (' || p_condition || ') ENABLE NOVALIDATE';
        DBMS_OUTPUT.PUT_LINE(p_name || ': created');
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE IN (-2264, -2275) THEN
                DBMS_OUTPUT.PUT_LINE(p_name || ': already exists');
            ELSE
                RAISE;
            END IF;
    END;
BEGIN
    ADD_CHECK('CK_USERS_FAILED_LOGIN', 'FAILED_LOGIN_COUNT >= 0');
END;
/

COMMENT ON COLUMN SYS_USERS.FAILED_LOGIN_COUNT IS 'So lan dang nhap sai lien tiep; ve 0 khi dang nhap thanh cong';
COMMENT ON COLUMN SYS_USERS.LOCKED_UNTIL IS 'Khoa tam thoi toi thoi diem nay do dang nhap sai nhieu lan (NULL = khong khoa)';

PROMPT ============ V13_2.2 Bang SYS_REFRESH_TOKENS ============

DECLARE
    PROCEDURE DDL(p_name IN VARCHAR2, p_sql IN VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE p_sql;
        DBMS_OUTPUT.PUT_LINE(p_name || ': created');
    EXCEPTION
        WHEN OTHERS THEN
            -- -955: ten da dung, -1408: cot da co index, -2261/-2275: rang buoc da ton tai
            IF SQLCODE IN (-955, -1408, -2261, -2275) THEN
                DBMS_OUTPUT.PUT_LINE(p_name || ': already exists');
            ELSE
                RAISE;
            END IF;
    END;
BEGIN
    DDL('SEQ_SYS_REFRESH_TOKENS',
        'CREATE SEQUENCE SEQ_SYS_REFRESH_TOKENS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE');
    DDL('SYS_REFRESH_TOKENS', q'[CREATE TABLE SYS_REFRESH_TOKENS (
        ID           NUMBER(19)     NOT NULL,
        USER_ID      NUMBER(19)     NOT NULL,
        JTI          VARCHAR2(64)   NOT NULL,
        FAMILY_ID    VARCHAR2(64)   NOT NULL,
        EXPIRES_AT   TIMESTAMP(6)   NOT NULL,
        REVOKED_AT   TIMESTAMP(6),
        REPLACED_BY  VARCHAR2(64),
        CREATED_AT   TIMESTAMP(6)   DEFAULT SYSTIMESTAMP NOT NULL,
        CONSTRAINT PK_SYS_REFRESH_TOKENS PRIMARY KEY (ID),
        CONSTRAINT UQ_REFRESH_TOKENS_JTI UNIQUE (JTI),
        CONSTRAINT FK_REFRESH_TOKENS_USER FOREIGN KEY (USER_ID) REFERENCES SYS_USERS (ID) ON DELETE CASCADE
    )]');
    DDL('IDX_REFRESH_TOKENS_FAMILY',
        'CREATE INDEX IDX_REFRESH_TOKENS_FAMILY ON SYS_REFRESH_TOKENS (FAMILY_ID, REVOKED_AT)');
    DDL('IDX_REFRESH_TOKENS_USER',
        'CREATE INDEX IDX_REFRESH_TOKENS_USER ON SYS_REFRESH_TOKENS (USER_ID, EXPIRES_AT)');
END;
/

COMMENT ON TABLE SYS_REFRESH_TOKENS IS 'Refresh token da phat hanh: xoay vong, phat hien dung lai, dang xuat theo phien';
COMMENT ON COLUMN SYS_REFRESH_TOKENS.JTI IS 'Claim jti cua refresh token (duy nhat)';
COMMENT ON COLUMN SYS_REFRESH_TOKENS.FAMILY_ID IS 'Phien dang nhap (claim sid); cac token xoay vong tu cung mot lan dang nhap';
COMMENT ON COLUMN SYS_REFRESH_TOKENS.REVOKED_AT IS 'Thoi diem thu hoi (xoay vong / dang xuat / phat hien dung lai); NULL = con hieu luc';
COMMENT ON COLUMN SYS_REFRESH_TOKENS.REPLACED_BY IS 'JTI cua token thay the khi xoay vong';

COMMIT;

PROMPT ============ V13_2 Kiem tra ============

SELECT COLUMN_NAME, DATA_TYPE, NULLABLE
  FROM USER_TAB_COLUMNS
 WHERE TABLE_NAME = 'SYS_USERS'
   AND COLUMN_NAME IN ('FAILED_LOGIN_COUNT', 'LOCKED_UNTIL')
 ORDER BY COLUMN_NAME;

SELECT COUNT(*) AS REFRESH_TOKENS FROM SYS_REFRESH_TOKENS;

PROMPT V13_2 hoan tat.
