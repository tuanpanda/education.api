package com.education.base.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlContentSanitizerTest {

    private final HtmlContentSanitizer sanitizer = new HtmlContentSanitizer();

    @Test
    void stripsScriptAndEventHandlers() {
        String dirty = "<p onclick=\"alert(1)\">Hi</p><script>alert(2)</script><a href=\"javascript:alert(3)\">x</a>";
        String clean = sanitizer.sanitize(dirty);
        assertThat(clean).contains("Hi");
        assertThat(clean.toLowerCase()).doesNotContain("script");
        assertThat(clean.toLowerCase()).doesNotContain("onclick");
        assertThat(clean.toLowerCase()).doesNotContain("javascript:");
    }

    @Test
    void allowsBasicFormattingAndHttpsLinks() {
        String html = "<p><strong>Bold</strong></p><ul><li>One</li></ul><a href=\"https://example.com\">link</a>";
        String clean = sanitizer.sanitize(html);
        assertThat(clean).contains("Bold").contains("One").contains("https://example.com");
    }

    @Test
    void blankReturnsEmpty() {
        assertThat(sanitizer.sanitize(null)).isEmpty();
        assertThat(sanitizer.sanitize("   ")).isEmpty();
    }
}
