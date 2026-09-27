package com.education.base.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Điều hướng các URL thường gặp khi mở browser (trang gốc, /swagger, favicon)
 * tới Swagger UI, tránh {@code NoResourceFoundException}.
 */
@Hidden
@RestController
public class HomeController {

    static final String SWAGGER_UI_PATH = "/swagger-ui/index.html";

    @GetMapping({"/", "/swagger", "/swagger/", "/swagger/index.html"})
    public ResponseEntity<Void> redirectToSwaggerUi() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(SWAGGER_UI_PATH))
                .build();
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent().build();
    }
}
