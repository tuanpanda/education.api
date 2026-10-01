package com.education.base.dto.response;

/**
 * CSRF token cho frontend: gửi lại {@code token} trong header {@code headerName} ở mọi request
 * POST/PUT/PATCH/DELETE (double-submit với cookie {@code XSRF-TOKEN}).
 */
public record CsrfTokenResponse(String headerName, String token) {
}
