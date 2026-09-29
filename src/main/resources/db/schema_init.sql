--------------------------------------------------------------------------------
-- EDUCATION PROJECT - ORACLE DATABASE INITIALIZATION SCRIPT
--
-- Kiến trúc: Spring Data JPA (DB-First, ddl-auto=none) + Spring JDBC (SimpleJdbcCall)
-- gọi Standalone Procedure (procedure độc lập, KHÔNG đóng gói trong Package).
--
-- Script này được sinh khớp 100% với schema đang chạy trên Oracle 21c (user EDUCATION):
-- 8 bảng, 7 sequence, 1 index, 3 Standalone Procedure và dữ liệu tham chiếu.
--
-- Chạy bằng SQL*Plus / SQLcl / SQL Developer với user sở hữu schema, ví dụ:
--     sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @schema_init.sql
--
-- CẢNH BÁO: Section 0 DROP toàn bộ đối tượng cũ, mọi dữ liệu hiện có sẽ bị mất.
--------------------------------------------------------------------------------

SET DEFINE OFF;
SET SERVEROUTPUT ON;

--------------------------------------------------------------------------------
-- 0. DROP OBJECTS NẾU ĐÃ TỒN TẠI (script chạy lại được nhiều lần - idempotent)
--    Bảng con được drop trước bảng cha để tránh vướng khóa ngoại.
--------------------------------------------------------------------------------
DECLARE
    PROCEDURE DROP_IF_EXISTS(P_TYPE VARCHAR2, P_NAME VARCHAR2, P_OPTIONS VARCHAR2 DEFAULT NULL) IS
    BEGIN
        EXECUTE IMMEDIATE 'DROP ' || P_TYPE || ' ' || P_NAME || ' ' || P_OPTIONS;
        DBMS_OUTPUT.PUT_LINE('Dropped ' || P_TYPE || ' ' || P_NAME);
    EXCEPTION
        WHEN OTHERS THEN
            -- -942: table/view không tồn tại, -2289: sequence không tồn tại,
            -- -4043: procedure/package không tồn tại.
            IF SQLCODE NOT IN (-942, -2289, -4043) THEN
                RAISE;
            END IF;
    END DROP_IF_EXISTS;
BEGIN
    DROP_IF_EXISTS('TABLE', 'SYS_ATTACHED_FILES', 'CASCADE CONSTRAINTS PURGE');
    DROP_IF_EXISTS('TABLE', 'EDU_STUDENTS', 'CASCADE CONSTRAINTS PURGE');
    DROP_IF_EXISTS('TABLE', 'SYS_ROLE_MENU_PERMISSIONS', 'CASCADE CONSTRAINTS PURGE');
    DROP_IF_EXISTS('TABLE', 'SYS_FUNCTIONS', 'CASCADE CONSTRAINTS PURGE');
    DROP_IF_EXISTS('TABLE', 'SYS_USER_ROLES', 'CASCADE CONSTRAINTS PURGE');
    DROP_IF_EXISTS('TABLE', 'SYS_MENUS', 'CASCADE CONSTRAINTS PURGE');
    DROP_IF_EXISTS('TABLE', 'SYS_ROLES', 'CASCADE CONSTRAINTS PURGE');
    DROP_IF_EXISTS('TABLE', 'SYS_USERS', 'CASCADE CONSTRAINTS PURGE');

    DROP_IF_EXISTS('SEQUENCE', 'SEQ_SYS_ATTACHED_FILES');
    DROP_IF_EXISTS('SEQUENCE', 'SEQ_EDU_STUDENTS');
    DROP_IF_EXISTS('SEQUENCE', 'SEQ_SYS_ROLE_MENU_PERM');
    DROP_IF_EXISTS('SEQUENCE', 'SEQ_SYS_FUNCTIONS');
    DROP_IF_EXISTS('SEQUENCE', 'SEQ_SYS_MENUS');
    DROP_IF_EXISTS('SEQUENCE', 'SEQ_SYS_ROLES');
    DROP_IF_EXISTS('SEQUENCE', 'SEQ_SYS_USERS');

    DROP_IF_EXISTS('PROCEDURE', 'PRC_SEARCH_STUDENTS');
    DROP_IF_EXISTS('PROCEDURE', 'PRC_GET_FILES_BY_REF');
    DROP_IF_EXISTS('PROCEDURE', 'PRC_GET_USER_SIDEBAR_MENU');

    -- Hai Package của kiến trúc cũ, nay đã được thay bằng Standalone Procedure.
    DROP_IF_EXISTS('PACKAGE', 'PKG_STUDENT_TEST');
    DROP_IF_EXISTS('PACKAGE', 'PKG_FILE_OPERATIONS');
