package com.education.base.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cấu hình thật trong {@code application.yml} / {@code application-prod.yml}: quy tắc giới hạn tần suất bind đúng,
 * ghi đè được bằng biến môi trường; profile prod giữ cookie Secure, tắt Swagger và không lộ lỗi nội bộ.
 */
class RateLimitConfigurationTest {

    private static StandardEnvironment environment(Map<String, Object> env, String... files) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        // Không phụ thuộc biến môi trường / system property của máy chạy test.
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("test-env", env));
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String file : files) {
            // File sau ưu tiên hơn file trước (như profile ghi đè application.yml); biến môi trường ưu tiên nhất.
            for (PropertySource<?> source : loader.load(file, new ClassPathResource(file))) {
                environment.getPropertySources().addAfter("test-env", source);
            }
        }
        return environment;
    }

    private static RateLimitProperties bind(StandardEnvironment environment) {
        return Binder.get(environment).bind("app.security.rate-limit", RateLimitProperties.class)
                .orElseThrow(() -> new AssertionError("không bind được app.security.rate-limit"));
    }

    @Test
    void defaultRules_coverChangePasswordPortalAndUploads() throws IOException {
        RateLimitProperties properties = bind(environment(Map.of(), "application.yml"));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getMaxTrackedKeys()).isEqualTo(100_000);
        assertThat(properties.getRules()).containsOnlyKeys("change-password", "portal", "upload");

        RateLimitProperties.Rule changePassword = properties.getRules().get("change-password");
        assertThat(changePassword.getPaths()).containsExactly("/api/v1/auth/change-password");
        assertThat(changePassword.getMethods()).containsExactly("POST");
        assertThat(changePassword.getWindow()).isEqualTo(Duration.ofMinutes(15));
        assertThat(changePassword.getPerIp()).isEqualTo(30);
        assertThat(changePassword.getPerUser()).isEqualTo(5);

        RateLimitProperties.Rule portal = properties.getRules().get("portal");
        assertThat(portal.getPaths()).containsExactly("/api/v1/portal/**");
        assertThat(portal.getMethods()).isEmpty();
        assertThat(portal.getPerIp()).isZero();
        assertThat(portal.getPerUser()).isEqualTo(300);
        assertThat(portal.getWindow()).isEqualTo(Duration.ofMinutes(5));

        RateLimitProperties.Rule upload = properties.getRules().get("upload");
        assertThat(upload.getPaths()).containsExactly("/api/**");
        assertThat(upload.getMethods()).containsExactly("POST", "PUT", "PATCH");
        assertThat(upload.isMultipartOnly()).isTrue();
        assertThat(upload.getWindow()).isEqualTo(Duration.ofHours(1));
        assertThat(upload.getPerIp()).isEqualTo(300);
        assertThat(upload.getPerUser()).isEqualTo(60);
    }

    @Test
    void loginLimits_wideForSharedCenterIp_strictPerUsername() throws IOException {
        AuthSecurityProperties.RateLimit fromYaml = Binder.get(environment(Map.of(), "application.yml"))
                .bind("app.security.auth.rate-limit", AuthSecurityProperties.RateLimit.class)
                .orElseThrow(() -> new AssertionError("không bind được app.security.auth.rate-limit"));

        // Cả trung tâm dùng chung một IP public (NAT): cả lớp đăng nhập cùng lúc không được chạm ngưỡng theo IP.
        assertThat(fromYaml.getLoginPerIp()).isEqualTo(200);
        assertThat(fromYaml.getWindow()).isEqualTo(Duration.ofMinutes(5));
        // Từng tài khoản vẫn được bảo vệ chặt.
        assertThat(fromYaml.getLoginPerUsername()).isEqualTo(10);
        // Mặc định trong code trùng application.yml (chạy không có file cấu hình).
        AuthSecurityProperties.RateLimit codeDefaults = new AuthSecurityProperties().getRateLimit();
        assertThat(codeDefaults.getLoginPerIp()).isEqualTo(200);
        assertThat(codeDefaults.getLoginPerUsername()).isEqualTo(10);

        AuthSecurityProperties.RateLimit overridden = Binder.get(environment(Map.of(
                        "AUTH_RATE_LIMIT_LOGIN_PER_IP", "500"), "application.yml"))
                .bind("app.security.auth.rate-limit", AuthSecurityProperties.RateLimit.class)
                .orElseThrow(() -> new AssertionError("không bind được app.security.auth.rate-limit"));
        assertThat(overridden.getLoginPerIp()).isEqualTo(500);
        assertThat(overridden.getLoginPerUsername()).isEqualTo(10);
    }

    @Test
    void environmentVariablesOverrideBudgets() throws IOException {
        RateLimitProperties properties = bind(environment(Map.of(
                "RATE_LIMIT_PORTAL_PER_USER", "50",
                "RATE_LIMIT_PORTAL_PER_IP", "2000",
                "RATE_LIMIT_UPLOAD_WINDOW", "10m",
                "RATE_LIMIT_ENABLED", "false"), "application.yml"));

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getRules().get("portal").getPerUser()).isEqualTo(50);
        assertThat(properties.getRules().get("portal").getPerIp()).isEqualTo(2000);
        assertThat(properties.getRules().get("upload").getWindow()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void localDefaults_areNotInternetFacing_andAuditIsOn() throws IOException {
        StandardEnvironment environment = environment(Map.of("JWT_SECRET", "x"), "application.yml",
                "application-prod.yml");

        assertThat(environment.getProperty("app.security.internet-facing", Boolean.class)).isFalse();
        assertThat(environment.getProperty("app.audit.enabled", Boolean.class)).isTrue();
        // Docker cục bộ (profile prod) không đổi hành vi: vẫn same-origin, cookie Secure mặc định.
        assertThat(environment.getProperty("app.cors.allowed-origins")).isEmpty();
        assertThat(environment.getProperty("app.security.cookie.secure", Boolean.class)).isTrue();
    }

    @Test
    void prodProfile_hidesInternalsAndApiDocs() throws IOException {
        StandardEnvironment environment = environment(Map.of("JWT_SECRET", "x"), "application.yml",
                "application-prod.yml");

        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(environment.getProperty("server.error.include-message")).isEqualTo("never");
        assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(environment.getProperty("management.endpoint.health.show-details")).isEqualTo("never");

        StandardEnvironment insecure = environment(Map.of("JWT_SECRET", "x", "AUTH_COOKIE_SECURE", "false"),
                "application.yml", "application-prod.yml");
        assertThat(insecure.getProperty("app.security.cookie.secure", Boolean.class)).isFalse();
        assertThat(List.of(insecure.getProperty("app.security.internet-facing"))).containsExactly("false");
    }
}
