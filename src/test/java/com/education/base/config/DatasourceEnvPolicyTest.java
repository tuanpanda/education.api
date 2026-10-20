package com.education.base.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

class DatasourceEnvPolicyTest {

    private static final String USER_VALUE = "SOME_SCHEMA_USER";
    private static final String PWD_VALUE = "not-a-real-secret-xyz";

    @Test
    void missingPassword_throwsNamingVariable_withoutValues() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.username", USER_VALUE);
        assertThatThrownBy(() -> DatasourceEnvPolicy.validate(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageNotContaining("DB_USERNAME")
                .hasMessageNotContaining(USER_VALUE);
    }

    @Test
    void missingUsername_throwsNamingVariable_withoutValues() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.password", PWD_VALUE);
        assertThatThrownBy(() -> DatasourceEnvPolicy.validate(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_USERNAME")
                .hasMessageNotContaining("DB_PASSWORD")
                .hasMessageNotContaining(PWD_VALUE);
    }

    @Test
    void unresolvedPlaceholdersAndBlank_countAsMissing() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.username", "${DB_USERNAME}")
                .withProperty("spring.datasource.password", "   ");
        assertThatThrownBy(() -> DatasourceEnvPolicy.validate(env))
                .hasMessageContaining("DB_USERNAME")
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void bothPresent_ok() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.username", USER_VALUE)
                .withProperty("spring.datasource.password", PWD_VALUE);
        assertThatCode(() -> DatasourceEnvPolicy.validate(env)).doesNotThrowAnyException();
    }

    @Test
    void testProfile_skipped() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        assertThatCode(() -> DatasourceEnvPolicy.validate(env)).doesNotThrowAnyException();
    }

    @Test
    void startupValidator_checksOnlyDataSourceBeans() {
        DatasourceEnvStartupValidator validator = new DatasourceEnvStartupValidator();
        validator.setEnvironment(new MockEnvironment());
        Object other = new Object();
        assertThat(validator.postProcessBeforeInitialization(other, "other")).isSameAs(other);
        DataSource ds = Mockito.mock(DataSource.class);
        assertThatThrownBy(() -> validator.postProcessBeforeInitialization(ds, "dataSource"))
                .hasMessageContaining("DB_USERNAME")
                .hasMessageContaining("DB_PASSWORD");
    }
}