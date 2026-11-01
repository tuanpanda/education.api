package com.education.base.repository.custom.impl;

import com.education.base.repository.base.OracleProcExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Tra kỳ thu cho báo cáo nợ / sổ học sinh: chia lô, không vượt 1000 phần tử trong IN (ORA-01795). */
class ReportRepositoryImplFeePeriodsTest {

    @Test
    void findFeePeriods_2000Ids_splitIntoChunksOfAtMost900() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReportRepositoryImpl repo = new ReportRepositoryImpl(mock(OracleProcExecutor.class), jdbc);
        List<Long> ids = new ArrayList<>(LongStream.rangeClosed(1, 2000).boxed().toList());
        ids.add(5L);
        ids.add(null);

        repo.findFeePeriods(ids);

        List<Invocation> calls = Mockito.mockingDetails(jdbc).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("query")).toList();
        assertThat(calls).hasSize(3);
        for (Invocation call : calls) {
            String sql = call.getArgument(0);
            long marks = sql.chars().filter(ch -> ch == '?').count();
            assertThat(marks).isBetween(1L, 900L);
            assertThat(sql).contains("PERIOD_FROM").contains("PERIOD_TO");
        }
    }

    @Test
    void findFeePeriods_emptyList_noQuery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new ReportRepositoryImpl(mock(OracleProcExecutor.class), jdbc).findFeePeriods(List.of());
        Mockito.verifyNoInteractions(jdbc);
    }
}
