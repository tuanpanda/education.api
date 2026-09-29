package com.education.base.integration;

import com.education.base.dto.request.StudentFilterRequest;
import com.education.base.dto.response.PageResponse;
import com.education.base.dto.response.StudentReportDto;
import com.education.base.exception.OracleBusinessException;
import com.education.base.mapper.FileMapperImpl;
import com.education.base.mapper.StudentMapperImpl;
import com.education.base.repository.AttendanceRepository;
import com.education.base.repository.ClassRepository;
import com.education.base.repository.ClassStudentRepository;
import com.education.base.repository.StudentRepository;
import com.education.base.repository.UserRepository;
import com.education.base.repository.base.OracleProcExecutor;
import com.education.base.repository.custom.impl.StudentRepositoryCustomImpl;
import com.education.base.service.FileStorageService;
import com.education.base.service.impl.StudentServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Smoke test luồng tìm kiếm học sinh đi qua Standalone Procedure
 * {@code PRC_SEARCH_STUDENTS} / {@code PRC_SEARCH_STUDENTS_PAGING} và {@code validateResult}.
 */
@ExtendWith(MockitoExtension.class)
class StudentIntegrationTest {

    private static final AbstractDataSource STUB_DS = new AbstractDataSource() {
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("Stub DataSource - smoke test không kết nối Oracle.");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("Stub DataSource - smoke test không kết nối Oracle.");
        }
    };

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private ClassRepository classRepository;

    @Mock
    private ClassStudentRepository classStudentRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private EntityManager entityManager;

    private StubJdbcCall jdbcCall;
    private RecordingProcExecutor oracleProcExecutor;
    private StudentRepositoryCustomImpl studentRepositoryCustom;
    private StudentServiceImpl studentService;

    @BeforeEach
    void setUp() {
        jdbcCall = new StubJdbcCall();
        oracleProcExecutor = new RecordingProcExecutor(jdbcCall);
        studentRepositoryCustom = new StudentRepositoryCustomImpl(oracleProcExecutor);
        studentService = new StudentServiceImpl(
                studentRepository, classRepository, classStudentRepository, attendanceRepository, userRepository,
                fileStorageService, new StudentMapperImpl(), new FileMapperImpl(),
                entityManager);
    }

    @Test
    void searchWithPaging_callsPagingProcedureThenValidateResult() {
        StudentReportDto row = StudentReportDto.builder()
                .id(15L)
                .studentCode("SV015")
                .fullName("Nguyen Van Smoke")
                .email("smoke@education.com")
                .status("ACTIVE")
                .build();

        Map<String, Object> out = new HashMap<>();
        out.put("O_ERR_CODE", OracleProcExecutor.SUCCESS_ERR_CODE);
        out.put("O_ERR_MSG", "SUCCESS");
        out.put("O_TOTAL_ROWS", 1L);
        out.put("O_DATA_CURSOR", List.of(row));
        jdbcCall.returning(out);

        StudentFilterRequest filter = StudentFilterRequest.builder()
                .keyword("SV015")
                .status("ACTIVE")
                .pageNo(1)
                .pageSize(20)
                .build();

        PageResponse<StudentReportDto> page = studentRepositoryCustom.searchWithPaging(filter);

        assertThat(oracleProcExecutor.lastProcedure).isEqualTo("PRC_SEARCH_STUDENTS_PAGING");
        assertThat(jdbcCall.lastIn)
                .containsEntry("P_KEYWORD", "SV015")
                .containsEntry("P_STATUS", "ACTIVE")
                .containsEntry("P_PAGE_NO", 1)
                .containsEntry("P_PAGE_SIZE", 20);
        assertThat(page.getTotalRows()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getStudentCode()).isEqualTo("SV015");
        assertThat(page.getContent().getFirst().getFullName()).isEqualTo("Nguyen Van Smoke");
    }

    @Test
    void searchWithPaging_alwaysSendsActiveStatus() {
        Map<String, Object> out = new HashMap<>();
        out.put("O_ERR_CODE", OracleProcExecutor.SUCCESS_ERR_CODE);
        out.put("O_ERR_MSG", "SUCCESS");
        out.put("O_TOTAL_ROWS", 0L);
        out.put("O_DATA_CURSOR", List.of());
        jdbcCall.returning(out);

        studentRepositoryCustom.searchWithPaging(StudentFilterRequest.builder()
                .status("INACTIVE")
                .pageNo(1)
                .pageSize(20)
                .build());

        assertThat(jdbcCall.lastIn).containsEntry("P_STATUS", "ACTIVE");
    }

    @Test
    void searchStudents_callsLegacyProcedureThenValidateResult() {
        StudentReportDto row = StudentReportDto.builder()
                .id(2L)
                .studentCode("SV002")
                .fullName("Tran Thi B")
                .status("ACTIVE")
                .build();
        Map<String, Object> out = new HashMap<>();
        out.put("O_ERR_CODE", "0");
        out.put("O_CURSOR", List.of(row));
        jdbcCall.returning(out);

        List<StudentReportDto> result = studentRepositoryCustom.searchStudents("Tran");

        assertThat(oracleProcExecutor.lastProcedure).isEqualTo("PRC_SEARCH_STUDENTS");
        assertThat(jdbcCall.lastIn).isEqualTo(Map.of("P_KEYWORD", "Tran"));
        assertThat(result).extracting(StudentReportDto::getStudentCode).containsExactly("SV002");
    }

    @Test
    void searchWithPaging_whenProcReturnsBusinessError_throwsOracleBusinessException() {
        Map<String, Object> out = new HashMap<>();
        out.put("O_ERR_CODE", "INVALID_PAGE");
        out.put("O_ERR_MSG", "Số trang không hợp lệ.");
        jdbcCall.returning(out);

        assertThatThrownBy(() -> studentRepositoryCustom.searchWithPaging(
                StudentFilterRequest.builder().pageNo(1).pageSize(10).build()))
                .isInstanceOf(OracleBusinessException.class)
                .satisfies(ex -> {
                    OracleBusinessException business = (OracleBusinessException) ex;
                    assertThat(business.getErrorCode()).isEqualTo("INVALID_PAGE");
                    assertThat(business.getMessage()).isEqualTo("Số trang không hợp lệ.");
                });
    }

    @Test
    void studentServiceSearch_delegatesToPagingProcedureRepository() {
        StudentFilterRequest filter = StudentFilterRequest.builder().keyword("SV").pageNo(1).pageSize(10).build();
        PageResponse<StudentReportDto> expected = PageResponse.of(List.of(), 1, 10, 0);
        when(studentRepository.searchWithPaging(filter)).thenReturn(expected);

        assertThat(studentService.search(filter)).isSameAs(expected);
        verify(studentRepository).searchWithPaging(filter);
    }

    private static final class StubJdbcCall extends SimpleJdbcCall {
        private Map<String, Object> out = Map.of();
        private Map<String, Object> lastIn = Map.of();

        private StubJdbcCall() {
            super(STUB_DS);
        }

        private StubJdbcCall returning(Map<String, Object> out) {
            this.out = out;
            return this;
        }

        @Override
        public SimpleJdbcCall declareParameters(SqlParameter... parameters) {
            return this;
        }

        @Override
        public Map<String, Object> execute(Map<String, ?> inParams) {
            Map<String, Object> copied = new HashMap<>();
            if (inParams != null) {
                copied.putAll(inParams);
            }
            this.lastIn = copied;
            return out;
        }
    }

    private static final class RecordingProcExecutor extends OracleProcExecutor {
        private final StubJdbcCall call;
        private String lastProcedure;

        private RecordingProcExecutor(StubJdbcCall call) {
            super(STUB_DS);
            this.call = call;
        }

        @Override
        public SimpleJdbcCall createCall(String procedureName) {
            lastProcedure = procedureName;
            return call;
        }
    }
}
