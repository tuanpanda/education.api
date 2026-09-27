package com.education.base.repository.custom.impl;

import com.education.base.dto.request.TimetableFilterRequest;
import com.education.base.dto.response.TimetableItemDto;
import com.education.base.repository.base.OracleProcExecutor;
import com.education.base.repository.custom.TimetableRepositoryCustom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oracle.jdbc.OracleTypes;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gọi {@code PRC_GET_TIMETABLE_BY_RANGE} qua {@link OracleProcExecutor}.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TimetableRepositoryCustomImpl implements TimetableRepositoryCustom {

    private static final String PROC_NAME = "PRC_GET_TIMETABLE_BY_RANGE";

    private static final RowMapper<TimetableItemDto> ROW_MAPPER = (rs, rowNum) -> TimetableItemDto.builder()
            .id(rs.getLong("ID"))
            .classId(JdbcValueReaders.getLong(rs, "CLASS_ID"))
            .classCode(rs.getString("CLASS_CODE"))
            .className(rs.getString("CLASS_NAME"))
            .scheduleId(JdbcValueReaders.getLong(rs, "SCHEDULE_ID"))
            .sessionDate(JdbcValueReaders.getLocalDate(rs, "SESSION_DATE"))
            .dayOfWeek(JdbcValueReaders.getInteger(rs, "DAY_OF_WEEK"))
            .startTime(rs.getString("START_TIME"))
            .endTime(rs.getString("END_TIME"))
            .roomName(rs.getString("ROOM_NAME"))
            .teacherId(JdbcValueReaders.getLong(rs, "TEACHER_ID"))
            .teacherName(rs.getString("TEACHER_NAME"))
            .topic(rs.getString("TOPIC"))
            .status(rs.getString("STATUS"))
            .note(rs.getString("NOTE"))
            .build();

    private final OracleProcExecutor oracleProcExecutor;

    @Override
    public List<TimetableItemDto> findTimetableByRange(TimetableFilterRequest filter) {
        TimetableFilterRequest criteria = filter == null ? new TimetableFilterRequest() : filter;

        SimpleJdbcCall call = oracleProcExecutor.createCall(PROC_NAME)
                .declareParameters(
                        new SqlParameter("P_FROM_DATE", Types.DATE),
                        new SqlParameter("P_TO_DATE", Types.DATE),
                        new SqlParameter("P_CLASS_ID", Types.NUMERIC),
                        new SqlParameter("P_TEACHER_ID", Types.NUMERIC),
                        new SqlParameter("P_STUDENT_ID", Types.NUMERIC),
                        new SqlOutParameter("O_CURSOR", OracleTypes.CURSOR, ROW_MAPPER),
                        new SqlOutParameter("O_ERR_CODE", Types.VARCHAR),
                        new SqlOutParameter("O_ERR_MSG", Types.VARCHAR));

        Map<String, Object> in = new HashMap<>();
        in.put("P_FROM_DATE", toSqlDate(criteria.getFromDate()));
        in.put("P_TO_DATE", toSqlDate(criteria.getToDate()));
        in.put("P_CLASS_ID", criteria.getClassId());
        in.put("P_TEACHER_ID", criteria.getTeacherId());
        in.put("P_STUDENT_ID", criteria.getStudentId());

        Map<String, Object> out = call.execute(in);
        oracleProcExecutor.validateResult(out);

        List<TimetableItemDto> rows = ProcCursorReader.readCursor(out, "O_CURSOR", TimetableItemDto.class);
        log.debug("{} trả về {} buổi (from={}, to={}, classId={}, teacherId={}, studentId={})",
                PROC_NAME, rows.size(), criteria.getFromDate(), criteria.getToDate(),
                criteria.getClassId(), criteria.getTeacherId(), criteria.getStudentId());
        return rows;
    }

    private java.sql.Date toSqlDate(LocalDate date) {
        return date == null ? null : java.sql.Date.valueOf(date);
    }
}