END;
/

--------------------------------------------------------------------------------
-- 1. TABLE: SYS_USERS (Người dùng hệ thống)
--------------------------------------------------------------------------------
CREATE TABLE SYS_USERS (
    ID              NUMBER(19)      NOT NULL,
    USERNAME        VARCHAR2(50)    NOT NULL,
    PASSWORD_HASH   VARCHAR2(255)   NOT NULL,
    FULL_NAME       VARCHAR2(100)   NOT NULL,
    EMAIL           VARCHAR2(100),
    PHONE           VARCHAR2(20),
    STATUS          VARCHAR2(20)    DEFAULT 'ACTIVE' NOT NULL,
    IS_DELETED      NUMBER(1)       DEFAULT 0 NOT NULL,
    CREATED_AT      TIMESTAMP(6)    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UPDATED_AT      TIMESTAMP(6)    DEFAULT CURRENT_TIMESTAMP,
    CREATED_BY      VARCHAR2(50),
    UPDATED_BY      VARCHAR2(50),
    CONSTRAINT PK_SYS_USERS PRIMARY KEY (ID),
    CONSTRAINT UQ_SYS_USERS_USERNAME UNIQUE (USERNAME),
    CONSTRAINT CK_USERS_DELETED CHECK (IS_DELETED IN (0, 1))
);

COMMENT ON TABLE SYS_USERS IS 'Người dùng hệ thống - Module Phân quyền';
COMMENT ON COLUMN SYS_USERS.ID IS 'Khóa chính, sinh từ SEQ_SYS_USERS';
COMMENT ON COLUMN SYS_USERS.USERNAME IS 'Tên đăng nhập, duy nhất';
COMMENT ON COLUMN SYS_USERS.PASSWORD_HASH IS 'Mật khẩu đã băm (BCrypt)';
COMMENT ON COLUMN SYS_USERS.IS_DELETED IS 'Cờ xóa mềm: 0 - chưa xóa, 1 - đã xóa';

CREATE SEQUENCE SEQ_SYS_USERS
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

--------------------------------------------------------------------------------
-- 2. TABLE: SYS_ROLES (Vai trò)
--------------------------------------------------------------------------------
CREATE TABLE SYS_ROLES (
    ID              NUMBER(19)      NOT NULL,
    ROLE_CODE       VARCHAR2(50)    NOT NULL,
    ROLE_NAME       VARCHAR2(100)   NOT NULL,
    DESCRIPTION     VARCHAR2(255),
    STATUS          VARCHAR2(20)    DEFAULT 'ACTIVE' NOT NULL,
    IS_DELETED      NUMBER(1)       DEFAULT 0 NOT NULL,
    CREATED_AT      TIMESTAMP(6)    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UPDATED_AT      TIMESTAMP(6)    DEFAULT CURRENT_TIMESTAMP,
    CREATED_BY      VARCHAR2(50),
    UPDATED_BY      VARCHAR2(50),
    CONSTRAINT PK_SYS_ROLES PRIMARY KEY (ID),
    CONSTRAINT UQ_SYS_ROLES_CODE UNIQUE (ROLE_CODE),
    CONSTRAINT CK_ROLES_DELETED CHECK (IS_DELETED IN (0, 1))
);

COMMENT ON TABLE SYS_ROLES IS 'Vai trò người dùng - Module Phân quyền';
COMMENT ON COLUMN SYS_ROLES.ID IS 'Khóa chính, sinh từ SEQ_SYS_ROLES';
COMMENT ON COLUMN SYS_ROLES.ROLE_CODE IS 'Mã vai trò, duy nhất (ROLE_ADMIN, ROLE_TEACHER...)';

CREATE SEQUENCE SEQ_SYS_ROLES
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

--------------------------------------------------------------------------------
-- 3. TABLE: SYS_USER_ROLES (Gán vai trò cho người dùng - khóa chính kép)
--------------------------------------------------------------------------------
CREATE TABLE SYS_USER_ROLES (
    USER_ID         NUMBER(19)      NOT NULL,
    ROLE_ID         NUMBER(19)      NOT NULL,
    ASSIGNED_AT     TIMESTAMP(6)    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ASSIGNED_BY     VARCHAR2(50),
    CONSTRAINT PK_SYS_USER_ROLES PRIMARY KEY (USER_ID, ROLE_ID),
    CONSTRAINT FK_USER_ROLES_USER FOREIGN KEY (USER_ID)
        REFERENCES SYS_USERS (ID) ON DELETE CASCADE,
    CONSTRAINT FK_USER_ROLES_ROLE FOREIGN KEY (ROLE_ID)
        REFERENCES SYS_ROLES (ID) ON DELETE CASCADE
);

