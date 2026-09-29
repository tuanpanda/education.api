package com.education.base.security;

/**
 * Loại JWT phát hành bởi hệ thống, lưu trong claim {@code token_type}.
 */
public enum TokenType {
    ACCESS,
    REFRESH
}
