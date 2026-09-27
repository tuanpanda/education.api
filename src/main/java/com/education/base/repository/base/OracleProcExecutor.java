package com.education.base.repository.base;

import com.education.base.exception.OracleBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Bean trung tâm bọc {@link SimpleJdbcCall} để gọi Standalone Procedure của Oracle (PL/SQL).
 * <p>
 * Toàn bộ Repository Custom Implementation trong hệ thống EDUCATION PHẢI sử dụng bean này
 * để đảm bảo tính thống nhất khi tạo lời gọi Procedure và validate kết quả trả về.
 * <p>
 * Convention tham số Out của Procedure:
 * <ul>
 *     <li>{@code O_ERR_CODE} (VARCHAR2): {@code '0'} là thành công, khác {@code '0'} là lỗi.</li>
 *     <li>{@code O_ERR_MSG} (VARCHAR2): mô tả lỗi khi {@code O_ERR_CODE != '0'}.</li>
 *     <li>{@code O_CURSOR} (SYS_REFCURSOR): con trỏ dữ liệu trả về (nếu có).</li>
 * </ul>
 */
@Slf4j
@Component
public class OracleProcExecutor {

    /**
     * Mã lỗi quy ước khi Procedure thực thi thành công.
     */
    public static final String SUCCESS_ERR_CODE = "0";

    private final DataSource dataSource;

    public OracleProcExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Tạo một {@link SimpleJdbcCall} để gọi Standalone Procedure {@code procedureName}.
     * <p>
     * Không dùng {@code withCatalogName(...)} vì Database sử dụng Standalone Procedure
     * (procedure đứng độc lập, không nằm trong Package). Dùng
     * {@code withoutProcedureColumnMetaDataAccess()} để tránh việc Spring JDBC truy vấn
     * metadata của Oracle Data Dictionary mỗi lần gọi, tăng hiệu năng và độ ổn định.
     *
     * @param procedureName tên Standalone Procedure (ví dụ: {@code PRC_SEARCH_STUDENTS}).
     * @return {@link SimpleJdbcCall} đã cấu hình, sẵn sàng để {@code declareParameters(...)}.
     */
    public SimpleJdbcCall createCall(String procedureName) {
        return new SimpleJdbcCall(dataSource)
                .withProcedureName(procedureName)
                .withoutProcedureColumnMetaDataAccess();
    }

    /**
     * Kiểm tra kết quả trả về từ Procedure dựa trên tham số Out quy ước {@code O_ERR_CODE}.
     * Nếu {@code O_ERR_CODE} khác {@code '0'}, ném {@link OracleBusinessException} với thông tin
     * lấy từ {@code O_ERR_CODE} và {@code O_ERR_MSG}.
     *
     * @param out Map kết quả trả về từ {@link SimpleJdbcCall#execute(Map)}.
     * @throws OracleBusinessException nếu {@code O_ERR_CODE} khác {@code '0'}.
     */
    public void validateResult(Map<String, Object> out) {
        if (out == null) {
            log.error("Kết quả trả về từ Oracle Procedure là null.");
            throw new OracleBusinessException("-1", "Không nhận được kết quả từ Oracle Procedure.");
        }

        Object errCodeObj = out.get("O_ERR_CODE");
        String errCode = errCodeObj != null ? String.valueOf(errCodeObj).trim() : null;

        if (errCode == null || errCode.isBlank()) {
            log.error("Thiếu O_ERR_CODE trong kết quả Oracle Procedure.");
            throw new OracleBusinessException("-1", "Thiếu O_ERR_CODE từ Oracle Procedure.");
        }

        if (!SUCCESS_ERR_CODE.equals(errCode)) {
            Object errMsgObj = out.get("O_ERR_MSG");
            String errMsg = errMsgObj != null ? String.valueOf(errMsgObj) : "Lỗi xử lý dữ liệu tại Oracle Procedure.";
            log.error("Oracle Procedure trả về lỗi: errorCode={}, message={}", errCode, errMsg);
            throw new OracleBusinessException(errCode, errMsg);
        }
    }
}
