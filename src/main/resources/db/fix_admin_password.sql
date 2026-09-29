-- =============================================================================
-- EDUCATION - SUA TAI KHOAN admin VE TRANG THAI SEED CUA V12
--
--   Dung khi dang nhap admin / Admin@123 bao "Ten dang nhap hoac mat khau khong dung":
--   DB duoc tao tu schema_init.sql cu co hash BCrypt "mau" ('$2a$10$7EqJ...Kq2G') khong khop mat khau
--   nao, nen V12 (chi ghi de hash chua phai BCrypt) da bo qua dong admin.
--
--   Ket qua: admin / Admin@123, bat buoc doi mat khau, ACTIVE, chua xoa, co vai tro ROLE_ADMIN.
--   Idempotent: chi cap nhat (va tang TOKEN_VERSION) khi dong admin chua o dung trang thai do.
--   CANH BAO: script nay dat lai mat khau admin ve Admin@123 neu admin da doi sang mat khau khac.
--
--   sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/fix_admin_password.sql
-- =============================================================================

SET DEFINE OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

-- BCrypt (cost 10) cua 'Admin@123' - giong V12__system_admin_security.sql.
UPDATE SYS_USERS
   SET PASSWORD_HASH = '$2a$10$aHlyZN6UgaGm.aPpX.jBjuKV3p0hBxTtfrUifsl0gyNMnzzT81OCy',
       MUST_CHANGE_PASSWORD = 1,
       TOKEN_VERSION = TOKEN_VERSION + 1,
       STATUS = 'ACTIVE',
       IS_DELETED = 0,
       UPDATED_AT = SYSTIMESTAMP,
       UPDATED_BY = 'FIX_ADMIN_PASSWORD'
 WHERE USERNAME = 'admin'
   AND (PASSWORD_HASH <> '$2a$10$aHlyZN6UgaGm.aPpX.jBjuKV3p0hBxTtfrUifsl0gyNMnzzT81OCy'
        OR MUST_CHANGE_PASSWORD <> 1
        OR STATUS <> 'ACTIVE'
        OR IS_DELETED <> 0);

MERGE INTO SYS_USER_ROLES t
USING (
    SELECT u.ID USER_ID, r.ID ROLE_ID
      FROM SYS_USERS u
      JOIN SYS_ROLES r ON r.ROLE_CODE = 'ROLE_ADMIN'
     WHERE u.USERNAME = 'admin'
) s ON (t.USER_ID = s.USER_ID AND t.ROLE_ID = s.ROLE_ID)
WHEN NOT MATCHED THEN INSERT (USER_ID, ROLE_ID, ASSIGNED_BY) VALUES (s.USER_ID, s.ROLE_ID, 'FIX_ADMIN_PASSWORD');

COMMIT;

SELECT USERNAME, SUBSTR(PASSWORD_HASH, 1, 7) AS HASH_PREFIX, STATUS, IS_DELETED, MUST_CHANGE_PASSWORD, TOKEN_VERSION
  FROM SYS_USERS
 WHERE USERNAME = 'admin';

EXIT