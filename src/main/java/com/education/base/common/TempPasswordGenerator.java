package com.education.base.common;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Sinh mật khẩu tạm cho tài khoản học sinh (tạo hàng loạt / đặt lại mật khẩu).
 * <ul>
 *     <li>{@value #LENGTH} ký tự từ bảng chữ thường + số đã bỏ ký tự dễ nhầm ({@code 0 o 1 l i}) để học sinh nhỏ
 *     gõ lại từ phiếu in được; luôn có ít nhất 1 chữ và 1 số nên thỏa {@link DomainConstants#PASSWORD_PATTERN}.</li>
 *     <li>{@link SecureRandom}; khoảng 31^10 (~8 x 10^14) tổ hợp, kết hợp khóa tạm thời khi sai mật khẩu nhiều lần.</li>
 *     <li>Mật khẩu tạm chỉ trả về MỘT lần trong response, không lưu dạng rõ, không ghi log.</li>
 * </ul>
 */
@Component
public class TempPasswordGenerator {

    public static final int LENGTH = 10;

    static final String LETTERS = "abcdefghjkmnpqrstuvwxyz";
    static final String DIGITS = "23456789";
    private static final String ALPHABET = LETTERS + DIGITS;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] chars = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            chars[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        // Bảo đảm có cả chữ lẫn số tại hai vị trí ngẫu nhiên khác nhau.
        int letterAt = random.nextInt(LENGTH);
        int digitAt = (letterAt + 1 + random.nextInt(LENGTH - 1)) % LENGTH;
        chars[letterAt] = LETTERS.charAt(random.nextInt(LETTERS.length()));
        chars[digitAt] = DIGITS.charAt(random.nextInt(DIGITS.length()));
        return new String(chars);
    }
}