COMMENT ON TABLE SYS_USER_ROLES IS 'Bảng nối người dùng - vai trò (dùng bởi PRC_GET_USER_SIDEBAR_MENU)';

--------------------------------------------------------------------------------
-- 4. TABLE: SYS_MENUS (Menu đa cấp của sidebar, tự tham chiếu qua PARENT_ID)
--------------------------------------------------------------------------------
CREATE TABLE SYS_MENUS (
    ID              NUMBER(19)      NOT NULL,
    PARENT_ID       NUMBER(19),
    MENU_CODE       VARCHAR2(50)    NOT NULL,
    MENU_NAME       VARCHAR2(100)   NOT NULL,
    MENU_TYPE       VARCHAR2(20)    NOT NULL,
    PATH            VARCHAR2(255),
    ICON            VARCHAR2(50),
    SORT_ORDER      NUMBER(5)       DEFAULT 0 NOT NULL,
    IS_HIDDEN       NUMBER(1)       DEFAULT 0 NOT NULL,
    STATUS          VARCHAR2(20)    DEFAULT 'ACTIVE' NOT NULL,
    IS_DELETED      NUMBER(1)       DEFAULT 0 NOT NULL,
    CREATED_AT      TIMESTAMP(6)    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UPDATED_AT      TIMESTAMP(6)    DEFAULT CURRENT_TIMESTAMP,
    CREATED_BY      VARCHAR2(50),
    UPDATED_BY      VARCHAR2(50),
    CONSTRAINT PK_SYS_MENUS PRIMARY KEY (ID),
    CONSTRAINT UQ_SYS_MENUS_CODE UNIQUE (MENU_CODE),
    CONSTRAINT CK_MENUS_DELETED CHECK (IS_DELETED IN (0, 1)),
    CONSTRAINT FK_SYS_MENUS_PARENT FOREIGN KEY (PARENT_ID)
        REFERENCES SYS_MENUS (ID) ON DELETE CASCADE
);

COMMENT ON TABLE SYS_MENUS IS 'Menu đa cấp của sidebar - Module Menu & Phân quyền';
COMMENT ON COLUMN SYS_MENUS.PARENT_ID IS 'ID menu cha, NULL nếu là menu gốc';
COMMENT ON COLUMN SYS_MENUS.MENU_TYPE IS 'DIR - thư mục chứa menu con, MENU - trang chức năng';
COMMENT ON COLUMN SYS_MENUS.PATH IS 'Đường dẫn route phía frontend';
COMMENT ON COLUMN SYS_MENUS.SORT_ORDER IS 'Thứ tự hiển thị trong cùng một cấp';
COMMENT ON COLUMN SYS_MENUS.IS_HIDDEN IS 'Ẩn khỏi sidebar: 0 - hiện, 1 - ẩn';

CREATE SEQUENCE SEQ_SYS_MENUS
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

--------------------------------------------------------------------------------
-- 5. TABLE: SYS_FUNCTIONS (Các hành động khả dụng trên một menu)
--------------------------------------------------------------------------------
CREATE TABLE SYS_FUNCTIONS (
    ID              NUMBER(19)      NOT NULL,
    MENU_ID         NUMBER(19)      NOT NULL,
    FUNCTION_CODE   VARCHAR2(50)    NOT NULL,
    FUNCTION_NAME   VARCHAR2(100)   NOT NULL,
    IS_DELETED      NUMBER(1)       DEFAULT 0 NOT NULL,
    CREATED_AT      TIMESTAMP(6)    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UPDATED_AT      TIMESTAMP(6)    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT PK_SYS_FUNCTIONS PRIMARY KEY (ID),
    CONSTRAINT UQ_SYS_FUNC_MENU UNIQUE (MENU_ID, FUNCTION_CODE),
    CONSTRAINT CK_FUNC_DELETED CHECK (IS_DELETED IN (0, 1)),
    CONSTRAINT FK_SYS_FUNCTIONS_MENU FOREIGN KEY (MENU_ID)
        REFERENCES SYS_MENUS (ID) ON DELETE CASCADE
);

COMMENT ON TABLE SYS_FUNCTIONS IS 'Hành động khả dụng trên menu (VIEW, CREATE, EXPORT...)';
COMMENT ON COLUMN SYS_FUNCTIONS.FUNCTION_CODE IS 'Mã hành động, dùng ghép thành quyền MENU_CODE:ACTION';

