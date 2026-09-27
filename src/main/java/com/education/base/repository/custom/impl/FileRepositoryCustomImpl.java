package com.education.base.repository.custom.impl;

import com.education.base.entity.FileEntity;
import com.education.base.repository.base.OracleProcExecutor;
import com.education.base.repository.custom.FileRepositoryCustom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oracle.jdbc.OracleTypes;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Triển khai {@link FileRepositoryCustom} bằng Standalone Procedure
 * {@code PRC_GET_FILES_BY_REF(P_MODULE_NAME, P_REFERENCE_ID, O_CURSOR, O_ERR_CODE, O_ERR_MSG)}.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FileRepositoryCustomImpl implements FileRepositoryCustom {

    private static final String PROC_NAME = "PRC_GET_FILES_BY_REF";

    /**
     * Procedure dùng alias không dấu gạch dưới, nên Oracle trả về nhãn cột dạng
     * {@code ORIGINALNAME}, {@code CONTENTTYPE}, {@code FILESIZE}...
     */
    private static final RowMapper<FileEntity> FILE_ROW_MAPPER = (rs, rowNum) -> {
        Timestamp createdAt = rs.getTimestamp("CREATEDAT");
        Object referenceId = rs.getObject("REFERENCEID");
        return FileEntity.builder()
                .id(rs.getLong("ID"))
                .originalName(rs.getString("ORIGINALNAME"))
                .contentType(rs.getString("CONTENTTYPE"))
                .fileSize(rs.getLong("FILESIZE"))
                .moduleName(rs.getString("MODULENAME"))
                .referenceId(referenceId == null ? null : rs.getLong("REFERENCEID"))
                .isDeleted(0)
                .createdAt(createdAt == null ? null : createdAt.toLocalDateTime())
                .build();
    };

    private final OracleProcExecutor oracleProcExecutor;

    @Override
    public List<FileEntity> getFilesByRef(String moduleName, Long referenceId) {
        SimpleJdbcCall call = oracleProcExecutor.createCall(PROC_NAME)
                .declareParameters(
                        new SqlParameter("P_MODULE_NAME", Types.VARCHAR),
                        new SqlParameter("P_REFERENCE_ID", Types.NUMERIC),
                        new SqlOutParameter("O_CURSOR", OracleTypes.CURSOR, FILE_ROW_MAPPER),
                        new SqlOutParameter("O_ERR_CODE", Types.VARCHAR),
                        new SqlOutParameter("O_ERR_MSG", Types.VARCHAR));

        Map<String, Object> in = new HashMap<>();
        in.put("P_MODULE_NAME", moduleName);
        in.put("P_REFERENCE_ID", referenceId);

        Map<String, Object> out = call.execute(in);
        oracleProcExecutor.validateResult(out);

        List<FileEntity> files = ProcCursorReader.readCursor(out, "O_CURSOR", FileEntity.class);
        log.debug("{} trả về {} file cho module='{}', referenceId={}", PROC_NAME, files.size(), moduleName, referenceId);
        return files;
    }
}
