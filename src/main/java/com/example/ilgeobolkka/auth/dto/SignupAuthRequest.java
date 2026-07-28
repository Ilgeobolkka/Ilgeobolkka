package com.example.ilgeobolkka.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public record SignupAuthRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull String password) {

    public SignupAuthRequest {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }

    @AssertTrue
    public boolean isPasswordValid() {
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