CREATE SEQUENCE SEQ_SYS_FUNCTIONS
    START WITH 8
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

--------------------------------------------------------------------------------
-- 6. TABLE: SYS_ROLE_MENU_PERMISSIONS (Quyền của vai trò trên từng menu)
--------------------------------------------------------------------------------
CREATE TABLE SYS_ROLE_MENU_PERMISSIONS (
    ID                  NUMBER(19)      NOT NULL,
    ROLE_ID             NUMBER(19)      NOT NULL,
    MENU_ID             NUMBER(19)      NOT NULL,
    ALLOWED_FUNCTIONS   VARCHAR2(500),
    CREATED_AT          TIMESTAMP(6)    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UPDATED_AT          TIMESTAMP(6)    DEFAULT CURRENT_TIMESTAMP,
    CREATED_BY          VARCHAR2(50),
    UPDATED_BY          VARCHAR2(50),
    CONSTRAINT PK_SYS_ROLE_MENU_PERM PRIMARY KEY (ID),
    CONSTRAINT UQ_ROLE_MENU_PERM UNIQUE (ROLE_ID, MENU_ID),
    CONSTRAINT FK_ROLE_PERM_ROLE FOREIGN KEY (ROLE_ID)
        REFERENCES SYS_ROLES (ID) ON DELETE CASCADE,
    CONSTRAINT FK_ROLE_PERM_MENU FOREIGN KEY (MENU_ID)
        REFERENCES SYS_MENUS (ID) ON DELETE CASCADE
);

COMMENT ON TABLE SYS_ROLE_MENU_PERMISSIONS IS 'Quyền của vai trò trên menu - Module Phân quyền';
COMMENT ON COLUMN SYS_ROLE_MENU_PERMISSIONS.ALLOWED_FUNCTIONS IS
    'Danh sách mã chức năng phân tách bằng dấu phẩy, ví dụ: VIEW,CREATE,UPDATE,DELETE,EXPORT';

CREATE SEQUENCE SEQ_SYS_ROLE_MENU_PERM
    START WITH 10
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

--------------------------------------------------------------------------------
-- 7. TABLE: EDU_STUDENTS (Module Quản lý Học sinh)
--------------------------------------------------------------------------------
CREATE TABLE EDU_STUDENTS (
    ID              NUMBER(19)      NOT NULL,
    STUDENT_CODE    VARCHAR2(30)    NOT NULL,
    FULL_NAME       VARCHAR2(150)   NOT NULL,
    EMAIL           VARCHAR2(100),
    DATE_OF_BIRTH   DATE,
    PARENT_NAME     VARCHAR2(100),
    PHONE           VARCHAR2(20),
    ADDRESS         VARCHAR2(255),
    NOTE            VARCHAR2(500),
    STATUS          VARCHAR2(20)    DEFAULT 'ACTIVE' NOT NULL,
    IS_DELETED      NUMBER(1)       DEFAULT 0 NOT NULL,
    CREATED_AT      TIMESTAMP(6)    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UPDATED_AT      TIMESTAMP(6)    DEFAULT CURRENT_TIMESTAMP,
    CREATED_BY      VARCHAR2(50),
    UPDATED_BY      VARCHAR2(50),
    CONSTRAINT PK_EDU_STUDENTS PRIMARY KEY (ID),
    CONSTRAINT UQ_EDU_STUDENTS_CODE UNIQUE (STUDENT_CODE),
    CONSTRAINT CK_STUDENTS_DELETED CHECK (IS_DELETED IN (0, 1))
);

COMMENT ON TABLE EDU_STUDENTS IS 'Danh sách học sinh - Module Student Management Hybrid';
COMMENT ON COLUMN EDU_STUDENTS.ID IS 'Khóa chính, sinh từ SEQ_EDU_STUDENTS';
COMMENT ON COLUMN EDU_STUDENTS.STUDENT_CODE IS 'Mã học sinh, duy nhất';
COMMENT ON COLUMN EDU_STUDENTS.FULL_NAME IS 'Họ và tên học sinh';
COMMENT ON COLUMN EDU_STUDENTS.DATE_OF_BIRTH IS 'Ngày sinh (DOB)';
COMMENT ON COLUMN EDU_STUDENTS.PARENT_NAME IS 'Phụ huynh: Mẹ/Bố hoặc tên người giám hộ';
COMMENT ON COLUMN EDU_STUDENTS.PHONE IS 'Số điện thoại liên hệ';
COMMENT ON COLUMN EDU_STUDENTS.ADDRESS IS 'Địa chỉ';
COMMENT ON COLUMN EDU_STUDENTS.NOTE IS 'Ghi chú';
COMMENT ON COLUMN EDU_STUDENTS.STATUS IS 'Trạng thái: ACTIVE, INACTIVE, GRADUATED...';
COMMENT ON COLUMN EDU_STUDENTS.IS_DELETED IS 'Cờ xóa mềm: 0 - chưa xóa, 1 - đã xóa';

