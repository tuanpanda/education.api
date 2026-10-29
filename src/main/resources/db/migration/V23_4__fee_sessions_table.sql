-- =============================================================================
-- MIGRATION V23_4__fee_sessions_table: THU HOC PHI THEO KHOANG NGAY
--
-- Bang FIN_TUITION_FEE_SESSIONS: moi buoi diem danh (PRESENT/LATE) da tinh tren 1 phieu = 1 dong.
-- UQ_FEE_SESSIONS_ATTENDANCE: 1 buoi chi nam tren toi da 1 phieu con hieu luc (huy/xoa phieu -> xoa dong).
-- Kieu ID khop FIN_TUITION_FEES.ID / EDU_ATTENDANCE.ID = NUMBER(19) (V1). Dung tablespace mac dinh cua user.
--
-- Spec: docs/SPEC-fee-date-range.md muc 4. Huong dan chay: docs/RUN-V23-fee-range.md
-- Moi file 1 khoi lenh. Chay lai an toan (idempotent).
--
-- THU TU CHAY: sau V23_3.
--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
--   F:\Database\bin\sqlplus.exe /nolog
--   CONNECT <DB_USER>@//localhost:1521/<SERVICE>   (mat khau nhap tai prompt)
--   @V23_4__fee_sessions_table.sql
--
-- ROLLBACK:
--   DROP TABLE FIN_TUITION_FEE_SESSIONS;
-- =============================================================================

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED

BEGIN
    EXECUTE IMMEDIATE q'[CREATE TABLE FIN_TUITION_FEE_SESSIONS (
        ID            NUMBER(19)   NOT NULL,
        FEE_ID        NUMBER(19)   NOT NULL,
        ATTENDANCE_ID NUMBER(19)   NOT NULL,
        SESSION_DATE  DATE         NOT NULL,
        CREATED_AT    TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
        CREATED_BY    VARCHAR2(50),
        CONSTRAINT PK_FIN_TUITION_FEE_SESSIONS PRIMARY KEY (ID),
        CONSTRAINT FK_FEE_SESSIONS_FEE FOREIGN KEY (FEE_ID) REFERENCES FIN_TUITION_FEES (ID),
        CONSTRAINT FK_FEE_SESSIONS_ATT FOREIGN KEY (ATTENDANCE_ID) REFERENCES EDU_ATTENDANCE (ID),
        CONSTRAINT UQ_FEE_SESSIONS_ATTENDANCE UNIQUE (ATTENDANCE_ID)
    )]';
    DBMS_OUTPUT.PUT_LINE('FIN_TUITION_FEE_SESSIONS: created');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE IN (-955, -2261, -2264, -2275) THEN
            DBMS_OUTPUT.PUT_LINE('FIN_TUITION_FEE_SESSIONS: already exists');
        ELSE
            RAISE;
        END IF;
END;
/
