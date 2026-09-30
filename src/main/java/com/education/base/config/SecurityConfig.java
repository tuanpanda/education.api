package com.education.base.config;

import com.education.base.security.JwtAuthenticationFilter;
import com.education.base.security.JwtTokenService;
import com.education.base.security.RestAccessDeniedHandler;
import com.education.base.security.RestAuthenticationEntryPoint;
import com.education.base.security.SecurityErrorWriter;
import com.education.base.service.AccessControlService;
import com.education.base.service.RefreshTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.time.Clock;
import java.util.Arrays;

/**
 * Bảo mật API bằng JWT stateless.
 * <ul>
 *     <li>Công khai: đăng nhập, làm mới token, health check, Swagger/OpenAPI.</li>
 *     <li>Mọi {@code /api/**} khác cần access token hợp lệ; quyền chi tiết theo menu x chức năng được
 *     {@code PermissionInterceptor} kiểm tra qua {@code @RequirePermission}.</li>
 *     <li>Ngoài {@code /api/**} chỉ mở {@code /}, {@code /favicon.ico}, {@code /error} và Swagger/OpenAPI
 *     ({@link #PUBLIC_DOC_PATHS}); mọi đường dẫn khác bị từ chối ({@code denyAll}). Swagger/OpenAPI tắt ở
 *     profile {@code prod} (springdoc) nên permitAll chỉ có tác dụng khi bật.</li>
 *     <li>CORS dùng cấu hình {@link WebConfig#addCorsMappings} (Spring Security tự nhận qua {@code cors()}).</li>
 *     <li>Header bảo mật: CSP {@link #API_CSP} cho response API, CSP nới lỏng {@link #SWAGGER_UI_CSP} cho Swagger UI;
 *     HSTS (chỉ gửi khi request là HTTPS).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    static final String[] PUBLIC_DOC_PATHS = {
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/swagger", "/swagger/**", "/", "/favicon.ico", "/error"
    };

    /** Trang Swagger UI (HTML/JS/CSS) cần CSP nới lỏng; chỉ phục vụ khi springdoc bật (không bật ở prod). */
    static final String[] SWAGGER_UI_PATHS = {"/swagger-ui/**", "/swagger-ui.html"};

    /** CSP cho response API (JSON/file): không cho nạp tài nguyên nào, không cho nhúng iframe. */
    static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";

    /** CSP cho Swagger UI: chỉ tài nguyên cùng origin (+ inline style/script, data: cho icon), không cho nhúng iframe. */
    static final String SWAGGER_UI_CSP = "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; "
            + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    /** HSTS 1 năm; Spring Security chỉ gửi header khi request là HTTPS ({@code request.isSecure()}). */
    static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenService jwtTokenService,
                                                   AccessControlService accessControlService,
                                                   ObjectMapper objectMapper,
                                                   ObjectProvider<RefreshTokenService> refreshTokenService)
            throws Exception {
        SecurityErrorWriter errorWriter = new SecurityErrorWriter(objectMapper);
        RequestMatcher swaggerUi = new OrRequestMatcher(Arrays.stream(SWAGGER_UI_PATHS)
                .map(AntPathRequestMatcher::antMatcher)
                .toArray(RequestMatcher[]::new));
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)
                                .includeSubDomains(false))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                new NegatedRequestMatcher(swaggerUi),
                                new StaticHeadersWriter("Content-Security-Policy", API_CSP)))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                swaggerUi,
                                new StaticHeadersWriter("Content-Security-Policy", SWAGGER_UI_CSP))))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/api/v1/health").permitAll()
                        .requestMatchers(PUBLIC_DOC_PATHS).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint(errorWriter))
                        .accessDeniedHandler(new RestAccessDeniedHandler(errorWriter)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenService, accessControlService,
                                refreshTokenService.getIfAvailable()),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