CREATE SEQUENCE SEQ_EDU_STUDENTS
    START WITH 3
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

--------------------------------------------------------------------------------
-- 8. TABLE: SYS_ATTACHED_FILES (Module Quản lý File & Xem File)
--------------------------------------------------------------------------------
CREATE TABLE SYS_ATTACHED_FILES (
    ID              NUMBER(19)      NOT NULL,
    ORIGINAL_NAME   VARCHAR2(255)   NOT NULL,
    STORED_NAME     VARCHAR2(255)   NOT NULL,
    FILE_PATH       VARCHAR2(500)   NOT NULL,
    CONTENT_TYPE    VARCHAR2(100)   NOT NULL,
    FILE_SIZE       NUMBER(19)      NOT NULL,
    MODULE_NAME     VARCHAR2(50)    NOT NULL,
    REFERENCE_ID    NUMBER(19),
    IS_DELETED      NUMBER(1)       DEFAULT 0 NOT NULL,
    CREATED_AT      TIMESTAMP(6)    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UPDATED_AT      TIMESTAMP(6)    DEFAULT CURRENT_TIMESTAMP,
    CREATED_BY      VARCHAR2(50),
    UPDATED_BY      VARCHAR2(50),
    CONSTRAINT PK_SYS_ATTACHED_FILES PRIMARY KEY (ID),
    CONSTRAINT CK_FILES_DELETED CHECK (IS_DELETED IN (0, 1))
);

COMMENT ON TABLE SYS_ATTACHED_FILES IS 'Metadata file đính kèm - Module File Management & Viewer';
COMMENT ON COLUMN SYS_ATTACHED_FILES.ID IS 'Khóa chính, sinh từ SEQ_SYS_ATTACHED_FILES';
COMMENT ON COLUMN SYS_ATTACHED_FILES.ORIGINAL_NAME IS 'Tên file gốc do người dùng upload';
COMMENT ON COLUMN SYS_ATTACHED_FILES.STORED_NAME IS 'Tên file vật lý dạng {UUID}_{tenfile}';
COMMENT ON COLUMN SYS_ATTACHED_FILES.FILE_PATH IS 'Đường dẫn TƯƠNG ĐỐI so với app.storage.base-dir';
COMMENT ON COLUMN SYS_ATTACHED_FILES.MODULE_NAME IS 'Module nghiệp vụ sở hữu file (STUDENT, COMMON...)';
COMMENT ON COLUMN SYS_ATTACHED_FILES.REFERENCE_ID IS 'ID bản ghi nghiệp vụ liên quan';
COMMENT ON COLUMN SYS_ATTACHED_FILES.IS_DELETED IS 'Cờ xóa mềm: 0 - chưa xóa, 1 - đã xóa';

CREATE INDEX IDX_FILES_MODULE_REF
    ON SYS_ATTACHED_FILES (MODULE_NAME, REFERENCE_ID, IS_DELETED);

CREATE SEQUENCE SEQ_SYS_ATTACHED_FILES
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

--------------------------------------------------------------------------------
-- 9. STANDALONE PROCEDURE: PRC_SEARCH_STUDENTS
--    Lưu ý alias không có dấu gạch dưới (STUDENT_CODE AS studentCode), Oracle sẽ
--    trả về nhãn cột STUDENTCODE / FULLNAME - RowMapper phía Java đọc đúng nhãn này.
--------------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE PRC_SEARCH_STUDENTS(
    P_KEYWORD    IN  VARCHAR2,
    O_CURSOR     OUT SYS_REFCURSOR,
    O_ERR_CODE   OUT VARCHAR2,
    O_ERR_MSG    OUT VARCHAR2
) AS
BEGIN
    OPEN O_CURSOR FOR
        SELECT ID,
               STUDENT_CODE AS studentCode,
               FULL_NAME    AS fullName,
               EMAIL,
               STATUS,
               DATE_OF_BIRTH AS dateOfBirth,
               PARENT_NAME   AS parentName,
               PHONE,
               ADDRESS,
               NOTE
        FROM EDU_STUDENTS
        WHERE (P_KEYWORD IS NULL
               OR LOWER(FULL_NAME) LIKE '%' || LOWER(P_KEYWORD) || '%'
               OR LOWER(STUDENT_CODE) LIKE '%' || LOWER(P_KEYWORD) || '%'
               OR LOWER(PARENT_NAME) LIKE '%' || LOWER(P_KEYWORD) || '%'
               OR LOWER(PHONE) LIKE '%' || LOWER(P_KEYWORD) || '%'
               OR LOWER(ADDRESS) LIKE '%' || LOWER(P_KEYWORD) || '%')
          AND IS_DELETED = 0;

    O_ERR_CODE := '0';
    O_ERR_MSG  := 'SUCCESS';
