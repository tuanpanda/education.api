-- =============================================================================
-- EDUCATION - SUA TAI KHOAN admin (VA TAI KHOAN DEMO) CON HASH MAT KHAU KHONG DUNG DUOC
--
--   Script van hanh (KHONG dong goi trong jar). Dung khi dang nhap admin bao
--   "Ten dang nhap hoac mat khau khong dung" vi DB duoc tao tu schema_init.sql cu co hash BCrypt "mau"
--   ('$2a$10$7EqJ...Kq2G') khong khop mat khau nao, hoac con hash SHA-256 cua V1 (khong phai BCrypt).
--
--   CHI cham toi dong co PASSWORD_HASH:
--     * = hash "mau" '$2a$10$7EqJtq98hPqEX7fNZaFWoO96u8xLw9Jm9j.qR8xT6rW1dG7z5Kq2G', hoac
--     * khong phai BCrypt (NOT LIKE '$2%').
--   Tai khoan da co BCrypt that (mat khau da doi) KHONG bao gio bi dat lai -> chay lai nhieu lan an toan.
--
--   Ket qua voi dong bi sua:
--     * admin    -> mat khau tam thoi Admin@123, ACTIVE, chua xoa, co ROLE_ADMIN.
--     * tai khoan khac -> mat khau tam thoi Education@123.
--     * Tat ca bat buoc doi mat khau o lan dang nhap dau (MUST_CHANGE_PASSWORD = 1). DOI NGAY sau khi dang nhap.
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @scripts/db/fix_admin_password.sql
-- =============================================================================

SET DEFINE OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

-- BCrypt (cost 10) cua 'Admin@123' - giong V12__system_admin_security.sql.
-- Chi khi dong admin vua duoc sua moi dam bao lai vai tro ROLE_ADMIN.
BEGIN
    UPDATE SYS_USERS
       SET PASSWORD_HASH = '$2a$10$aHlyZN6UgaGm.aPpX.jBjuKV3p0hBxTtfrUifsl0gyNMnzzT81OCy',
           MUST_CHANGE_PASSWORD = 1,
           TOKEN_VERSION = TOKEN_VERSION + 1,
           STATUS = 'ACTIVE',
           IS_DELETED = 0,
           UPDATED_AT = SYSTIMESTAMP,
           UPDATED_BY = 'FIX_ADMIN_PASSWORD'
     WHERE USERNAME = 'admin'
       AND (PASSWORD_HASH = '$2a$10$7EqJtq98hPqEX7fNZaFWoO96u8xLw9Jm9j.qR8xT6rW1dG7z5Kq2G'
            OR PASSWORD_HASH NOT LIKE '$2%');

    IF SQL%ROWCOUNT > 0 THEN
        MERGE INTO SYS_USER_ROLES t
        USING (
            SELECT u.ID USER_ID, r.ID ROLE_ID
              FROM SYS_USERS u
              JOIN SYS_ROLES r ON r.ROLE_CODE = 'ROLE_ADMIN'
             WHERE u.USERNAME = 'admin'
        ) s ON (t.USER_ID = s.USER_ID AND t.ROLE_ID = s.ROLE_ID)
        WHEN NOT MATCHED THEN INSERT (USER_ID, ROLE_ID, ASSIGNED_BY)
                              VALUES (s.USER_ID, s.ROLE_ID, 'FIX_ADMIN_PASSWORD');
    END IF;
END;
/

-- Tai khoan demo (vd. teacher1 tu schema_init.sql cu) con hash "mau" hoac hash khong phai BCrypt (SHA-256 V1)
-- -> BCrypt (cost 10) cua 'Education@123' (giong V12), bat buoc doi mat khau.
-- Dong da co BCrypt that (hoac mat khau nguoi dung tu doi) khong bi dong toi.
UPDATE SYS_USERS
   SET PASSWORD_HASH = '$2a$10$NDLPXq0uJZykYoFJWdOZIuYlv64aHu.YXRqP9n8yWAd7./30VwigW',
       MUST_CHANGE_PASSWORD = 1,
       TOKEN_VERSION = TOKEN_VERSION + 1,
       UPDATED_AT = SYSTIMESTAMP,
       UPDATED_BY = 'FIX_ADMIN_PASSWORD'
 WHERE USERNAME <> 'admin'
   AND (PASSWORD_HASH = '$2a$10$7EqJtq98hPqEX7fNZaFWoO96u8xLw9Jm9j.qR8xT6rW1dG7z5Kq2G'
        OR PASSWORD_HASH NOT LIKE '$2%');

COMMIT;

SELECT USERNAME, SUBSTR(PASSWORD_HASH, 1, 7) AS HASH_PREFIX, STATUS, IS_DELETED, MUST_CHANGE_PASSWORD, TOKEN_VERSION
  FROM SYS_USERS
 ORDER BY ID;

EXIT
