package com.education.base.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm tra JWT secret khi khởi động: tối thiểu 32 byte, cấm secret dev/test ở profile prod.
 */
class JwtSecretPolicyTest {

    private static final String DEV_SECRET = "dev-only-education-jwt-secret-change-me-0123456789";
    private static final String TEST_SECRET = "test-only-education-jwt-secret-0123456789abcdef";
    private static final String STRONG_SECRET = "Zq3u8x0Lr9vN2mK7pW4sT6yB1cF5hJ8dG0aE3iO7uQ2=";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ValidatorConfig.class);

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    @Import(JwtSecretStartupValidator.class)
    static class ValidatorConfig {
    }

    @Test
    void secretShorterThan32Bytes_isRejected() {
        assertThatThrownBy(() -> JwtSecretPolicy.validate("0123456789012345678901234567890", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
        assertThatThrownBy(() -> JwtSecretPolicy.validate(null, false)).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> JwtSecretPolicy.validate("01234567890123456789012345678901", false))
                .doesNotThrowAnyException();
    }

    @Test
    void lengthIsMeasuredInUtf8Bytes() {
        // 16 ký tự có dấu x 2 byte = 32 byte -> hợp lệ; 15 ký tự = 30 byte -> không hợp lệ.
        assertThatCode(() -> JwtSecretPolicy.requireMinimumLength("ăăăăăăăăăăăăăăăă")).doesNotThrowAnyException();
        assertThatThrownBy(() -> JwtSecretPolicy.requireMinimumLength("ăăăăăăăăăăăăăăă"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void knownDevOrTestSecret_isRejectedOnlyInProd() {
        assertThatThrownBy(() -> JwtSecretPolicy.validate(DEV_SECRET, true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("prod");
        assertThatThrownBy(() -> JwtSecretPolicy.validate(TEST_SECRET, true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtSecretPolicy.validate(
                "replace-with-a-random-secret-of-at-least-32-characters", true))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> JwtSecretPolicy.validate(DEV_SECRET, false)).doesNotThrowAnyException();
        assertThatCode(() -> JwtSecretPolicy.validate(STRONG_SECRET, true)).doesNotThrowAnyException();
    }

    @Test
    void startup_prodProfileWithDevSecret_failsContext() {
        runner.withPropertyValues("app.security.jwt.secret=" + DEV_SECRET)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("prod");
                });
    }

    @Test
    void startup_shortSecret_failsContextInAnyProfile() {
        runner.withPropertyValues("app.security.jwt.secret=short-secret-123")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("32 byte");
                });
    }

    @Test
    void startup_devSecretOutsideProd_andStrongSecretInProd_start() {
        runner.withPropertyValues("app.security.jwt.secret=" + DEV_SECRET)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .run(context -> assertThat(context).hasNotFailed());
        runner.withPropertyValues("app.security.jwt.secret=" + STRONG_SECRET)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).hasNotFailed());
    }
}
