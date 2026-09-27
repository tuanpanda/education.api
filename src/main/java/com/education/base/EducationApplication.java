package com.education.base;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * EDUCATION PROJECT - Application Entry Point.
 * <p>
 * Kiến trúc: Java 21 + Spring Boot 3.x + Oracle Hybrid DB-First
 * (Spring Data JPA cho CRUD chuẩn hóa, Spring JDBC/SimpleJdbcCall cho Oracle Package/Procedure).
 */
@SpringBootApplication
public class EducationApplication {

    public static void main(String[] args) {
        SpringApplication.run(EducationApplication.class, args);
    }
}
