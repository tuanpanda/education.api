--------------------------------------------------------------------------------
-- EDUCATION PROJECT - DỌN ĐỐI TƯỢNG CỦA KIẾN TRÚC CŨ
--
-- PKG_STUDENT_TEST và PKG_FILE_OPERATIONS thuộc giai đoạn trước, khi tầng
-- Repository còn gọi Package. Toàn bộ mã nguồn hiện tại đã chuyển sang gọi
-- Standalone Procedure (PRC_SEARCH_STUDENTS, PRC_GET_FILES_BY_REF,
-- PRC_GET_USER_SIDEBAR_MENU), nên hai Package này không còn được tham chiếu và
-- đang ở trạng thái INVALID trong Data Dictionary.
--
-- Script an toàn khi chạy lại nhiều lần (bỏ qua lỗi ORA-04043 object không tồn tại).
--     sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @cleanup_legacy_packages.sql
--------------------------------------------------------------------------------

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE DROP_PACKAGE_IF_EXISTS(P_NAME VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'DROP PACKAGE ' || P_NAME;
        DBMS_OUTPUT.PUT_LINE('Đã xóa PACKAGE ' || P_NAME);
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE = -4043 THEN
                DBMS_OUTPUT.PUT_LINE('PACKAGE ' || P_NAME || ' không tồn tại, bỏ qua.');
            ELSE
                RAISE;
            END IF;
    END DROP_PACKAGE_IF_EXISTS;
BEGIN
    DROP_PACKAGE_IF_EXISTS('PKG_STUDENT_TEST');
    DROP_PACKAGE_IF_EXISTS('PKG_FILE_OPERATIONS');
END;
/

-- Kiểm tra lại: chỉ còn 3 Standalone Procedure, tất cả phải ở trạng thái VALID.
SELECT OBJECT_NAME, OBJECT_TYPE, STATUS
  FROM USER_OBJECTS
 WHERE OBJECT_TYPE IN ('PROCEDURE', 'PACKAGE', 'PACKAGE BODY')
 ORDER BY OBJECT_TYPE, OBJECT_NAME;

--------------------------------------------------------------------------------
-- Tùy chọn: các bảng của schema cũ vẫn nằm trong Recycle Bin (đối tượng BIN$...).
-- Bỏ chú thích dòng dưới nếu muốn giải phóng hẳn dung lượng. Lưu ý sau khi PURGE
-- sẽ không thể FLASHBACK các bảng đã xóa trước đó.
--------------------------------------------------------------------------------
-- PURGE RECYCLEBIN;
