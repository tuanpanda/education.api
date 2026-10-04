package com.education.base.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AuditDetailsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sensitiveKeysAreRedactedAtAnyDepth() {
        for (String key : List.of("password", "newPassword", "oldPwd", "passwd", "accessToken", "refresh_token",
                "Cookie", "Set-Cookie", "Authorization", "clientSecret", "credentials", "otp", "jwt", "sessionId",
                "X-XSRF-TOKEN", "csrf", "apiKey", "api_key")) {
            assertThat(AuditDetails.isSensitiveKey(key)).as(key).isTrue();
        }
        for (String key : List.of("username", "reason", "amount", "roleIds", "receiptNo", "status")) {
            assertThat(AuditDetails.isSensitiveKey(key)).as(key).isFalse();
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("username", "admin");
        input.put("password", "P@ss");
        input.put("nested", Map.of("token", "t", "list", List.of(Map.of("secret", "s", "id", 1))));
        String json = AuditDetails.toJson(objectMapper, input, 4000);

        assertThat(json).contains("\"username\":\"admin\"")
                .contains("\"password\":\"[REDACTED]\"")
                .contains("\"token\":\"[REDACTED]\"")
                .contains("\"secret\":\"[REDACTED]\"")
                .contains("\"id\":1")
                .doesNotContain("P@ss");
    }

    @Test
    void longValuesAndCollectionsAreShortened() {
        Map<String, Object> clean = AuditDetails.sanitize(Map.of(
                "note", "a".repeat(2000),
                "ids", IntStream.range(0, 120).boxed().toList()));

        assertThat((String) clean.get("note")).hasSize(AuditDetails.MAX_VALUE_CHARS + 3).endsWith("...");
        @SuppressWarnings("unchecked")
        List<Object> ids = (List<Object>) clean.get("ids");
        assertThat(ids).hasSize(AuditDetails.MAX_COLLECTION_ITEMS + 1);
        assertThat(ids.get(ids.size() - 1)).isEqualTo("...(+70)");
    }

    @Test
    void emptyDetailsBecomeNull() {
        assertThat(AuditDetails.toJson(objectMapper, Map.of(), 4000)).isNull();
        assertThat(AuditDetails.toJson(objectMapper, null, 4000)).isNull();
    }

    @Test
    void truncateUtf8_respectsByteLimitAndNeverSplitsCharacters() {
        assertThat(AuditDetails.truncateUtf8(null, 10)).isNull();
        assertThat(AuditDetails.truncateUtf8("abc", 10)).isEqualTo("abc");
        // "ệ" = 3 byte: 10 byte chứa được 3 ký tự.
        assertThat(AuditDetails.truncateUtf8("ệệệệệ", 10)).isEqualTo("ệệệ");
        // Emoji (cặp surrogate, 4 byte) không bị cắt đôi.
        String emoji = "a\uD83D\uDE00b";
        assertThat(AuditDetails.truncateUtf8(emoji, 4)).isEqualTo("a");
        assertThat(AuditDetails.truncateUtf8(emoji, 5)).isEqualTo("a\uD83D\uDE00");
        String long1 = "Nhật ký ".repeat(1000);
        assertThat(AuditDetails.truncateUtf8(long1, 4000).getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(4000).isGreaterThan(3990);
    }

    @Test
    void code_normalizesToUpperCase() {
        assertThat(AuditDetails.code(" login_failed ", 50)).isEqualTo("LOGIN_FAILED");
        assertThat(AuditDetails.code("  ", 50)).isNull();
        assertThat(AuditDetails.code(null, 50)).isNull();
    }
}
