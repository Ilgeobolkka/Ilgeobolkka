package com.example.ilgeobolkka.auth.dto;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class AuthInputPolicy {

    private AuthInputPolicy() {
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    static boolean isPasswordValid(String password) {
        if (password == null) {
            return true;
        }
        if (password.codePointCount(0, password.length()) < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 64) {
            return false;
        }

        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSpecialCharacter = false;
        for (int index = 0; index < password.length(); index++) {
            char character = password.charAt(index);
            boolean isLetter =
                    ('A' <= character && character <= 'Z')
                            || ('a' <= character && character <= 'z');
            boolean isDigit = '0' <= character && character <= '9';
            hasLetter |= isLetter;
            hasDigit |= isDigit;
            hasSpecialCharacter |=
                    '!' <= character && character <= '~' && !isLetter && !isDigit;
        }
        return hasLetter && hasDigit && hasSpecialCharacter;
    }
}
