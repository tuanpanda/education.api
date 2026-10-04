package com.education.base.common;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TempPasswordGeneratorTest {

    private final TempPasswordGenerator generator = new TempPasswordGenerator();

    @Test
    void passwordsAreReadableAndSatisfyPasswordPolicy() {
        Pattern policy = Pattern.compile(DomainConstants.PASSWORD_PATTERN);
        for (int i = 0; i < 2000; i++) {
            String password = generator.generate();
            assertThat(password).hasSize(TempPasswordGenerator.LENGTH);
            assertThat(password).matches("[a-z2-9]+");
            assertThat(password).doesNotContain("0", "1", "o", "l", "i");
            assertThat(password).matches(".*[a-z].*").matches(".*[2-9].*");
            assertThat(policy.matcher(password).matches()).as(password).isTrue();
        }
    }

    @Test
    void passwordsAreRandom() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(generator.generate());
        }
        assertThat(seen).hasSize(1000);
    }
}
