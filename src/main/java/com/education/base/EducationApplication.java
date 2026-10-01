package com.education.base;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import java.util.TimeZone;

/**
 * EDUCATION PROJECT - Application Entry Point.
 * <p>
 * Kiến trúc: Java 21 + Spring Boot 3.x + Oracle Hybrid DB-First
 * (Spring Data JPA cho CRUD chuẩn hóa, Spring JDBC/SimpleJdbcCall cho Oracle Package/Procedure).
 * <p>
 * Xác thực dùng JWT tự quản lý ({@code SecurityConfig}) nên tắt user in-memory mặc định của Spring Boot.
 * <p>
 * Múi giờ: DB lưu thời gian local (DATE/TIMESTAMP không kèm time zone) theo giờ Việt Nam, Java dùng
 * {@code LocalDate}/{@code LocalDateTime.now()}. JVM được cố định ở {@link #APP_TIME_ZONE} (trùng
 * {@code spring.jpa.properties.hibernate.jdbc.time_zone} và {@code spring.jackson.time-zone}) để JPA, JDBC
 * (procedure) và {@code now()} luôn nhất quán dù chạy trên Windows/IIS, IntelliJ hay container (mặc định UTC).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class EducationApplication {

    /** Múi giờ nghiệp vụ; phải trùng giá trị trong application.yml và Dockerfile/docker-compose. */
    public static final String APP_TIME_ZONE = "Asia/Ho_Chi_Minh";

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(APP_TIME_ZONE));
        SpringApplication.run(EducationApplication.class, args);
    }
}
