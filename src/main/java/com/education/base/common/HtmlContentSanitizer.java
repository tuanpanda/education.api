package com.education.base.common;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * Làm sạch HTML nội dung thông báo trước khi lưu (chống XSS).
 * Cho phép định dạng cơ bản: đoạn, danh sách, nhấn mạnh, liên kết http(s).
 */
@Component
public class HtmlContentSanitizer {

    private final PolicyFactory policy = new HtmlPolicyBuilder()
            .allowElements("p", "br", "div", "span", "ul", "ol", "li",
                    "strong", "b", "em", "i", "u", "h1", "h2", "h3", "h4", "blockquote", "a")
            .allowUrlProtocols("http", "https")
            .allowAttributes("href").onElements("a")
            .requireRelNofollowOnLinks()
            .toFactory();

    /**
     * @return HTML đã làm sạch; chuỗi rỗng nếu input null/blank.
     */
    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return policy.sanitize(html);
    }
}
