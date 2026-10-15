package com.education.base.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * AC-15 (docs/specs/PRE.md): ket noi DB bat buoc lay tu bien moi truong DB_USERNAME / DB_PASSWORD,
 * khong co gia tri mac dinh. Thieu (hoac rong, hoac con placeholder chua giai) -> ung dung dung ngay,
 * thong bao chi ghi TEN bien, khong bao gio in gia tri.
 * Bo qua khi profile {@code test} dang bat.
 */
public final class DatasourceEnvPolicy {

    public static final String TEST_PROFILE = "test";

    private DatasourceEnvPolicy() {
    }

    /** Kiem tra; nem {@link IllegalStateException} neu thieu bien. */
    public static void validate(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of(TEST_PROFILE))) {
            return;
        }
        List<String> missing = new ArrayList<>();
        if (isMissing(environment, "spring.datasource.username")) {
            missing.add("DB_USERNAME");
        }
        if (isMissing(environment, "spring.datasource.password")) {
            missing.add("DB_PASSWORD");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Thieu bien moi truong ket noi DB: " + String.join(", ", missing)
                    + ". Dat bien nay truoc khi khoi dong (khong co gia tri mac dinh, xem AC-15 docs/specs/PRE.md).");
        }
    }

    private static boolean isMissing(Environment environment, String key) {
        String value;
        try {
            value = environment.getProperty(key);
        } catch (IllegalArgumentException unresolved) {
            return true;
        }
        return value == null || value.isBlank() || value.contains("${");
    }
}