EXCEPTION
    WHEN OTHERS THEN
        O_ERR_CODE := TO_CHAR(SQLCODE);
        O_ERR_MSG  := SUBSTR(SQLERRM, 1, 255);
END PRC_SEARCH_STUDENTS;
/

--------------------------------------------------------------------------------
-- 10. STANDALONE PROCEDURE: PRC_GET_FILES_BY_REF
--     Cursor chỉ trả metadata phục vụ danh sách (không gồm FILE_PATH/STORED_NAME).
--------------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE PRC_GET_FILES_BY_REF(
    P_MODULE_NAME   IN  VARCHAR2,
    P_REFERENCE_ID  IN  NUMBER,
    O_CURSOR        OUT SYS_REFCURSOR,
    O_ERR_CODE      OUT VARCHAR2,
    O_ERR_MSG       OUT VARCHAR2
) AS
BEGIN
    OPEN O_CURSOR FOR
        SELECT ID, ORIGINAL_NAME AS originalName, CONTENT_TYPE AS contentType,
               FILE_SIZE AS fileSize, MODULE_NAME AS moduleName,
               REFERENCE_ID AS referenceId, CREATED_AT AS createdAt
        FROM SYS_ATTACHED_FILES
        WHERE MODULE_NAME = P_MODULE_NAME
          AND REFERENCE_ID = P_REFERENCE_ID
          AND IS_DELETED = 0
        ORDER BY CREATED_AT DESC;

    O_ERR_CODE := '0';
    O_ERR_MSG  := 'SUCCESS';
EXCEPTION
    WHEN OTHERS THEN
        O_ERR_CODE := TO_CHAR(SQLCODE);
        O_ERR_MSG  := SUBSTR(SQLERRM, 1, 255);
END PRC_GET_FILES_BY_REF;
/

--------------------------------------------------------------------------------
-- 11. STANDALONE PROCEDURE: PRC_GET_USER_SIDEBAR_MENU
--     Trả về 2 REF CURSOR: danh sách menu phẳng (Java tự gom thành cây theo
--     PARENT_ID) và danh sách quyền phẳng dạng MENU_CODE:ACTION.
--------------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE PRC_GET_USER_SIDEBAR_MENU(
    P_USER_ID               IN  NUMBER,
    O_MENU_CURSOR           OUT SYS_REFCURSOR,
    O_PERMISSIONS_CURSOR    OUT SYS_REFCURSOR,
    O_ERR_CODE              OUT VARCHAR2,
    O_ERR_MSG               OUT VARCHAR2
) AS
BEGIN
    -- 1. Trả về danh sách Menu được phép xem
    OPEN O_MENU_CURSOR FOR
        WITH USER_PERMITTED_MENUS AS (
            SELECT DISTINCT m.ID, m.PARENT_ID, m.MENU_CODE, m.MENU_NAME,
                            m.MENU_TYPE, m.PATH, m.ICON, m.SORT_ORDER,
                            rmp.ALLOWED_FUNCTIONS
            FROM SYS_MENUS m
            JOIN SYS_ROLE_MENU_PERMISSIONS rmp ON m.ID = rmp.MENU_ID
            JOIN SYS_USER_ROLES ur ON rmp.ROLE_ID = ur.ROLE_ID
            WHERE ur.USER_ID = P_USER_ID
              AND m.IS_DELETED = 0
              AND m.IS_HIDDEN = 0
              AND m.STATUS = 'ACTIVE'
        )
        SELECT ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, PATH, ICON, SORT_ORDER, ALLOWED_FUNCTIONS
        FROM USER_PERMITTED_MENUS
        ORDER BY SORT_ORDER ASC;

    -- 2. Trả về danh sách quyền phẳng MENU_CODE:ACTION
    OPEN O_PERMISSIONS_CURSOR FOR
        SELECT DISTINCT m.MENU_CODE || ':' || f.FUNCTION_CODE AS PERMISSION_KEY
        FROM SYS_ROLE_MENU_PERMISSIONS rmp
        JOIN SYS_USER_ROLES ur ON rmp.ROLE_ID = ur.ROLE_ID
        JOIN SYS_MENUS m ON rmp.MENU_ID = m.ID
        JOIN SYS_FUNCTIONS f ON m.ID = f.MENU_ID
        WHERE ur.USER_ID = P_USER_ID
          AND INSTR(',' || rmp.ALLOWED_FUNCTIONS || ',', ',' || f.FUNCTION_CODE || ',') > 0;

    O_ERR_CODE := '0';
    O_ERR_MSG  := 'SUCCESS';
