package com.education.base.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code APP_INTERNET_FACING=true}: không khởi động nếu cookie không Secure hoặc CORS có origin http://.
 * Mặc định (false) không chặn gì - Docker cục bộ chạy như cũ.
 */
class InternetExposureValidatorTest {

    private static AuthCookieProperties cookie(boolean secure) {
        AuthCookieProperties properties = new AuthCookieProperties();
        properties.setSecure(secure);
        return properties;
    }

    private static void validate(MockEnvironment environment, boolean secureCookie, List<String> origins) {
        new InternetExposureValidator(environment, cookie(secureCookie), new CorsProperties(origins))
                .afterPropertiesSet();
    }

    @Test
    void localDefaults_neverBlockStartup() {
        assertThatCode(() -> validate(new MockEnvironment(), false, List.of("http://192.168.1.10:8088")))
                .doesNotThrowAnyException();

        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatCode(() -> validate(prod, false, List.of("http://localhost:5173")))
                .doesNotThrowAnyException();
    }

    @Test
    void internetFacing_requiresSecureCookie() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(InternetExposureValidator.PROPERTY, "true");

        assertThatThrownBy(() -> validate(environment, false, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_COOKIE_SECURE");
    }

    @Test
    void internetFacing_rejectsPlainHttpOrigins() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(InternetExposureValidator.PROPERTY, "true");

        assertThatThrownBy(() -> validate(environment, true,
                List.of("https://hocsinh.trungtam.vn", "http://hocsinh.trungtam.vn")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS")
                .hasMessageContaining("http://hocsinh.trungtam.vn");
    }

    @Test
    void internetFacing_acceptsSameOriginOrHttpsOnly() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(InternetExposureValidator.PROPERTY, "true")
                .withProperty("server.forward-headers-strategy", "native")
                .withProperty("springdoc.api-docs.enabled", "false");

        assertThatCode(() -> validate(environment, true, List.of())).doesNotThrowAnyException();
        assertThatCode(() -> validate(environment, true, List.of("HTTPS://hocsinh.trungtam.vn")))
                .doesNotThrowAnyException();
    }
}
