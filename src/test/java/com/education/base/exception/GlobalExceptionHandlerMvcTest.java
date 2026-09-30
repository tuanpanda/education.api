package com.education.base.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra mapping exception của Spring MVC -> HTTP status + body {@code ApiResponse} qua DispatcherServlet thật.
 */
class GlobalExceptionHandlerMvcTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void unsupportedMethod_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(delete("/probe/items"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value(containsString("DELETE")))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));
    }

    @Test
    void unsupportedMediaType_returns415() throws Exception {
        mockMvc.perform(post("/probe/json").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.message").value(containsString("text/plain")));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/probe/json").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void missingRequestParameter_returns400() throws Exception {
        mockMvc.perform(get("/probe/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("userId")));
    }

    @Test
    void missingRequestPart_returns400() throws Exception {
        mockMvc.perform(multipart("/probe/upload").param("note", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("file")));
    }

    @Test
    void noResourceFound_returns404() throws Exception {
        mockMvc.perform(get("/probe/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void maxUploadSizeExceeded_returns413() throws Exception {
        mockMvc.perform(get("/probe/too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void businessNotFound_returns404() throws Exception {
        mockMvc.perform(get("/probe/business-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FEE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Khong tim thay khoan hoc phi ID: 15"));
    }

    @Test
    void rawOracleError_returnsGenericMessage() throws Exception {
        mockMvc.perform(get("/probe/raw-oracle"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("-1"))
                .andExpect(jsonPath("$.message").value(OracleErrorMessages.GENERIC_MESSAGE));
    }

    /**
     * Controller thử nghiệm, chỉ đăng ký qua standaloneSetup (các test {@code @WebMvcTest} đều chỉ định controller
     * cụ thể nên không nạp class này).
     */
    @RestController
    public static class ProbeController {

        @GetMapping("/probe/items")
        public ResponseEntity<String> items() {
            return ResponseEntity.ok("ok");
        }

        @PostMapping(value = "/probe/json", consumes = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<String> json(@RequestBody Map<String, Object> body) {
            return ResponseEntity.ok("ok");
        }

        @GetMapping("/probe/param")
        public ResponseEntity<String> param(@RequestParam("userId") Long userId) {
            return ResponseEntity.ok("ok");
        }

        @PostMapping("/probe/upload")
        public ResponseEntity<String> upload(@RequestPart("file") MultipartFile file) {
            return ResponseEntity.ok("ok");
        }

        @GetMapping("/probe/no-resource")
        public ResponseEntity<String> noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "probe/missing.png");
        }

        @GetMapping("/probe/too-large")
        public ResponseEntity<String> tooLarge() {
            throw new MaxUploadSizeExceededException(50L * 1024 * 1024);
        }

        @GetMapping("/probe/business-not-found")
        public ResponseEntity<String> businessNotFound() {
            throw new OracleBusinessException("FEE_NOT_FOUND", "Khong tim thay khoan hoc phi ID: 15");
        }

        @GetMapping("/probe/raw-oracle")
        public ResponseEntity<String> rawOracle() {
            throw new OracleBusinessException("-1", "ORA-00001: unique constraint (EDUCATION.UK_X) violated");
        }
    }
}
