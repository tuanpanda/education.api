package com.education.base.config;

import com.education.base.security.JwtAuthenticationFilter;
import com.education.base.security.JwtTokenService;
import com.education.base.security.RestAccessDeniedHandler;
import com.education.base.security.RestAuthenticationEntryPoint;
import com.education.base.security.SecurityErrorWriter;
import com.education.base.service.AccessControlService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Clock;

/**
 * Bảo mật API bằng JWT stateless.
 * <ul>
 *     <li>Công khai: đăng nhập, làm mới token, health check, Swagger/OpenAPI.</li>
 *     <li>Mọi {@code /api/**} khác cần access token hợp lệ; quyền chi tiết theo menu x chức năng được
 *     {@code PermissionInterceptor} kiểm tra qua {@code @RequirePermission}.</li>
 *     <li>CORS dùng cấu hình {@link WebConfig#addCorsMappings} (Spring Security tự nhận qua {@code cors()}).</li>
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenService jwtTokenService,
                                                   AccessControlService accessControlService,
                                                   ObjectMapper objectMapper) throws Exception {
        SecurityErrorWriter errorWriter = new SecurityErrorWriter(objectMapper);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/api/v1/health").permitAll()
                        .requestMatchers(PUBLIC_DOC_PATHS).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint(errorWriter))
                        .accessDeniedHandler(new RestAccessDeniedHandler(errorWriter)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenService, accessControlService),
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