EXCEPTION
    WHEN OTHERS THEN
        O_ERR_CODE := TO_CHAR(SQLCODE);
        O_ERR_MSG  := SUBSTR(SQLERRM, 1, 255);
END PRC_GET_USER_SIDEBAR_MENU;
/

--------------------------------------------------------------------------------
-- 12. DỮ LIỆU THAM CHIẾU
--     Menu, vai trò, người dùng và chức năng dùng ID tường minh để cấu hình
--     phân quyền được ổn định giữa các môi trường.
--------------------------------------------------------------------------------
INSERT INTO SYS_ROLES (ID, ROLE_CODE, ROLE_NAME, DESCRIPTION, STATUS, IS_DELETED)
VALUES (1, 'ROLE_ADMIN', 'Quản trị viên tối cao', 'Toàn quyền hệ thống', 'ACTIVE', 0);
INSERT INTO SYS_ROLES (ID, ROLE_CODE, ROLE_NAME, DESCRIPTION, STATUS, IS_DELETED)
VALUES (2, 'ROLE_TEACHER', 'Giảng viên', 'Quyền xem và quản lý học sinh', 'ACTIVE', 0);

-- Mat khau demo 'Education@123' bam SHA-256 (giong V1). V12 chuyen sang BCrypt:
-- admin -> 'Admin@123', teacher1 -> 'Education@123' (ca hai bat buoc doi mat khau).
INSERT INTO SYS_USERS (ID, USERNAME, PASSWORD_HASH, FULL_NAME, EMAIL, STATUS, IS_DELETED)
VALUES (1, 'admin', LOWER(RAWTOHEX(STANDARD_HASH('Education@123', 'SHA256'))),
        'Administrator', 'admin@education.com', 'ACTIVE', 0);
INSERT INTO SYS_USERS (ID, USERNAME, PASSWORD_HASH, FULL_NAME, EMAIL, STATUS, IS_DELETED)
VALUES (2, 'teacher1', LOWER(RAWTOHEX(STANDARD_HASH('Education@123', 'SHA256'))),
        'Giao Vien A', 'teacher@education.com', 'ACTIVE', 0);

INSERT INTO SYS_USER_ROLES (USER_ID, ROLE_ID) VALUES (1, 1);
INSERT INTO SYS_USER_ROLES (USER_ID, ROLE_ID) VALUES (2, 2);

INSERT INTO SYS_MENUS (ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, PATH, ICON, SORT_ORDER, IS_HIDDEN, STATUS, IS_DELETED)
VALUES (100, NULL, 'DIR_ACADEMIC', 'Quản lý Đào tạo', 'DIR', NULL, 'academic-cap', 1, 0, 'ACTIVE', 0);
INSERT INTO SYS_MENUS (ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, PATH, ICON, SORT_ORDER, IS_HIDDEN, STATUS, IS_DELETED)
VALUES (101, 100, 'MENU_STUDENT_LIST', 'Hồ sơ Học sinh', 'MENU', '/students/list', 'users', 1, 0, 'ACTIVE', 0);
INSERT INTO SYS_MENUS (ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, PATH, ICON, SORT_ORDER, IS_HIDDEN, STATUS, IS_DELETED)
VALUES (200, NULL, 'DIR_FINANCE', 'Tài chính & Học phí', 'DIR', NULL, 'cash', 2, 0, 'ACTIVE', 0);
INSERT INTO SYS_MENUS (ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, PATH, ICON, SORT_ORDER, IS_HIDDEN, STATUS, IS_DELETED)
VALUES (201, 200, 'MENU_TUITION_PAYMENT', 'Thu học phí VietQR', 'MENU', '/finance/vietqr', 'qrcode', 1, 0, 'ACTIVE', 0);
INSERT INTO SYS_MENUS (ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, PATH, ICON, SORT_ORDER, IS_HIDDEN, STATUS, IS_DELETED)
VALUES (300, NULL, 'DIR_SYSTEM', 'Cấu hình Hệ thống', 'DIR', NULL, 'cog', 3, 0, 'ACTIVE', 0);
INSERT INTO SYS_MENUS (ID, PARENT_ID, MENU_CODE, MENU_NAME, MENU_TYPE, PATH, ICON, SORT_ORDER, IS_HIDDEN, STATUS, IS_DELETED)
VALUES (301, 300, 'MENU_USER_PERM', 'Phân quyền & Menu', 'MENU', '/system/permissions', 'shield-check', 1, 0, 'ACTIVE', 0);

INSERT INTO SYS_FUNCTIONS (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
VALUES (1, 101, 'VIEW', 'Xem danh sách', 0);
INSERT INTO SYS_FUNCTIONS (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
VALUES (2, 101, 'CREATE', 'Thêm mới học sinh', 0);
INSERT INTO SYS_FUNCTIONS (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
VALUES (3, 101, 'UPDATE', 'Chỉnh sửa', 0);
INSERT INTO SYS_FUNCTIONS (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
VALUES (4, 101, 'DELETE', 'Xóa', 0);
INSERT INTO SYS_FUNCTIONS (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
VALUES (5, 101, 'EXPORT', 'Xuất Excel', 0);
INSERT INTO SYS_FUNCTIONS (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
VALUES (6, 201, 'VIEW', 'Xem hóa đơn', 0);
INSERT INTO SYS_FUNCTIONS (ID, MENU_ID, FUNCTION_CODE, FUNCTION_NAME, IS_DELETED)
VALUES (7, 201, 'GEN_QR', 'Sinh mã VietQR', 0);

INSERT INTO SYS_ROLE_MENU_PERMISSIONS (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS) VALUES (2, 1, 100, 'VIEW');
INSERT INTO SYS_ROLE_MENU_PERMISSIONS (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS) VALUES (3, 1, 101, 'VIEW,CREATE,UPDATE,DELETE,EXPORT');
INSERT INTO SYS_ROLE_MENU_PERMISSIONS (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS) VALUES (4, 1, 200, 'VIEW');
INSERT INTO SYS_ROLE_MENU_PERMISSIONS (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS) VALUES (5, 1, 201, 'VIEW,GEN_QR');
INSERT INTO SYS_ROLE_MENU_PERMISSIONS (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS) VALUES (6, 1, 300, 'VIEW');
INSERT INTO SYS_ROLE_MENU_PERMISSIONS (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS) VALUES (7, 1, 301, 'VIEW,CREATE,UPDATE,DELETE');
INSERT INTO SYS_ROLE_MENU_PERMISSIONS (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS) VALUES (8, 2, 100, 'VIEW');
INSERT INTO SYS_ROLE_MENU_PERMISSIONS (ID, ROLE_ID, MENU_ID, ALLOWED_FUNCTIONS) VALUES (9, 2, 101, 'VIEW,EXPORT');

INSERT INTO EDU_STUDENTS (ID, STUDENT_CODE, FULL_NAME, EMAIL, STATUS, IS_DELETED)
VALUES (1, 'SV001', 'Nguyen Van A', 'a.nguyen@education.com', 'ACTIVE', 0);
INSERT INTO EDU_STUDENTS (ID, STUDENT_CODE, FULL_NAME, EMAIL, STATUS, IS_DELETED)
VALUES (2, 'SV002', 'Tran Thi B', 'b.tran@education.com', 'ACTIVE', 0);

COMMIT;

--------------------------------------------------------------------------------
-- 13. KIỂM TRA NHANH
--------------------------------------------------------------------------------
SELECT OBJECT_NAME, OBJECT_TYPE, STATUS
  FROM USER_OBJECTS
 WHERE OBJECT_TYPE IN ('PROCEDURE', 'PACKAGE', 'PACKAGE BODY')
 ORDER BY OBJECT_TYPE, OBJECT_NAME;

DECLARE
    V_MENU_CURSOR SYS_REFCURSOR;
    V_PERM_CURSOR SYS_REFCURSOR;
    V_ERR_CODE    VARCHAR2(20);
    V_ERR_MSG     VARCHAR2(4000);
BEGIN
    PRC_GET_USER_SIDEBAR_MENU(1, V_MENU_CURSOR, V_PERM_CURSOR, V_ERR_CODE, V_ERR_MSG);
    DBMS_OUTPUT.PUT_LINE('PRC_GET_USER_SIDEBAR_MENU -> ' || V_ERR_CODE || ' / ' || V_ERR_MSG);
    CLOSE V_MENU_CURSOR;
    CLOSE V_PERM_CURSOR;
END;
/
